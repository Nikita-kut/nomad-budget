#!/usr/bin/env python3
"""Тянет курсы RUB→USD/VND из open.er-api.com и кладёт в Supabase exchange_rates.

Переменные окружения:
  SUPABASE_URL          https://<ref>.supabase.co
  SUPABASE_SERVICE_KEY  секретный ключ (обходит RLS), только в GitHub Secrets
"""
import datetime as dt
import json
import os
import sys
import urllib.request

RATES_URL = "https://open.er-api.com/v6/latest/RUB"
QUOTES = ("USD", "VND")
SOURCE = "er-api"


def fetch_rates() -> tuple[str, dict[str, float]]:
    with urllib.request.urlopen(RATES_URL, timeout=30) as response:
        payload = json.load(response)
    if payload.get("result") != "success":
        raise SystemExit(f"API error: {payload}")
    updated = dt.datetime.fromtimestamp(payload["time_last_update_unix"], tz=dt.timezone.utc)
    rate_date = updated.date().isoformat()
    quotes_per_rub = payload["rates"]
    rub_per_quote = {q: 1.0 / quotes_per_rub[q] for q in QUOTES}
    return rate_date, rub_per_quote


def upsert(rows: list[dict]) -> None:
    url = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1/exchange_rates"
    key = os.environ["SUPABASE_SERVICE_KEY"]
    body = json.dumps(rows).encode()
    request = urllib.request.Request(
        url,
        data=body,
        method="POST",
        headers={
            "apikey": key,
            "Authorization": f"Bearer {key}",
            "Content-Type": "application/json",
            "Prefer": "resolution=merge-duplicates,return=minimal",
        },
    )
    with urllib.request.urlopen(request, timeout=30) as response:
        if response.status not in (200, 201, 204):
            raise SystemExit(f"Supabase error: {response.status} {response.read()}")


def main() -> None:
    rate_date, rates = fetch_rates()
    rows = [
        {"rate_date": rate_date, "base": "RUB", "quote": quote, "rate": round(rate, 10), "source": SOURCE}
        for quote, rate in rates.items()
    ]
    for row in rows:
        print(f"{row['rate_date']} 1 {row['quote']} = {row['rate']:.6f} RUB")
    if "--dry-run" in sys.argv:
        print("dry run, nothing written")
        return
    upsert(rows)
    print(f"upserted {len(rows)} rows")


if __name__ == "__main__":
    main()
