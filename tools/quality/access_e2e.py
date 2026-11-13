#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import secrets
import sys
import urllib.error
import urllib.parse
import urllib.request
from concurrent.futures import ThreadPoolExecutor

DOMAIN = "example.test"


class Api:
    def __init__(self, base: str, key: str):
        self.base = base.rstrip("/")
        self.key = key

    def call(self, path: str, method: str = "GET", body: dict | None = None, token: str | None = None) -> tuple[int, object]:
        headers = {"apikey": self.key, "Content-Type": "application/json"}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.base + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                raw = response.read()
                return response.status, json.loads(raw) if raw else None
        except urllib.error.HTTPError as error:
            raw = error.read()
            try:
                return error.code, json.loads(raw)
            except ValueError:
                return error.code, raw.decode(errors="replace")

    def login(self, email: str, password: str) -> tuple[int, dict]:
        status, body = self.call("/auth/v1/token?grant_type=password", "POST", {"email": email, "password": password})
        return status, body if isinstance(body, dict) else {}

    def admin(self, token: str | None, **body) -> tuple[int, object]:
        return self.call("/functions/v1/admin", "POST", body, token)

    def redeem(self, code: str, email: str, password: str) -> tuple[int, object]:
        return self.call("/functions/v1/redeem-invite", "POST", {"code": code, "email": email, "password": password})


def param(url: str, name: str) -> str:
    parsed = urllib.parse.urlparse(url)
    if parsed.query:
        raise ValueError("код в query попадёт в логи сайта, нужен фрагмент #")
    return urllib.parse.parse_qs(parsed.fragment)[name][0]


