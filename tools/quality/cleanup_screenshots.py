#!/usr/bin/env python3
from __future__ import annotations

import datetime as dt
import json
import os
import sys
import urllib.request

BUCKET = "tickets"
GRACE = dt.timedelta(minutes=int(os.environ.get("CLEANUP_GRACE_MINUTES", "60")))


class Storage:
    def __init__(self, url: str, key: str):
        self.url = url.rstrip("/")
        self.key = key

    def call(self, path: str, method: str = "GET", body: object = None):
        data = json.dumps(body).encode() if body is not None else None
        request = urllib.request.Request(self.url + path, data=data, method=method, headers={
            "apikey": self.key, "Authorization": f"Bearer {self.key}", "Content-Type": "application/json",
        })
        with urllib.request.urlopen(request, timeout=60) as response:
            raw = response.read()
            return json.loads(raw) if raw else None

    def list(self, prefix: str) -> list[dict]:
        return self.call(f"/storage/v1/object/list/{BUCKET}", "POST", {"prefix": prefix, "limit": 1000, "offset": 0}) or []

    def referenced(self) -> set[str]:
        rows = self.call("/rest/v1/tickets?select=screenshot_path&screenshot_path=not.is.null") or []
        return {row["screenshot_path"] for row in rows}

    def delete(self, paths: list[str]) -> None:
        if paths:
            self.call(f"/storage/v1/object/{BUCKET}", "DELETE", {"prefixes": paths})


def orphans(files: list[tuple[str, str]], referenced: set[str], now: dt.datetime) -> list[str]:
    result = []
    for path, created in files:
        created_at = dt.datetime.fromisoformat(created.replace("Z", "+00:00"))
        if path not in referenced and now - created_at > GRACE:
            result.append(path)
    return result


def main() -> int:
    storage = Storage(os.environ["SUPABASE_URL"], os.environ["SUPABASE_SERVICE_KEY"])
    files = []
    for folder in storage.list(""):
        if folder.get("id") is not None:
            continue
        for item in storage.list(folder["name"] + "/"):
            if item.get("id") is not None:
                files.append((f"{folder['name']}/{item['name']}", item["created_at"]))
    stale = orphans(files, storage.referenced(), dt.datetime.now(dt.timezone.utc))
    storage.delete(stale)
    print(f"скриншотов {len(files)}, удалено без обращения {len(stale)}")
    return 0


if __name__ == "__main__":
    sys.exit(main())
