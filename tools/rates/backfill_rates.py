#!/usr/bin/env python3
"""Догружает историю курсов к RUB из архива ЦБ в exchange_rates (source = cbr) — только даты раньше первого уже известного курса валюты.

Нужна, чтобы операции до запуска ежедневной джобы (fetch_rates.py) пересчитывались по курсу своего дня, в том числе при смене базовой валюты.

Переменные окружения:
  SUPABASE_URL          https://<ref>.supabase.co
  SUPABASE_SERVICE_KEY  секретный ключ (обходит RLS), только в GitHub Secrets

Аргументы:
  --from YYYY-MM-DD   с какой даты; по умолчанию — дата первой операции
  --dry-run           только посчитать, ничего не писать

В лог не печатаются коды валют и значения: логи публичного репозитория видны всем.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import sys
import urllib.parse
import urllib.request
import xml.etree.ElementTree as ET

PIVOT = "RUB"
SOURCE = "cbr"
CATALOG_URL = "https://www.cbr.ru/scripts/XML_valFull.asp"
DYNAMIC_URL = "https://www.cbr.ru/scripts/XML_dynamic.asp"
TIMEOUT = 60
CHUNK = 500


def supabase_request(path: str, method: str = "GET", body: bytes | None = None, prefer: str | None = None):
    url = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1/" + path
    key = os.environ["SUPABASE_SERVICE_KEY"]
    headers = {"apikey": key, "Authorization": f"Bearer {key}", "Content-Type": "application/json"}
    if prefer:
        headers["Prefer"] = prefer
    request = urllib.request.Request(url, data=body, method=method, headers=headers)
    with urllib.request.urlopen(request, timeout=TIMEOUT) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def http_bytes(url: str) -> bytes:
    with urllib.request.urlopen(url, timeout=TIMEOUT) as response:
        return response.read()


def parse_catalog(xml_bytes: bytes) -> dict[str, list[str]]:
    ids: dict[str, list[str]] = {}
    for item in ET.fromstring(xml_bytes).iter("Item"):
        code = (item.findtext("ISO_Char_Code") or "").strip().upper()
        if len(code) == 3:
            ids.setdefault(code, []).append(item.get("ID", "").strip())
    return ids


def parse_decimal(text: str) -> float:
    return float(text.strip().replace(" ", "").replace(",", "."))


def parse_dynamic(xml_bytes: bytes) -> dict[str, float]:
    rates: dict[str, float] = {}
    for record in ET.fromstring(xml_bytes).iter("Record"):
        day = dt.datetime.strptime(record.get("Date", ""), "%d.%m.%Y").date().isoformat()
        nominal = parse_decimal(record.findtext("Nominal") or "1")
        value = parse_decimal(record.findtext("Value") or "0")
        if nominal > 0 and value > 0:
            rates[day] = value / nominal
    return rates


def dynamic_url(cbr_id: str, start: dt.date, end: dt.date) -> str:
    query = urllib.parse.urlencode({
        "date_req1": start.strftime("%d/%m/%Y"),
        "date_req2": end.strftime("%d/%m/%Y"),
        "VAL_NM_RQ": cbr_id,
    })
    return f"{DYNAMIC_URL}?{query}"


def missing_rows(code: str, rates: dict[str, float], known_from: str | None) -> list[dict]:
    return [
        {"rate_date": day, "base": PIVOT, "quote": code, "rate": round(rate, 10), "source": SOURCE}
        for day, rate in sorted(rates.items())
        if known_from is None or day < known_from
    ]


def first_operation_date() -> dt.date | None:
    rows = supabase_request("transactions?select=tx_date&order=tx_date.asc&limit=1")
    return dt.date.fromisoformat(rows[0]["tx_date"]) if rows else None


def first_known_date(code: str) -> str | None:
    rows = supabase_request(f"exchange_rates?select=rate_date&base=eq.{PIVOT}&quote=eq.{urllib.parse.quote(code)}&order=rate_date.asc&limit=1")
    return rows[0]["rate_date"] if rows else None


def main() -> int:
    args = sys.argv[1:]
    dry_run = "--dry-run" in args
    start = dt.date.fromisoformat(args[args.index("--from") + 1]) if "--from" in args else first_operation_date()
    if start is None:
        print("no operations, nothing to backfill")
        return 0
    end = dt.date.today() - dt.timedelta(days=1)
    codes = [row["code"] for row in supabase_request(f"currencies?select=code&code=neq.{PIVOT}")]
    catalog = parse_catalog(http_bytes(CATALOG_URL))
    rows: list[dict] = []
    skipped = 0
    for code in codes:
        rates: dict[str, float] = {}
        for cbr_id in catalog.get(code, []):
            rates = parse_dynamic(http_bytes(dynamic_url(cbr_id, start, end)))
            if rates:
                break
        if not rates:
            skipped += 1
            continue
        rows += missing_rows(code, rates, first_known_date(code))
    print(f"{len(codes)} currencies, {skipped} not in the central bank archive, {len(rows)} new rows from {start}")
    if dry_run or not rows:
        return 0
    for index in range(0, len(rows), CHUNK):
        supabase_request(
            "exchange_rates?on_conflict=rate_date,base,quote,source",
            method="POST",
            body=json.dumps(rows[index:index + CHUNK]).encode(),
            prefer="resolution=ignore-duplicates,return=minimal",
        )
    print("done")
    return 0


if __name__ == "__main__":
    sys.exit(main())