def main() -> int:
    api = Api(os.environ["API_URL"], os.environ["ANON_KEY"])
    admin_email = os.environ["ADMIN_EMAIL"]
    admin_password = os.environ["ADMIN_PASSWORD"]
    run = secrets.token_hex(4)
    password = "e2e-" + secrets.token_urlsafe(12)
    fails: list[str] = []

    def check(ok: bool, message: str) -> None:
        if not ok:
            fails.append(message)

    status, session = api.login(admin_email, admin_password)
    admin_token = session.get("access_token")
    if not admin_token:
        detail = {k: session.get(k) for k in ("error_code", "msg", "error", "error_description") if session.get(k)}
        print(f"FAIL админ не вошёл ({status} {detail})")
        return 1

    status, _ = api.admin(None, action="users_list")
    check(status == 401, f"A7 админ-функция без входа отвечает {status}, ожидался 401")

    new_email = f"new-{run}@{DOMAIN}"
    status, invite = api.admin(admin_token, action="invite_create", email=new_email.upper())
    check(status == 200 and isinstance(invite, dict) and "url" in invite, f"A6 приглашение не создано ({status})")
    code = param(invite["url"], "invite") if isinstance(invite, dict) and "url" in invite else ""

    status, _ = api.redeem(code, f"other-{run}@{DOMAIN}", password)
    check(status == 400, f"A6 приглашение принято с чужой почтой ({status})")
    status, body = api.redeem(code, new_email, "short")
    check(status == 400, f"A6 принят короткий пароль ({status})")
    status, body = api.redeem(code, new_email, password)
    check(status == 200, f"A6 верное приглашение не погашено ({status} {body})")
    status, _ = api.redeem(code, new_email, password)
    check(status == 400, f"A6 приглашение погашено второй раз ({status})")

    status, user_session = api.login(new_email, password)
    user_token = user_session.get("access_token")
    check(bool(user_token), f"новый пользователь не вошёл ({status})")

    if user_token:
        status, _ = api.admin(user_token, action="users_list")
        check(status == 403, f"A7 обычный пользователь вызвал админ-функцию ({status})")
        status, _ = api.call("/auth/v1/user", "PUT", {"data": {"role": "admin"}}, user_token)
        status, _ = api.admin(user_token, action="users_list")
        check(status == 403, "A7 user_metadata сделала пользователя админом")

    race_email = f"race-{run}@{DOMAIN}"
    status, race = api.admin(admin_token, action="invite_create", email=race_email)
    race_code = param(race["url"], "invite") if isinstance(race, dict) and "url" in race else ""
    with ThreadPoolExecutor(max_workers=10) as pool:
        results = list(pool.map(lambda _: api.redeem(race_code, race_email, password)[0], range(10)))
    check(results.count(200) == 1, f"A6 10 параллельных погашений: успешных {results.count(200)}, ожидалось 1")

    gone_email = f"gone-{run}@{DOMAIN}"
    _, gone = api.admin(admin_token, action="invite_create", email=gone_email)
    _, invites = api.admin(admin_token, action="invite_list")
    gone_id = next((i["id"] for i in invites if i["email"] == gone_email), None) if isinstance(invites, list) else None
    api.admin(admin_token, action="invite_revoke", id=gone_id)
    status, _ = api.redeem(param(gone["url"], "invite"), gone_email, password)
    check(status == 400, f"A6 отозванное приглашение погашено ({status})")

    status, users = api.admin(admin_token, action="users_list")
    new_user = next((u for u in users if u.get("email") == new_email), None) if isinstance(users, list) else None
    check(new_user is not None, "новый пользователь не виден админу")
    if isinstance(users, list) and users:
        check(set(users[0]) == {"id", "email", "created_at", "last_sign_in_at", "blocked"}, f"A8 админ видит лишние поля: {sorted(users[0])}")

    if new_user and user_token:
        status, link = api.admin(admin_token, action="reset_link", user_id=new_user["id"])
        token_hash = param(link["url"], "reset") if isinstance(link, dict) and "url" in link else ""
        status, verified = api.call("/auth/v1/verify", "POST", {"type": "recovery", "token_hash": token_hash})
        check(status == 200 and isinstance(verified, dict) and verified.get("access_token"), f"A11 ссылка сброса не сработала ({status})")
        status, _ = api.call("/auth/v1/verify", "POST", {"type": "recovery", "token_hash": token_hash})
        check(status >= 400, f"A11 ссылка сброса сработала второй раз ({status})")
        _, actions = api.call("/rest/v1/owner_actions?select=action", token=user_token)
        seen = {a["action"] for a in actions} if isinstance(actions, list) else set()
        check({"invite_redeemed", "reset_link_created"} <= seen, f"A11 журнал пользователя неполный: {sorted(seen)}")

        status, _ = api.admin(admin_token, action="block", user_id=new_user["id"])
        check(status == 200, f"A12 блокировка не прошла ({status})")
        status, rows = api.call("/rest/v1/accounts?select=id", token=user_token)
        check(status >= 400, f"A12 заблокированный читает данные старым токеном ({status})")
        status, _ = api.login(new_email, password)
        check(status >= 400, f"A12 заблокированный вошёл ({status})")
        refresh = user_session.get("refresh_token")
        status, _ = api.call("/auth/v1/token?grant_type=refresh_token", "POST", {"refresh_token": refresh})
        check(status >= 400, f"A12 заблокированный обновил токен ({status})")
        status, _ = api.admin(admin_token, action="unblock", user_id=new_user["id"])
        status, _ = api.login(new_email, password)
        check(status == 200, f"A12 после разблокировки вход не работает ({status})")

    admin_id = next((u["id"] for u in users if u.get("email") == admin_email.lower()), None) if isinstance(users, list) else None
    if admin_id:
        status, _ = api.admin(admin_token, action="block", user_id=admin_id)
        check(status == 400, f"админ заблокировал админа ({status})")
        status, _ = api.admin(admin_token, action="reset_link", user_id=admin_id)
        check(status == 400, f"админ получил ссылку входа в учётку админа ({status})")

    status, _ = api.call("/auth/v1/signup", "POST", {"email": f"stranger-{run}@{DOMAIN}", "password": password})
    check(status >= 400, f"A6 регистрация без приглашения открыта ({status})")

    for fail in fails:
        print(f"FAIL {fail}")
    print("access e2e: " + ("ok" if not fails else f"нарушений {len(fails)}"))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
