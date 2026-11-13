#!/usr/bin/env python3
"""CI: PR, который трогает расчёты, экраны, бота или базу, должен иметь отмеченный чек-лист качества.

Пункты берутся из .github/pull_request_template.md и ищутся в теле PR дословно. Тело — из PR_BODY, изменённые файлы — построчно из stdin.
"""
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

GUARDED = re.compile(r"^(composeApp/src/commonMain/|tools/rates/|supabase/)")
ITEM = re.compile(r"^\s*-\s*\[( |x|X)\]\s+(.*?)\s*$")
TEMPLATE = Path(__file__).resolve().parents[2] / ".github" / "pull_request_template.md"


def required_items(template: str) -> list[str]:
    return [m.group(2) for m in (ITEM.match(line) for line in template.splitlines()) if m]


def check(body: str, files: list[str], template: str) -> list[str]:
    if not any(GUARDED.match(path) for path in files):
        return []
    marked = {m.group(2): m.group(1) != " " for m in (ITEM.match(line) for line in body.splitlines()) if m}
    problems = []
    for item in required_items(template):
        ticked = next((done for text, done in marked.items() if text.startswith(item)), None)
        if ticked is None:
            problems.append(f"нет пункта чек-листа: {item}")
        elif not ticked:
            problems.append(f"не отмечено: {item}")
    return problems


def main() -> int:
    files = [line.strip() for line in sys.stdin if line.strip()]
    problems = check(os.environ.get("PR_BODY") or "", files, TEMPLATE.read_text(encoding="utf-8"))
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
