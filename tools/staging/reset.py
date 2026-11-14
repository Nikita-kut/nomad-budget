#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import secrets
import sys
import urllib.request
from pathlib import Path

ROOT = Path(__file__).resolve().parents[2]
PROD_REF = "gncrjdgznnriuhyuatcv"
DOMAIN = "example.test"
USERS = {
    "a": "seed",
    "b": "seed",
    "admin": "seed",
    "blocked": "blocked",
    "fresh": "empty",
}


def access_token() -> str:
    token = os.environ.get("SUPABASE_ACCESS_TOKEN")
    if token:
        return token
    sys.path.insert(0, str(ROOT / "tools" / "quality"))
    import reconcile

    return reconcile.token()


def query(ref: str, token: str, sql: str) -> list[dict]:
    body = json.dumps({"query": sql}).encode()
    request = urllib.request.Request(
        f"https://api.supabase.com/v1/projects/{ref}/database/query",
        data=body,
        method="POST",
        headers={"Authorization": f"Bearer {token}", "Content-Type": "application/json"},
    )
    with urllib.request.urlopen(request, timeout=120) as response:
        return json.loads(response.read())


def literal(value: str) -> str:
    return "'" + value.replace("'", "''") + "'"


def reset_sql(password: str) -> str:
    emails = ", ".join(literal(f"{name}@{DOMAIN}") for name in USERS)
    parts = [f"delete from auth.users where email in ({emails}) or email like {literal('%@' + DOMAIN)};"]
    for name, kind in USERS.items():
        email = f"{name}@{DOMAIN}"
        parts.append(f"select tests.create_user({literal(email)});")
        parts.append(
            f"update auth.users set encrypted_password = crypt({literal(password)}, gen_salt('bf')) where email = {literal(email)};"
        )
        if kind in ("seed", "blocked"):
            parts.append(f"select tests.seed_finance((select id from auth.users where email = {literal(email)}), {literal(name.upper())});")
        if kind == "blocked":
            parts.append(f"update auth.users set banned_until = now() + interval '100 years' where email = {literal(email)};")
            parts.append(f"insert into public.account_blocks (user_id) select id from auth.users where email = {literal(email)};")
        if name == "admin":
            parts.append(f"insert into public.app_admins (user_id) select id from auth.users where email = {literal(email)};")
    return "\n".join(parts)


def main() -> int:
    ref = os.environ.get("STAGING_PROJECT_REF", "").strip()
    if not ref or ref == PROD_REF:
        sys.exit("STAGING_PROJECT_REF пуст или указывает на прод — сброс только для staging")
    password = os.environ.get("STAGING_TEST_PASSWORD") or secrets.token_urlsafe(18)
    token = access_token()
    query(ref, token, (ROOT / "supabase" / "tests" / "helpers.sql").read_text(encoding="utf-8"))
    query(ref, token, reset_sql(password))
    rows = query(ref, token, f"select email, banned_until is not null as blocked from auth.users where email like {literal('%@' + DOMAIN)} order by email")
    for row in rows:
        print(f"{row['email']}{' (заблокирован)' if row['blocked'] else ''}")
    if not os.environ.get("STAGING_TEST_PASSWORD"):
        print("Пароль сгенерирован и нигде не сохранён: задай STAGING_TEST_PASSWORD, чтобы входить тестовыми пользователями")
    return 0


if __name__ == "__main__":
    sys.exit(main())
