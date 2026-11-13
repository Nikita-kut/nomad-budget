#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

PROD_URL = "https://gncrjdgznnriuhyuatcv.supabase.co"
PROD_KEY = "sb_publishable_Q9BgpKUePZpEYW-i5KofKg_shXWf7PL"
USER_TABLES = ["accounts", "transactions", "debts", "budget_lines", "user_settings", "rate_watches", "telegram_subscribers"]
CLIENT_RPCS = ["delete_own_account", "change_base_currency", "revert_base_currency", "close_debt", "ensure_currency"]


def status(url: str, key: str, method: str = "GET", body: dict | None = None) -> tuple[int, str]:
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers={"apikey": key, "Content-Type": "application/json"})
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            return response.status, response.read().decode(errors="replace")
    except urllib.error.HTTPError as error:
        return error.code, error.read().decode(errors="replace")


def probe(base: str, key: str, strict: bool) -> tuple[list[str], list[str]]:
    failures = []
    warnings = []
    code, _ = status(f"{base}/auth/v1/signup", key, "POST", {"email": "probe@example.test", "password": "probe-password-123"})
    if code < 300:
        failures.append(f"регистрация открыта ({code})")
    code, _ = status(f"{base}/auth/v1/settings", key)
    if code != 200:
        failures.append(f"Auth не отвечает ({code})")
    for table in USER_TABLES:
        code, text = status(f"{base}/rest/v1/{table}?select=*&limit=1", key)
        if code < 300 and text.strip() not in ("[]", ""):
            failures.append(f"аноним читает {table}")
        elif code < 300:
            (failures if strict else warnings).append(f"аноним получает пустой ответ {code} по {table}, ожидался отказ")
    for rpc in CLIENT_RPCS:
        code, _ = status(f"{base}/rest/v1/rpc/{rpc}", key, "POST", {})
        if code < 300:
            failures.append(f"аноним вызвал {rpc} ({code})")
    return failures, warnings


def main() -> int:
    base = os.environ.get("PROBE_URL", PROD_URL).rstrip("/")
    key = os.environ.get("PROBE_KEY", PROD_KEY)
    failures, warnings = probe(base, key, os.environ.get("PROBE_STRICT") == "1")
    for warning in warnings:
        print(f"WARN {warning}")
    for failure in failures:
        print(f"FAIL {failure}")
    print("prod probe: " + ("ok" if not failures else f"нарушений {len(failures)}"))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
