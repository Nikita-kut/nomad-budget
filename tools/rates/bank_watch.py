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
CATEGORY_LABELS = {CATEGORY_EXCHANGE: "обмен в приложении", CATEGORY_CARD: "оплата картой"}
SNAPSHOT_CATEGORIES = (CATEGORY_EXCHANGE, CATEGORY_CARD)

RUB = "RUB"
DEFAULT_TIMEZONE = "Europe/Moscow"
DEFAULT_QUIET_FROM = 18
DEFAULT_QUIET_TO = 10
RETENTION_DAYS = 180
HISTORY_DAYS = 7
CARD_SANITY_RATIO = 0.5
PERCENT_EPSILON = 1e-9
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


# ---------- Время и тихие часы ----------

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


# ---------- Сигналы ----------

def evaluate(watch: dict, rate: float, state: dict, local: dt.datetime) -> tuple[dict, list[dict]]:
    """Возвращает новое состояние слежения и события для отправки: target, jump, digest, quiet_summary."""
    target = _float_or_none(watch.get("target"))
    jump_percent = float(watch.get("jump_percent") or 1.5)
    digest_hour = watch.get("digest_hour")
    quiet = in_quiet_hours(local.hour, int(watch.get("quiet_from", DEFAULT_QUIET_FROM)), int(watch.get("quiet_to", DEFAULT_QUIET_TO)))

    last = _float_or_none(state.get("last"))
    above_before = bool(state.get("above_target"))
    above_now = target is not None and rate >= target
    reached = above_now and not above_before
    jump = None
    if last is not None and last > 0:
        change = rate / last - 1.0
        if abs(change) * 100 >= jump_percent - PERCENT_EPSILON:
            jump = {"from": last, "to": rate, "change": change}

    pending = dict(state.get("pending") or {})
    events: list[dict] = []
    if quiet:
        if reached or jump:
            pending.setdefault("since", local.isoformat(timespec="minutes"))
            pending["min"] = min(rate, pending.get("min", rate))
            pending["max"] = max(rate, pending.get("max", rate))
            pending["target"] = bool(pending.get("target")) or reached
            pending["jump"] = bool(pending.get("jump")) or jump is not None
    else:
        if pending:
            events.append({"kind": "quiet_summary", "pending": pending})
            pending = {}
        if reached:
            events.append({"kind": "target", "target": target})
        elif jump:
            events.append({"kind": "jump", **jump})

    today = local.date().isoformat()
    if digest_hour is not None and not quiet and local.hour == int(digest_hour) and state.get("last_digest_date") != today:
        events.append({"kind": "digest"})
        last_digest = today
    else:
        last_digest = state.get("last_digest_date")

    new_state = {
        "last": rate,
        "last_at": local.isoformat(timespec="minutes"),
        "above_target": above_now,
        "pending": pending,
        "last_digest_date": last_digest,
    }
    return new_state, events


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


def spread(bank: float, cbr: float | None) -> float | None:
    if not cbr:
        return None
    return bank / cbr - 1.0


# ---------- Сообщения ----------

def pair_title(watch: dict) -> str:
    return f"{watch['from_currency']} → {watch['to_currency']}"


def cbr_line(from_code: str, to_code: str, bank: float, cbr: float | None, unit: int) -> str:
    if cbr is None:
        return "ЦБ: курса нет"
    return f"ЦБ: {rate_text(from_code, to_code, cbr, unit)} · разница {format_percent(spread(bank, cbr) or 0.0)}"


def message_target(watch: dict, rate: float, cbr: float | None, target: float) -> str:
    f, t = watch["from_currency"], watch["to_currency"]
    unit = quote_unit(rate)
    return (
        f"🎯 <b>{pair_title(watch)}</b>: цель достигнута\n"
        f"{rate_text(f, t, rate, unit)} · цель {format_quote(target * unit)}\n"
        f"{cbr_line(f, t, rate, cbr, unit)}"
    )


def message_jump(watch: dict, rate: float, cbr: float | None, previous: float, change: float) -> str:
    f, t = watch["from_currency"], watch["to_currency"]
    unit = quote_unit(rate)
    arrow = "▲" if change > 0 else "▼"
    return (
        f"{arrow} <b>{pair_title(watch)}</b>: {format_percent(change)} с прошлой проверки\n"
        f"{rate_text(f, t, rate, unit)} · было {format_quote(previous * unit)}\n"
        f"{cbr_line(f, t, rate, cbr, unit)}"
    )


def message_quiet_summary(watch: dict, rate: float, cbr: float | None, pending: dict) -> str:
    f, t = watch["from_currency"], watch["to_currency"]
    unit = quote_unit(rate)
    parts = []
    if pending.get("target"):
        parts.append("достигалась цель")
    if pending.get("jump"):
        parts.append("были резкие скачки")
    low, high = pending.get("min"), pending.get("max")
    span = f", курс от {format_quote(low * unit)} до {format_quote(high * unit)}" if low is not None and high is not None else ""
    return (
        f"🌙 <b>{pair_title(watch)}</b>: пока уведомления не отправлялись, {', '.join(parts) or 'событий не было'}{span}\n"
        f"Сейчас {rate_text(f, t, rate, unit)}\n"
        f"{cbr_line(f, t, rate, cbr, unit)}"
    )


