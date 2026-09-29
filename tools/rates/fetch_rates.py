#!/usr/bin/env python3
"""Тянет курсы к RUB из open.er-api.com для всех валют из таблицы currencies и кладёт в exchange_rates.

Переменные окружения:
  SUPABASE_URL          https://<ref>.supabase.co
  SUPABASE_SERVICE_KEY  секретный ключ (обходит RLS), только в GitHub Secrets

В лог не печатаются коды валют и значения: логи публичного репозитория видны всем.
"""
import datetime as dt
import json
import os
import sys
import urllib.request

BASE = "RUB"
RATES_URL = f"https://open.er-api.com/v6/latest/{BASE}"
SOURCE = "er-api"


def supabase_request(path: str, method: str = "GET", body: bytes | None = None, prefer: str | None = None):
    url = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1/" + path
    key = os.environ["SUPABASE_SERVICE_KEY"]
    headers = {
        "apikey": key,
        "Authorization": f"Bearer {key}",
        "Content-Type": "application/json",
    }
    if prefer:
        headers["Prefer"] = prefer
    request = urllib.request.Request(url, data=body, method=method, headers=headers)
    with urllib.request.urlopen(request, timeout=30) as response:
        raw = response.read()
        return json.loads(raw) if raw else None


def quote_currencies() -> list[str]:
    rows = supabase_request(f"currencies?select=code&code=neq.{BASE}")
    return [row["code"] for row in rows]


def fetch_rates(quotes: list[str]) -> tuple[str, dict[str, float]]:
    with urllib.request.urlopen(RATES_URL, timeout=30) as response:
        payload = json.load(response)
    if payload.get("result") != "success":
        raise SystemExit("rates API returned an error")
    updated = dt.datetime.fromtimestamp(payload["time_last_update_unix"], tz=dt.timezone.utc)
    quotes_per_base = payload["rates"]
    missing = [q for q in quotes if q not in quotes_per_base]
    if missing:
        raise SystemExit(f"rates API has no data for {len(missing)} of {len(quotes)} currencies")
    return updated.date().isoformat(), {q: 1.0 / quotes_per_base[q] for q in quotes}


def main() -> None:
    dry_run = "--dry-run" in sys.argv
    quotes = quote_currencies()
    rate_date, rates = fetch_rates(quotes)
    rows = [
        {"rate_date": rate_date, "base": BASE, "quote": quote, "rate": round(rate, 10), "source": SOURCE}
        for quote, rate in rates.items()
    ]
    print(f"{rate_date}: {len(rows)} rate(s) prepared")
    if dry_run:
        print("dry run, nothing written")
        return
    supabase_request("exchange_rates", method="POST", body=json.dumps(rows).encode(),
                     prefer="resolution=merge-duplicates,return=minimal")
    print(f"upserted {len(rows)} row(s)")


if __name__ == "__main__":
    main()
