#!/usr/bin/env python3
"""CI: PR, который трогает расчёты, экраны, бота или базу, должен иметь отмеченный чек-лист качества.

Читает тело PR из PR_BODY и список изменённых файлов из аргументов.
"""
from __future__ import annotations

import os
import re
import sys

GUARDED = re.compile(r"^(composeApp/src/commonMain/|tools/rates/|supabase/)")
ITEM = re.compile(r"^\s*-\s*\[( |x|X)\]\s+(.*)$")
REQUIRED = 7


def check(body: str, files: list[str]) -> list[str]:
    if not any(GUARDED.match(path) for path in files):
        return []
    items = [m for m in (ITEM.match(line) for line in body.splitlines()) if m]
    if len(items) < REQUIRED:
        return [f"в описании PR нет чек-листа качества ({len(items)} пунктов из {REQUIRED}) — возьми его из .github/pull_request_template.md"]
    return [f"не отмечено: {m.group(2)}" for m in items if m.group(1) == " "]


def main() -> int:
    problems = check(os.environ.get("PR_BODY", ""), [line.strip() for line in sys.argv[1:] if line.strip()])
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
