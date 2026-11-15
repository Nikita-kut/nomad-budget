#!/usr/bin/env python3
from __future__ import annotations

import json
import os
import secrets
import struct
import sys
import urllib.error
import urllib.parse
import urllib.request
import uuid
import zlib

DOMAIN = "example.test"


def png(width: int = 4, height: int = 4) -> bytes:
    def chunk(kind: bytes, data: bytes) -> bytes:
        return struct.pack(">I", len(data)) + kind + data + struct.pack(">I", zlib.crc32(kind + data) & 0xFFFFFFFF)
    raw = b"".join(b"\x00" + b"\x80\x80\x80" * width for _ in range(height))
    return b"\x89PNG\r\n\x1a\n" + chunk(b"IHDR", struct.pack(">IIBBBBB", width, height, 8, 2, 0, 0, 0)) + chunk(b"IDAT", zlib.compress(raw)) + chunk(b"IEND", b"")


class Api:
    def __init__(self, base: str, key: str):
        self.base = base.rstrip("/")
        self.key = key

    def raw(self, path: str, method: str = "GET", data: bytes | None = None, token: str | None = None, content_type: str = "application/json", extra: dict | None = None) -> tuple[int, bytes]:
        headers = {"apikey": self.key, "Content-Type": content_type, **(extra or {})}
        if token:
            headers["Authorization"] = f"Bearer {token}"
        url = path if path.startswith("http") else self.base + path
        request = urllib.request.Request(url, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(request, timeout=60) as response:
                return response.status, response.read()
        except urllib.error.HTTPError as error:
            return error.code, error.read()

    def call(self, path: str, method: str = "GET", body: object = None, token: str | None = None, extra: dict | None = None) -> tuple[int, object]:
        status, raw = self.raw(path, method, json.dumps(body).encode() if body is not None else None, token, extra=extra)
        try:
            return status, json.loads(raw) if raw else None
        except ValueError:
            return status, raw.decode(errors="replace")

    def login(self, email: str, password: str) -> str | None:
        _, body = self.call("/auth/v1/token?grant_type=password", "POST", {"email": email, "password": password})
        return body.get("access_token") if isinstance(body, dict) else None

    def user_id(self, token: str) -> str:
        _, body = self.call("/auth/v1/user", token=token)
        return body["id"]

    def admin(self, token: str | None, **body) -> tuple[int, object]:
        return self.call("/functions/v1/admin", "POST", body, token)


def main() -> int:
    api = Api(os.environ["API_URL"], os.environ["ANON_KEY"])
    run = secrets.token_hex(4)
    password = "e2e-" + secrets.token_urlsafe(12)
    fails: list[str] = []

    def check(ok: bool, message: str) -> None:
        if not ok:
            fails.append(message)

    admin_token = api.login(os.environ["ADMIN_EMAIL"], os.environ["ADMIN_PASSWORD"])
    if not admin_token:
        print("FAIL админ не вошёл")
        return 1

    tokens = {}
    for name in ("a", "b"):
        email = f"t{name}-{run}@{DOMAIN}"
        _, invite = api.admin(admin_token, action="invite_create", email=email)
        code = urllib.parse.parse_qs(urllib.parse.urlparse(invite["url"]).fragment)["invite"][0]
        api.call("/functions/v1/redeem-invite", "POST", {"code": code, "email": email, "password": password})
        tokens[name] = api.login(email, password)
    a, b = tokens["a"], tokens["b"]
    if not a or not b:
        print("FAIL тестовые пользователи не созданы")
        return 1
    a_id, b_id = api.user_id(a), api.user_id(b)

    a_path = f"{a_id}/{uuid.uuid4()}.png"
    status, _ = api.raw(f"/storage/v1/object/tickets/{a_path}", "POST", png(), a, "image/png")
    check(status == 200, f"A9 A не загрузил скриншот в свою папку ({status})")
    status, _ = api.raw(f"/storage/v1/object/tickets/{b_id}/{uuid.uuid4()}.png", "POST", png(), a, "image/png")
    check(status >= 400, f"A9 A загрузил скриншот в папку B ({status})")
    status, _ = api.raw(f"/storage/v1/object/tickets/{a_path}", "POST", png(8, 8), a, "image/png", {"x-upsert": "true"})
    check(status >= 400, f"A9 A перезаписал свой скриншот ({status})")
    status, _ = api.raw(f"/storage/v1/object/tickets/{a_id}/{uuid.uuid4()}.png", "POST", b"<svg/>", a, "image/svg+xml")
    check(status >= 400, f"A9 принят не PNG ({status})")
    status, _ = api.raw(f"/storage/v1/object/tickets/{a_id}/{uuid.uuid4()}.png", "POST", png() + b"\x00" * (2 * 1024 * 1024 + 1), a, "image/png")
    check(status >= 400, f"A9 принят файл больше 2 МБ ({status})")

    status, _ = api.raw(f"/storage/v1/object/authenticated/tickets/{a_path}", token=b)
    check(status >= 400, f"A9 B скачал скриншот A ({status})")
    status, listing = api.call("/storage/v1/object/list/tickets", "POST", {"prefix": f"{a_id}/"}, b)
    check(not listing if status < 400 else True, "A9 B видит список файлов A")
    status, _ = api.raw(f"/storage/v1/object/authenticated/tickets/{a_path}")
    check(status >= 400, f"A9 аноним скачал скриншот ({status})")
    status, _ = api.raw(f"/storage/v1/object/public/tickets/{a_path}")
    check(status >= 400, f"A9 корзина публичная ({status})")
    status, _ = api.raw(f"/storage/v1/object/authenticated/tickets/{a_path}", token=a)
    check(status == 200, f"A9 автор не видит свой скриншот ({status})")

    status, created = api.call("/rest/v1/tickets?select=id,number", "POST",
                               {"kind": "bug", "body": "Кнопка <b>не</b> работает & ничего", "screen": "month", "mark_x": 0.4, "mark_y": 0.6,
                                "tech": {"platform": "web", "version": "0.0.0"}, "screenshot_path": a_path},
                               a, {"Prefer": "return=representation"})
    check(status == 201, f"обращение не создано ({status} {created})")
    ticket_id = created[0]["id"] if isinstance(created, list) and created else None

    status, _ = api.call("/rest/v1/tickets", "POST", {"kind": "bug", "body": "чужой скриншот", "screenshot_path": a_path}, b)
    check(status >= 400, f"B создал обращение со скриншотом A ({status})")
    status, mine = api.call("/rest/v1/tickets?select=id", token=b)
    check(isinstance(mine, list) and not any(t["id"] == ticket_id for t in mine), "B видит обращение A")
    status, _ = api.call(f"/rest/v1/tickets?id=eq.{ticket_id}", "PATCH", {"status": "done"}, a)
    status, own = api.call(f"/rest/v1/tickets?id=eq.{ticket_id}&select=status", token=a)
    check(isinstance(own, list) and own and own[0]["status"] == "new", "автор сменил статус")

    status, _ = api.admin(b, action="tickets_list")
    check(status == 403, f"обычный пользователь читает все обращения ({status})")
    status, listed = api.admin(admin_token, action="tickets_list")
    row = next((t for t in listed if t.get("id") == ticket_id), None) if isinstance(listed, list) else None
    check(row is not None, f"админ не видит обращение ({status})")
    if row:
        check("screenshot_path" not in row and "user_id" not in row, "в списке админа лишние поля")
        url = row.get("screenshot_url") or ""
        local = api.base + "/storage/v1" + url.split("/storage/v1", 1)[1] if "/storage/v1" in url else ""
        status, image = api.raw(local) if local else (0, b"")
        check(status == 200 and image.startswith(b"\x89PNG"), f"админ не открыл скриншот по подписанной ссылке ({status})")
    status, _ = api.admin(admin_token, action="ticket_status", id=ticket_id, status="in_progress")
    check(status == 200, f"админ не сменил статус ({status})")
    status, own = api.call(f"/rest/v1/tickets?id=eq.{ticket_id}&select=status", token=a)
    check(isinstance(own, list) and own and own[0]["status"] == "in_progress", "автор не видит новый статус")
    status, _ = api.admin(admin_token, action="ticket_status", id=ticket_id, status="hacked")
    check(status == 400, f"принят неизвестный статус ({status})")

    for fail in fails:
        print(f"FAIL {fail}")
    print("tickets e2e: " + ("ok" if not fails else f"нарушений {len(fails)}"))
    return 1 if fails else 0


if __name__ == "__main__":
    sys.exit(main())
