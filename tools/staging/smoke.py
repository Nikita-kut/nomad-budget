#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import sys
import urllib.error
import urllib.request

DOMAIN = "example.test"


def call(url: str, key: str, method: str = "GET", body: dict | None = None, token: str | None = None) -> tuple[int, object]:
    headers = {"apikey": key, "Content-Type": "application/json"}
    if token:
        headers["Authorization"] = f"Bearer {token}"
    data = json.dumps(body).encode() if body is not None else None
    request = urllib.request.Request(url, data=data, method=method, headers=headers)
    try:
        with urllib.request.urlopen(request, timeout=30) as response:
            raw = response.read()
            return response.status, json.loads(raw) if raw else None
    except urllib.error.HTTPError as error:
        raw = error.read()
        try:
            return error.code, json.loads(raw)
        except ValueError:
            return error.code, raw.decode(errors="replace")


def login(base: str, key: str, email: str, password: str) -> tuple[int, str | None]:
    status, body = call(f"{base}/auth/v1/token?grant_type=password", key, "POST", {"email": email, "password": password})
    return status, body.get("access_token") if isinstance(body, dict) else None


def main() -> int:
    base = os.environ["STAGING_SUPABASE_URL"].rstrip("/")
    key = os.environ["STAGING_SUPABASE_KEY"]
    password = os.environ["STAGING_TEST_PASSWORD"]
    if "gncrjdgznnriuhyuatcv" in base:
        sys.exit("smoke только для staging")
    failures = []

    status, _ = call(f"{base}/auth/v1/signup", key, "POST", {"email": f"stranger@{DOMAIN}", "password": "stranger-password-1"})
    if status < 300:
        failures.append(f"регистрация без приглашения открыта ({status})")

    status, body = call(f"{base}/rest/v1/accounts?select=id", key)
    if status < 300 and body:
        failures.append("аноним читает счета")

    tokens = {}
    for name in ("a", "b"):
        status, token = login(base, key, f"{name}@{DOMAIN}", password)
        if not token:
            failures.append(f"{name}@ не вошёл ({status})")
        tokens[name] = token

    status, token = login(base, key, f"blocked@{DOMAIN}", password)
    if token:
        failures.append("заблокированный пользователь вошёл")

    if tokens.get("a") and tokens.get("b"):
        _, a_accounts = call(f"{base}/rest/v1/accounts?select=id,name", key, token=tokens["a"])
        _, b_accounts = call(f"{base}/rest/v1/accounts?select=id,name", key, token=tokens["b"])
        if not a_accounts or not b_accounts:
            failures.append("у тестовых пользователей нет счетов — сид не применён")
        elif any(not row["name"].startswith("A ") for row in a_accounts) or any(not row["name"].startswith("B ") for row in b_accounts):
            failures.append("пользователь видит чужие счета")
        a_ids = {row["id"] for row in a_accounts or []}
        if a_ids:
            ids = ",".join(sorted(a_ids))
            status, body = call(f"{base}/rest/v1/accounts?id=in.({ids})", key, "PATCH", {"name": "B was here"}, token=tokens["b"])
            _, after = call(f"{base}/rest/v1/accounts?select=name", key, token=tokens["a"])
            if any(row["name"] == "B was here" for row in after or []):
                failures.append("B переименовал счёт A")

    for failure in failures:
        print(f"FAIL {failure}")
    print("staging smoke: " + ("ok" if not failures else f"нарушений {len(failures)}"))
    return 1 if failures else 0


if __name__ == "__main__":
    sys.exit(main())
