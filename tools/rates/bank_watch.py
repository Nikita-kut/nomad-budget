#!/usr/bin/env python3
"""Следит за курсом банка по включённым rate_watches, пишет снимки в bank_rate_snapshots и шлёт сигналы в Telegram.

Переменные окружения:
  SUPABASE_URL          https://<ref>.supabase.co
  SUPABASE_SERVICE_KEY  секретный ключ (обходит RLS), только в GitHub Secrets
  TELEGRAM_BOT_TOKEN    токен бота от @BotFather, только в GitHub Secrets; без него скрипт выходит с кодом 0

Режимы:
  bank_watch.py                      рабочий прогон
  bank_watch.py --dry-run FROM TO    только курсы банка и ЦБ для пары, без Supabase и Telegram

В лог не печатаются коды валют и значения пользователей: логи публичного репозитория видны всем.
Семантика курсов и формат сообщений — docs/BANK_RATE_WATCH.md.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import ssl
import sys
import urllib.error
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET
from pathlib import Path
from zoneinfo import ZoneInfo, ZoneInfoNotFoundError

HERE = Path(__file__).resolve().parent
PROVIDER_CA = HERE / "ca" / "russian_trusted.pem"

TBANK_URL = "https://api.tinkoff.ru/v1/currency_rates"
CBR_URL = "https://www.cbr.ru/scripts/XML_daily.asp"
TELEGRAM_URL = "https://api.telegram.org/bot{token}/{method}"

CATEGORY_EXCHANGE = "DebitCardsTransfers"
CATEGORY_CARD = "DebitCardsOperations"
SNAPSHOT_CATEGORIES = (CATEGORY_EXCHANGE, CATEGORY_CARD)

RUB = "RUB"
DEFAULT_TIMEZONE = "Europe/Moscow"
DEFAULT_QUIET_FROM = 18
DEFAULT_QUIET_TO = 10
EXCHANGE_TIMEZONE = "Europe/Moscow"
DEFAULT_TRADE_FROM = 10
DEFAULT_TRADE_TO = 19
DEFAULT_GOOD_PERCENTILE = 85
DEFAULT_IMPROVE_PERCENT = 0.5
WARMUP_TRADING_DAYS = 2
CHECKS_PER_HOUR = 4
RETENTION_DAYS = 180
HISTORY_DAYS = 7
CARD_SANITY_RATIO = 0.5
PERCENT_EPSILON = 1e-9
RATE_EPSILON = 1e-9
SHARE_EPSILON = 1e-9
KIND_GOOD = "good"
KIND_BAD = "bad"
VERDICT_NORMAL = "normal"
VERDICT_WARMUP = "warmup"
VERDICT_NIGHT = "night"
VERDICT_WEEKEND = "weekend"
VERDICT_TEXTS = {
    KIND_GOOD: "выгодно",
    KIND_BAD: "не меняй",
    VERDICT_NORMAL: "обычно",
    VERDICT_WARMUP: "копим историю",
    VERDICT_NIGHT: "биржа закрыта, ночной курс",
    VERDICT_WEEKEND: "биржа закрыта, курс выходного дня",
}
MAX_QUOTE_UNIT = 1_000_000
NBSP = " "
MINUS = "−"
TIMEOUT = 30


# ---------- HTTP ----------

def provider_ssl_context() -> ssl.SSLContext:
    context = ssl.create_default_context()
    context.load_verify_locations(cafile=str(PROVIDER_CA))
    return context


def http_json(url: str, context: ssl.SSLContext | None = None, data: bytes | None = None, headers: dict[str, str] | None = None, method: str | None = None):
    request = urllib.request.Request(url, data=data, headers=headers or {}, method=method)
    with urllib.request.urlopen(request, timeout=TIMEOUT, context=context) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def http_bytes(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
        return response.read()


# ---------- Supabase ----------

class Supabase:
    def __init__(self, url: str, key: str):
        self.base = url.rstrip("/") + "/rest/v1/"
        self.key = key

    def request(self, path: str, method: str = "GET", body=None, prefer: str | None = None):
        headers = {"apikey": self.key, "Authorization": f"Bearer {self.key}", "Content-Type": "application/json"}
        if prefer:
            headers["Prefer"] = prefer
        data = json.dumps(body).encode() if body is not None else None
        return http_json(self.base + path, data=data, headers=headers, method=method)

    def select(self, table: str, query: str = ""):
        return self.request(f"{table}?{query}") or []

    def insert(self, table: str, rows, prefer: str = "return=minimal"):
        return self.request(table, method="POST", body=rows, prefer=prefer)

    def upsert(self, table: str, rows, prefer: str = "resolution=merge-duplicates,return=minimal"):
        return self.request(table, method="POST", body=rows, prefer=prefer)

    def patch(self, table: str, query: str, body):
        return self.request(f"{table}?{query}", method="PATCH", body=body, prefer="return=minimal")

    def delete(self, table: str, query: str):
        return self.request(f"{table}?{query}", method="DELETE", prefer="return=minimal")


# ---------- Telegram ----------

class Telegram:
    def __init__(self, token: str):
        self.token = token

    def call(self, method: str, **params):
        url = TELEGRAM_URL.format(token=self.token, method=method)
        payload = http_json(url, data=json.dumps(params).encode(), headers={"Content-Type": "application/json"}, method="POST")
        if not payload or not payload.get("ok"):
            raise RuntimeError(f"telegram {method} failed")
        return payload["result"]

    def send(self, chat_id: int, text: str) -> None:
        self.call("sendMessage", chat_id=chat_id, text=text, parse_mode="HTML", disable_web_page_preview=True)


# ---------- Парсинг курсов (чистые функции) ----------

def parse_bank_rates(payload: dict) -> dict[str, dict[str, float | None]]:
    """category -> {"buy": TO за 1 FROM при покупке TO, "sell": TO за 1 FROM при продаже TO}."""
    rates = {}
    for item in payload.get("payload", {}).get("rates", []):
        category = item.get("category")
        if not category:
            continue
        rates[category] = {"buy": _float_or_none(item.get("buy")), "sell": _float_or_none(item.get("sell"))}
    return rates


def _float_or_none(value) -> float | None:
    try:
        number = float(value)
    except (TypeError, ValueError):
        return None
    return number if number > 0 else None


def parse_cbr(xml_bytes: bytes) -> dict[str, float]:
    """code -> сколько RUB за 1 единицу валюты (VunitRate), плюс RUB=1."""
    root = ET.fromstring(xml_bytes)
    per_unit = {RUB: 1.0}
    for valute in root.iter("Valute"):
        code = (valute.findtext("CharCode") or "").strip().upper()
        unit_rate = valute.findtext("VunitRate")
        value = valute.findtext("Value")
        nominal = valute.findtext("Nominal")
        rate = None
        if unit_rate:
            rate = _parse_decimal(unit_rate)
        elif value and nominal:
            rate = _parse_decimal(value) / float(nominal)
        if code and rate:
            per_unit[code] = rate
    return per_unit


def _parse_decimal(text: str) -> float:
    return float(text.strip().replace(",", ".").replace(" ", ""))


def cross_rate(rub_per_unit: dict[str, float], from_code: str, to_code: str) -> float | None:
    """TO за 1 FROM по курсу ЦБ через рубль."""
    a = rub_per_unit.get(from_code)
    b = rub_per_unit.get(to_code)
    if not a or not b:
        return None
    return a / b


def card_rate(rates: dict[str, dict[str, float | None]], exchange: float | None) -> float | None:
    """Курс оплаты картой; для некоторых пар провайдер отдаёт заглушку, она отбрасывается по отношению к курсу обмена."""
    card = rates.get(CATEGORY_CARD, {}).get("buy")
    if card is None or exchange is None:
        return card
    ratio = card / exchange
    return card if CARD_SANITY_RATIO <= ratio <= 1 / CARD_SANITY_RATIO else None


# ---------- Время: окно отправки и часы биржи ----------

def local_now(now_utc: dt.datetime, timezone: str) -> dt.datetime:
    try:
        zone = ZoneInfo(timezone or DEFAULT_TIMEZONE)
    except (ZoneInfoNotFoundError, ValueError):
        try:
            zone = ZoneInfo(DEFAULT_TIMEZONE)
        except ZoneInfoNotFoundError:
            zone = dt.timezone.utc
    return now_utc.astimezone(zone)


def in_quiet_hours(hour: int, quiet_from: int, quiet_to: int) -> bool:
    """Окно отправки = [quiet_to, quiet_from) по местному времени; по умолчанию 10:00–18:00, остальное — тишина."""
    if quiet_from == quiet_to:
        return False
    if quiet_from < quiet_to:
        return quiet_from <= hour < quiet_to
    return hour >= quiet_from or hour < quiet_to


def exchange_now(now_utc: dt.datetime) -> dt.datetime:
    return local_now(now_utc, EXCHANGE_TIMEZONE)


def is_trading_day(day: dt.date, holidays: set[str]) -> bool:
    return day.weekday() < 5 and day.isoformat() not in holidays


def in_trading_hours(now_utc: dt.datetime, trade_from: int, trade_to: int, holidays: set[str]) -> bool:
    moment = exchange_now(now_utc)
    return is_trading_day(moment.date(), holidays) and trade_from <= moment.hour < trade_to


def trade_hours(watch: dict) -> tuple[int, int]:
    trade_from = watch.get("trade_from")
    trade_to = watch.get("trade_to")
    return (
        DEFAULT_TRADE_FROM if trade_from is None else int(trade_from),
        DEFAULT_TRADE_TO if trade_to is None else int(trade_to),
    )


def warmup_samples(trade_from: int, trade_to: int) -> int:
    return WARMUP_TRADING_DAYS * max(trade_to - trade_from, 1) * CHECKS_PER_HOUR


# ---------- Оценка курса ----------

def spread(bank: float, cbr: float | None) -> float | None:
    if not cbr:
        return None
    return bank / cbr - 1.0


def median(values: list[float]) -> float | None:
    if not values:
        return None
    ordered = sorted(values)
    middle = len(ordered) // 2
    if len(ordered) % 2:
        return ordered[middle]
    return (ordered[middle - 1] + ordered[middle]) / 2.0


def share_below(values: list[float], rate: float) -> float:
    return sum(1 for value in values if value < rate) / len(values) if values else 0.0


def share_above(values: list[float], rate: float) -> float:
    return sum(1 for value in values if value > rate) / len(values) if values else 0.0


def median_spread(history: list[dict]) -> float | None:
    return median([s for s in (spread(row["buy"], row.get("cbr")) for row in history) if s is not None])


def assess(watch: dict, rate: float, cbr: float | None, history: list[dict]) -> dict:
    values = [row["buy"] for row in history]
    trade_from, trade_to = trade_hours(watch)
    warmup = len(values) < warmup_samples(trade_from, trade_to)
    threshold = float(watch.get("good_percentile") or DEFAULT_GOOD_PERCENTILE) / 100.0
    below = share_below(values, rate)
    above = share_above(values, rate)
    week_spread = median_spread(history)
    now_spread = spread(rate, cbr)
    spread_ok = week_spread is None or now_spread is None or now_spread > week_spread
    good = not warmup and below >= threshold - SHARE_EPSILON and spread_ok
    bad = not warmup and above >= threshold - SHARE_EPSILON
    kind = KIND_GOOD if good else KIND_BAD if bad else None
    return {
        "samples": len(values), "warmup": warmup, "below": below, "above": above,
        "spread": now_spread, "median_spread": week_spread, "kind": kind,
    }


def is_improved(kind: str, rate: float, sent: float, improve_percent: float) -> bool:
    if sent <= 0:
        return False
    change = rate / sent - 1.0
    if kind == KIND_BAD:
        change = -change
    return change * 100 >= improve_percent - PERCENT_EPSILON


def rate_changed(rate: float, last: float | None) -> bool:
    return last is None or abs(rate - last) > RATE_EPSILON * max(abs(last), 1e-12)


# ---------- Сигналы ----------

def evaluate(watch: dict, rate: float, cbr: float | None, state: dict, now_utc: dt.datetime, history: list[dict], holidays: set[str]) -> tuple[dict, list[dict]]:
    """Возвращает новое состояние слежения и не больше одного события для отправки: digest, target, good или bad."""
    local = local_now(now_utc, watch.get("timezone") or DEFAULT_TIMEZONE)
    quiet = in_quiet_hours(local.hour, int(watch.get("quiet_from", DEFAULT_QUIET_FROM)), int(watch.get("quiet_to", DEFAULT_QUIET_TO)))
    trade_from, trade_to = trade_hours(watch)
    trading = in_trading_hours(now_utc, trade_from, trade_to, holidays)
    improve_percent = float(watch.get("improve_percent") or DEFAULT_IMPROVE_PERCENT)
    digest_hour = watch.get("digest_hour")

    assessment = assess(watch, rate, cbr, history) if trading else None
    kind = assessment["kind"] if assessment else None
    window = dict(state.get("window") or {}) or None
    notes = dict(state.get("notes") or {}) if digest_hour is not None else {}
    signal = None

    target = _float_or_none(watch.get("target"))
    last = _float_or_none(state.get("last"))
    above_target = target is not None and rate >= target
    target_event = None
    if above_target and rate_changed(rate, last):
        if not quiet:
            target_event = {"kind": "target", "target": target, "previous": last}
        elif digest_hour is not None:
            notes["target_max"] = max(rate, notes.get("target_max", rate))

    if kind is None:
        window = None
    else:
        if window is None or window.get("kind") != kind:
            window = {"kind": kind, "since": now_utc.isoformat(), "sent": None, "notified": False}
        sent = _float_or_none(window.get("sent"))
        improved = sent is not None and is_improved(kind, rate, sent, improve_percent)
        if quiet:
            if digest_hour is not None:
                notes[kind] = True
                if kind == KIND_GOOD:
                    notes["best"] = max(rate, notes.get("best", rate))
                else:
                    notes["worst"] = min(rate, notes.get("worst", rate))
            if sent is None or improved:
                window["sent"] = rate
        elif not window.get("notified"):
            signal = {"kind": kind, "repeat": False, "previous": None, "assessment": assessment}
            window["sent"] = rate
            window["notified"] = True
        elif improved:
            signal = {"kind": kind, "repeat": True, "previous": sent, "assessment": assessment}
            window["sent"] = rate

    events: list[dict] = []
    today = local.date().isoformat()
    last_digest = state.get("last_digest_date")
    if digest_hour is not None and not quiet and local.hour == int(digest_hour) and last_digest != today:
        events.append({
            "kind": "digest", "verdict": verdict(assessment, exchange_now(now_utc).date(), holidays), "assessment": assessment,
            "notes": notes, "target": target if above_target else None,
        })
        notes = {}
        last_digest = today
    elif target_event is not None:
        events.append(target_event)
    elif signal is not None:
        events.append(signal)

    new_state = {
        "last": rate,
        "last_at": now_utc.isoformat(),
        "window": window,
        "notes": notes,
        "last_digest_date": last_digest,
    }
    return new_state, events


def verdict(assessment: dict | None, exchange_day: dt.date, holidays: set[str]) -> str:
    if assessment is None:
        return VERDICT_NIGHT if is_trading_day(exchange_day, holidays) else VERDICT_WEEKEND
    if assessment["kind"]:
        return assessment["kind"]
    return VERDICT_WARMUP if assessment["warmup"] else VERDICT_NORMAL



# ---------- Формат чисел (повторяет MoneyFormat в приложении) ----------

def group_thousands(value: int) -> str:
    digits = str(value)
    out = []
    for index, char in enumerate(digits):
        remaining = len(digits) - index
        if index > 0 and remaining % 3 == 0:
            out.append(NBSP)
        out.append(char)
    return "".join(out)


def format_number(value: float, decimals: int) -> str:
    factor = 10 ** decimals
    scaled = int(abs(value) * factor + 0.5)
    whole, fraction = divmod(scaled, factor)
    sign = MINUS if value < 0 and scaled != 0 else ""
    if decimals == 0:
        return f"{sign}{group_thousands(whole)}"
    return f"{sign}{group_thousands(whole)},{str(fraction).rjust(decimals, '0')}"


def format_quote(rate: float) -> str:
    if rate >= 100.0:
        return group_thousands(int(rate + 0.5))
    if rate >= 1.0:
        return format_number(rate, 2)
    return format_number(rate, 4)


def format_precise(rate: float) -> str:
    return format_number(rate, 2 if rate >= 1.0 else 4)


def quote_unit(value: float) -> int:
    unit = 1
    absolute = abs(value)
    while absolute > 0.0 and absolute * unit < 1.0 and unit < MAX_QUOTE_UNIT:
        unit *= 10
    return unit


def unit_label(unit: int, code: str) -> str:
    return f"{group_thousands(unit)}{NBSP}{code}"


def rate_text(from_code: str, to_code: str, value: float, unit: int | None = None) -> str:
    unit = unit or quote_unit(value)
    return f"{unit_label(unit, from_code)} = {format_quote(value * unit)}{NBSP}{to_code}"


def format_percent(share: float) -> str:
    tenths = int(round(share * 1000))
    if tenths == 0:
        return "0 %"
    sign = "+" if tenths > 0 else MINUS
    absolute = abs(tenths)
    whole, fraction = divmod(absolute, 10)
    text = f"{whole}" if fraction == 0 else f"{whole},{fraction}"
    return f"{sign}{text}{NBSP}%"


def format_share(share: float) -> str:
    return f"{int(share * 100 + 0.5)}{NBSP}%"


def format_percent_abs(share: float) -> str:
    return format_percent(abs(share)).lstrip("+")


def plural(count: int, one: str, few: str, many: str) -> str:
    tail = abs(count) % 100
    if 11 <= tail <= 14:
        word = many
    elif tail % 10 == 1:
        word = one
    elif 2 <= tail % 10 <= 4:
        word = few
    else:
        word = many
    return f"{count}{NBSP}{word}"


# ---------- Сообщения ----------

def pair_title(watch: dict) -> str:
    return f"{watch['from_currency']} → {watch['to_currency']}"


def cbr_line(from_code: str, to_code: str, bank: float, cbr: float | None, unit: int) -> str:
    if cbr is None:
        return "ЦБ: курса нет"
    return f"ЦБ: {rate_text(from_code, to_code, cbr, unit)} · разница {format_percent(spread(bank, cbr) or 0.0)}"


def card_line(rate: float, card: float | None) -> str | None:
    if card is None or rate <= 0:
        return None
    difference = card / rate - 1.0
    if int(round(difference * 1000)) == 0:
        return "Картой так же"
    word = "выгоднее" if difference > 0 else "хуже"
    return f"Картой {word} на {format_percent_abs(difference)}"


def position_text(kind: str | None, assessment: dict) -> str:
    if kind == KIND_BAD:
        return f"хуже {format_share(assessment['above'])} замеров недели"
    return f"лучше {format_share(assessment['below'])} замеров недели"


def message_signal(watch: dict, rate: float, card: float | None, cbr: float | None, event: dict) -> str:
    f, t = watch["from_currency"], watch["to_currency"]
    unit = quote_unit(rate)
    kind = event["kind"]
    previous = event.get("previous")
    if kind == KIND_GOOD:
        icon = "🟢"
        headline = "выгодно менять" if previous is None else f"стало ещё выгоднее, было {format_quote(previous * unit)}"
    else:
        icon = "🔴"
        headline = "сейчас не меняй" if previous is None else f"стало ещё хуже, было {format_quote(previous * unit)}"
    lines = [
        f"{icon} <b>{pair_title(watch)}</b>: {headline}",
        f"{rate_text(f, t, rate, unit)} · {position_text(kind, event['assessment'])}",
        card_line(rate, card),
        cbr_line(f, t, rate, cbr, unit),
    ]
    return "\n".join(line for line in lines if line)


def message_target(rate: float, target: float, previous: float | None) -> str:
    unit = quote_unit(rate)
    was = f" (было {format_precise(previous * unit)})" if previous is not None else ""
    goal = format_precise(target * unit)
    goal = goal[:-3] if goal.endswith(",00") else goal
    return f"🎯 Курс выше цели {goal}: {format_precise(rate * unit)}{was}"


def verdict_text(code: str, assessment: dict | None) -> str:
    if code == VERDICT_WARMUP and assessment is not None:
        return f"копим историю: {plural(assessment['samples'], 'замер', 'замера', 'замеров')}"
    return VERDICT_TEXTS[code]


def notes_line(notes: dict, unit: int) -> str | None:
    parts = []
    if notes.get(KIND_GOOD):
        parts.append(f"было выгодно, до {format_quote(notes['best'] * unit)}" if notes.get("best") else "было выгодно")
    if notes.get(KIND_BAD):
        parts.append(f"было невыгодно, до {format_quote(notes['worst'] * unit)}" if notes.get("worst") else "было невыгодно")
    if notes.get("target_max"):
        parts.append(f"выше цели было: макс {format_quote(notes['target_max'] * unit)}")
    return f"Вне окна отправки: {'; '.join(parts)}" if parts else None


def message_digest(watch: dict, rate: float, card: float | None, cbr: float | None, event: dict, local: dt.datetime) -> str:
    f, t = watch["from_currency"], watch["to_currency"]
    unit = quote_unit(rate)
    assessment = event.get("assessment")
    code = event["verdict"]
    lines = [
        f"☀️ <b>{pair_title(watch)}</b>, {local.strftime('%H:%M')} · {verdict_text(code, assessment)}",
        f"Обмен: {rate_text(f, t, rate, unit)}",
        card_line(rate, card),
        cbr_line(f, t, rate, cbr, unit),
    ]
    if assessment is not None and not assessment["warmup"]:
        lines.append(f"Лучше {format_share(assessment['below'])} замеров недели")
    if event.get("target") is not None:
        lines.append(f"Выше цели {format_quote(event['target'] * unit)}")
    lines.append(notes_line(event.get("notes") or {}, unit))
    return "\n".join(line for line in lines if line)


def subscribe_text(watch: dict) -> str:
    return f"Подписка на курс включена: <b>{pair_title(watch)}</b>, обмен в приложении.\nОтписаться — /stop"


HELP_TEXT = "Это бот уведомлений о курсе банка. Подписка включается по ссылке из приложения, /stop — выключает."
STOP_TEXT = "Подписка выключена."


# ---------- Провайдеры ----------

def fetch_tbank(from_code: str, to_code: str) -> dict[str, dict[str, float | None]]:
    query = urllib.parse.urlencode({"from": from_code, "to": to_code})
    payload = http_json(f"{TBANK_URL}?{query}", context=provider_ssl_context())
    return parse_bank_rates(payload)


PROVIDERS = {"tbank": fetch_tbank}


def fetch_cbr() -> dict[str, float]:
    return parse_cbr(http_bytes(CBR_URL))


# ---------- Прогон ----------

def handle_updates(db: Supabase, tg: Telegram, info: dict) -> int:
    offset = int(info.get("update_offset") or 0)
    updates = tg.call("getUpdates", offset=offset, timeout=0)
    for update in updates:
        offset = max(offset, int(update["update_id"]) + 1)
        message = update.get("message") or {}
        text = (message.get("text") or "").strip()
        chat = message.get("chat") or {}
        chat_id = chat.get("id")
        if chat_id is None or not text:
            continue
        try:
            handle_command(db, tg, chat, text)
        except (urllib.error.URLError, RuntimeError, OSError) as e:
            print(f"command failed: {type(e).__name__}", file=sys.stderr)
    return offset


def handle_command(db: Supabase, tg: Telegram, chat: dict, text: str) -> None:
    chat_id = int(chat["id"])
    parts = text.split(maxsplit=1)
    command = parts[0].split("@")[0].lower()
    if command == "/start" and len(parts) > 1:
        code = parts[1].strip()
        rows = db.select("rate_watches", "select=*&link_code=eq." + urllib.parse.quote(code) + "&limit=1")
        if not rows:
            tg.send(chat_id, "Ссылка не подошла: открой приложение и нажми «Подписаться в Telegram» ещё раз.")
            return
        watch = rows[0]
        title = chat.get("first_name") or chat.get("username") or chat.get("title") or ""
        db.insert("telegram_subscribers?on_conflict=watch_id,chat_id", {"watch_id": watch["id"], "chat_id": chat_id, "title": title[:64] or None},
                  prefer="resolution=ignore-duplicates,return=minimal")
        tg.send(chat_id, subscribe_text(watch))
    elif command == "/stop":
        db.delete("telegram_subscribers", f"chat_id=eq.{chat_id}")
        tg.send(chat_id, STOP_TEXT)
    else:
        tg.send(chat_id, HELP_TEXT)


def load_history(db: Supabase, watch: dict, since: dt.datetime) -> list[dict]:
    query = (
        f"select=buy,cbr&provider=eq.{watch['provider']}&from_currency=eq.{watch['from_currency']}&to_currency=eq.{watch['to_currency']}"
        f"&category=eq.{CATEGORY_EXCHANGE}&in_hours=is.true&taken_at=gte.{urllib.parse.quote(since.isoformat())}&order=taken_at.asc&limit=1000"
    )
    return [{"buy": float(row["buy"]), "cbr": _float_or_none(row.get("cbr"))} for row in db.select("bank_rate_snapshots", query)]


def load_holidays(db: Supabase) -> set[str]:
    return {row["date"] for row in db.select("exchange_holidays", "select=date")}


def pair_trade_hours(watches: list[dict]) -> dict[tuple[str, str, str], tuple[int, int]]:
    hours: dict[tuple[str, str, str], tuple[int, int]] = {}
    for watch in watches:
        key = (watch["provider"], watch["from_currency"], watch["to_currency"])
        trade_from, trade_to = trade_hours(watch)
        known = hours.get(key)
        hours[key] = (trade_from, trade_to) if known is None else (min(known[0], trade_from), max(known[1], trade_to))
    return hours


def run(db: Supabase, tg: Telegram, now_utc: dt.datetime) -> None:
    me = tg.call("getMe")
    rows = db.select("bot_info", "select=*&id=eq.1")
    info = rows[0] if rows else {}
    offset = handle_updates(db, tg, info)
    db.upsert("bot_info", {"id": 1, "username": me.get("username"), "update_offset": offset, "updated_at": now_utc.isoformat()})

    watches = db.select("rate_watches", "select=*&enabled=is.true")
    subscribers = db.select("telegram_subscribers", "select=watch_id,chat_id")
    holidays = load_holidays(db)
    chats_by_watch: dict[str, list[int]] = {}
    for row in subscribers:
        chats_by_watch.setdefault(row["watch_id"], []).append(int(row["chat_id"]))
    print(f"{len(watches)} watch(es), {len(subscribers)} subscriber(s)")

    cbr_table: dict[str, float] | None = None
    try:
        cbr_table = fetch_cbr()
    except (urllib.error.URLError, ET.ParseError, OSError, ValueError) as e:
        print(f"central bank rates unavailable: {type(e).__name__}", file=sys.stderr)

    hours = pair_trade_hours(watches)
    fetched: dict[tuple[str, str, str], dict] = {}
    for watch in watches:
        key = (watch["provider"], watch["from_currency"], watch["to_currency"])
        if key not in fetched:
            trade_from, trade_to = hours[key]
            fetched[key] = fetch_pair(db, key, cbr_table, now_utc, in_trading_hours(now_utc, trade_from, trade_to, holidays))
        rates = fetched[key]
        if rates is None:
            continue
        try:
            process_watch(db, tg, watch, rates, chats_by_watch.get(watch["id"], []), now_utc, holidays)
        except (urllib.error.URLError, RuntimeError, OSError, ValueError) as e:
            print(f"watch failed: {type(e).__name__}", file=sys.stderr)

    cutoff = (now_utc - dt.timedelta(days=RETENTION_DAYS)).isoformat()
    db.delete("bank_rate_snapshots", "taken_at=lt." + urllib.parse.quote(cutoff))


def fetch_pair(db: Supabase, key: tuple[str, str, str], cbr_table: dict[str, float] | None, now_utc: dt.datetime, in_hours: bool) -> dict | None:
    provider, from_code, to_code = key
    fetcher = PROVIDERS.get(provider)
    if fetcher is None:
        print(f"unknown provider {provider}", file=sys.stderr)
        return None
    try:
        rates = fetcher(from_code, to_code)
    except (urllib.error.URLError, OSError, ValueError, KeyError) as e:
        print(f"provider {provider} unavailable: {type(e).__name__}", file=sys.stderr)
        return None
    cbr = cross_rate(cbr_table, from_code, to_code) if cbr_table else None
    exchange = rates.get(CATEGORY_EXCHANGE, {}).get("buy")
    card = card_rate(rates, exchange)
    snapshots = snapshot_rows(key, rates, card, cbr, now_utc, in_hours)
    if snapshots:
        db.insert("bank_rate_snapshots", snapshots)
    return {"rates": rates, "cbr": cbr, "card": card, "exchange": exchange}


def snapshot_rows(key: tuple[str, str, str], rates: dict, card: float | None, cbr: float | None, now_utc: dt.datetime, in_hours: bool) -> list[dict]:
    provider, from_code, to_code = key
    rows = []
    for category in SNAPSHOT_CATEGORIES:
        entry = rates.get(category) or {}
        buy = card if category == CATEGORY_CARD else entry.get("buy")
        if buy is None:
            continue
        rows.append({
            "provider": provider, "from_currency": from_code, "to_currency": to_code, "category": category,
            "buy": buy, "sell": entry.get("sell"), "cbr": cbr, "taken_at": now_utc.isoformat(), "in_hours": in_hours,
        })
    return rows


def process_watch(db: Supabase, tg: Telegram, watch: dict, pair: dict, chats: list[int], now_utc: dt.datetime, holidays: set[str]) -> None:
    rate = pair["exchange"]
    if rate is None:
        return
    cbr = pair["cbr"]
    card = pair["card"]
    history = load_history(db, watch, now_utc - dt.timedelta(days=HISTORY_DAYS))
    new_state, events = evaluate(watch, rate, cbr, watch.get("state") or {}, now_utc, history, holidays)
    local = local_now(now_utc, watch.get("timezone") or DEFAULT_TIMEZONE)
    for event in events:
        if event["kind"] == "digest":
            text = message_digest(watch, rate, card, cbr, event, local)
        elif event["kind"] == "target":
            text = message_target(rate, event["target"], event.get("previous"))
        else:
            text = message_signal(watch, rate, card, cbr, event)
        for chat_id in chats:
            try:
                tg.send(chat_id, text)
            except (urllib.error.URLError, RuntimeError, OSError) as e:
                print(f"send failed: {type(e).__name__}", file=sys.stderr)
    db.patch("rate_watches", f"id=eq.{watch['id']}", {"state": new_state})
    print(f"watch checked, {len(events)} event(s)")



def dry_run(from_code: str, to_code: str) -> None:
    rates = fetch_tbank(from_code, to_code)
    exchange = rates.get(CATEGORY_EXCHANGE, {}).get("buy")
    card = card_rate(rates, exchange)
    cbr = cross_rate(fetch_cbr(), from_code, to_code)
    print(f"provider tbank, pair {from_code} → {to_code}, {len(rates)} categories")
    for category in SNAPSHOT_CATEGORIES:
        entry = rates.get(category) or {}
        print(f"  {category}: buy={entry.get('buy')} sell={entry.get('sell')}")
    if exchange is not None:
        print(f"  exchange: {rate_text(from_code, to_code, exchange)}")
    print(f"  card:     {rate_text(from_code, to_code, card) if card is not None else 'n/a'}")
    if cbr is not None:
        print(f"  cbr:      {rate_text(from_code, to_code, cbr)}")
        if exchange is not None:
            print(f"  spread:   {format_percent(spread(exchange, cbr))}")


def main() -> int:
    if "--dry-run" in sys.argv:
        args = [a for a in sys.argv[1:] if not a.startswith("--")]
        if len(args) != 2:
            print("usage: bank_watch.py --dry-run FROM TO", file=sys.stderr)
            return 2
        dry_run(args[0].upper(), args[1].upper())
        return 0
    token = os.environ.get("TELEGRAM_BOT_TOKEN")
    if not token:
        print("TELEGRAM_BOT_TOKEN is not set, nothing to do")
        return 0
    url = os.environ.get("SUPABASE_URL")
    key = os.environ.get("SUPABASE_SERVICE_KEY")
    if not url or not key:
        print("SUPABASE_URL or SUPABASE_SERVICE_KEY is not set", file=sys.stderr)
        return 1
    run(Supabase(url, key), Telegram(token), dt.datetime.now(dt.timezone.utc).replace(microsecond=0))
    return 0


if __name__ == "__main__":
    sys.exit(main())