def message_digest(watch: dict, rate: float, card: float | None, cbr: float | None, history: list[float], local: dt.datetime) -> str:
    f, t = watch["from_currency"], watch["to_currency"]
    unit = quote_unit(rate)
    target = _float_or_none(watch.get("target"))
    lines = [
        f"☀️ <b>{pair_title(watch)}</b>, {local.strftime('%H:%M')}",
        f"Обмен в приложении: {rate_text(f, t, rate, unit)}",
    ]
    if card is not None:
        lines.append(f"Оплата картой: {rate_text(f, t, card, unit)}")
    lines.append(cbr_line(f, t, rate, cbr, unit))
    if history:
        lines.append(f"За {HISTORY_DAYS} дней: мин {format_quote(min(history) * unit)} · макс {format_quote(max(history) * unit)}")
    lines.append(f"Цель: {format_quote(target * unit)}" if target is not None else "Цель не задана")
    return "\n".join(lines)


def subscribe_text(watch: dict) -> str:
    label = CATEGORY_LABELS.get(watch.get("category"), watch.get("category"))
    return f"Подписка на курс включена: <b>{pair_title(watch)}</b>, {label}.\nОтписаться — /stop"


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


def load_history(db: Supabase, watch: dict, since: dt.datetime) -> list[float]:
    query = (
        f"select=buy&provider=eq.{watch['provider']}&from_currency=eq.{watch['from_currency']}&to_currency=eq.{watch['to_currency']}"
        f"&category=eq.{watch['category']}&taken_at=gte.{urllib.parse.quote(since.isoformat())}&order=taken_at.asc&limit=1000"
    )
    return [float(row["buy"]) for row in db.select("bank_rate_snapshots", query)]


def run(db: Supabase, tg: Telegram, now_utc: dt.datetime) -> None:
    me = tg.call("getMe")
    rows = db.select("bot_info", "select=*&id=eq.1")
    info = rows[0] if rows else {}
    offset = handle_updates(db, tg, info)
    db.upsert("bot_info", {"id": 1, "username": me.get("username"), "update_offset": offset, "updated_at": now_utc.isoformat()})

    watches = db.select("rate_watches", "select=*&enabled=is.true")
    subscribers = db.select("telegram_subscribers", "select=watch_id,chat_id")
    chats_by_watch: dict[str, list[int]] = {}
    for row in subscribers:
        chats_by_watch.setdefault(row["watch_id"], []).append(int(row["chat_id"]))
    print(f"{len(watches)} watch(es), {len(subscribers)} subscriber(s)")

    cbr_table: dict[str, float] | None = None
    try:
        cbr_table = fetch_cbr()
    except (urllib.error.URLError, ET.ParseError, OSError, ValueError) as e:
        print(f"central bank rates unavailable: {type(e).__name__}", file=sys.stderr)

    fetched: dict[tuple[str, str, str], dict] = {}
    for watch in watches:
        key = (watch["provider"], watch["from_currency"], watch["to_currency"])
        if key not in fetched:
            fetched[key] = fetch_pair(db, key, cbr_table, now_utc)
        rates = fetched[key]
        if rates is None:
            continue
        try:
            process_watch(db, tg, watch, rates, chats_by_watch.get(watch["id"], []), now_utc)
        except (urllib.error.URLError, RuntimeError, OSError, ValueError) as e:
            print(f"watch failed: {type(e).__name__}", file=sys.stderr)

    cutoff = (now_utc - dt.timedelta(days=RETENTION_DAYS)).isoformat()
    db.delete("bank_rate_snapshots", "taken_at=lt." + urllib.parse.quote(cutoff))


def fetch_pair(db: Supabase, key: tuple[str, str, str], cbr_table: dict[str, float] | None, now_utc: dt.datetime) -> dict | None:
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
    snapshots = []
    for category in SNAPSHOT_CATEGORIES:
        entry = rates.get(category) or {}
        buy = card if category == CATEGORY_CARD else entry.get("buy")
        if buy is None:
            continue
        snapshots.append({
            "provider": provider, "from_currency": from_code, "to_currency": to_code, "category": category,
            "buy": buy, "sell": entry.get("sell"), "cbr": cbr, "taken_at": now_utc.isoformat(),
        })
    if snapshots:
        db.insert("bank_rate_snapshots", snapshots)
    return {"rates": rates, "cbr": cbr, "card": card, "exchange": exchange}


def process_watch(db: Supabase, tg: Telegram, watch: dict, pair: dict, chats: list[int], now_utc: dt.datetime) -> None:
    category = watch["category"]
    rate = pair["card"] if category == CATEGORY_CARD else pair["rates"].get(category, {}).get("buy")
    if rate is None:
        return
    cbr = pair["cbr"]
    local = local_now(now_utc, watch.get("timezone") or DEFAULT_TIMEZONE)
    state = watch.get("state") or {}
    new_state, events = evaluate(watch, rate, state, local)
    for event in events:
        kind = event["kind"]
        if kind == "target":
            text = message_target(watch, rate, cbr, event["target"])
        elif kind == "jump":
            text = message_jump(watch, rate, cbr, event["from"], event["change"])
        elif kind == "quiet_summary":
            text = message_quiet_summary(watch, rate, cbr, event["pending"])
        else:
            history = load_history(db, watch, now_utc - dt.timedelta(days=HISTORY_DAYS))
            text = message_digest(watch, rate, pair["card"], cbr, history, local)
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
