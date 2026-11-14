#!/usr/bin/env python3
from __future__ import annotations

import os
import re
import sys
from pathlib import Path

MIGRATION = re.compile(r"^supabase/migrations/(\d{4})_[a-z0-9_]+\.sql$")
LABEL = "destructive-ok"
DESTRUCTIVE = [
    (re.compile(r"\bdrop\s+table\b", re.I), "drop table"),
    (re.compile(r"\bdrop\s+column\b", re.I), "drop column"),
    (re.compile(r"\bdrop\s+schema\b", re.I), "drop schema"),
    (re.compile(r"^\s*truncate\b", re.I), "truncate"),
    (re.compile(r"\balter\s+column\s+\S+\s+(set\s+data\s+)?type\b", re.I), "смена типа колонки"),
    (re.compile(r"\bdisable\s+row\s+level\s+security\b", re.I), "выключение RLS"),
]
WRITE = re.compile(r"\b(delete\s+from|update\s+(only\s+)?[\w.\"]+(\s+\w+)?\s+set)\b", re.I)
WHERE = re.compile(r"\bwhere\b(?!\s+true\b)", re.I)
LINE_COMMENT = re.compile(r"--[^\n]*")
BLOCK_COMMENT = re.compile(r"/\*.*?\*/", re.S)
PARENS = re.compile(r"\([^()]*\)")


def statements(sql: str) -> list[str]:
    clean = LINE_COMMENT.sub("", BLOCK_COMMENT.sub("", sql))
    return [part.strip() for part in clean.split(";") if part.strip()]


def top_level(stmt: str) -> str:
    previous = None
    while previous != stmt:
        previous, stmt = stmt, PARENS.sub(" ", stmt)
    return stmt


def destructive(sql: str) -> list[str]:
    found = []
    for stmt in statements(sql):
        for pattern, name in DESTRUCTIVE:
            if pattern.search(stmt):
                found.append(name)
        if WRITE.search(stmt) and not WHERE.search(top_level(stmt)):
            found.append("delete или update без where")
    return sorted(set(found))


def check(changes: list[tuple[str, str]], existing: list[str], read, labels: set[str]) -> list[str]:
    problems = []
    numbers = sorted(int(m.group(1)) for m in (MIGRATION.match(p) for p in existing) if m)
    last = numbers[-1] if numbers else 0
    added = []
    for status, path in changes:
        if not path.startswith("supabase/migrations/"):
            continue
        if status != "A":
            problems.append(f"{path}: выложенную миграцию нельзя менять или удалять — добавь новую")
            continue
        match = MIGRATION.match(path)
        if not match:
            problems.append(f"{path}: имя миграции должно быть NNNN_snake_case.sql")
            continue
        added.append((int(match.group(1)), path))
    for expected, (number, path) in enumerate(sorted(added), start=last + 1):
        if number != expected:
            problems.append(f"{path}: номер {number:04d}, ожидался {expected:04d}")
    for _, path in sorted(added):
        found = destructive(read(path))
        if found and LABEL not in labels:
            problems.append(f"{path}: разрушительные операции ({', '.join(found)}) — нужен свежий дамп и метка {LABEL}")
    return problems


def main() -> int:
    root = Path(__file__).resolve().parents[2]
    changes = []
    for line in sys.stdin:
        parts = line.rstrip("\n").split("\t")
        if len(parts) >= 2:
            changes.append((parts[0][0], parts[-1]))
    added = {path for status, path in changes if status == "A"}
    existing = [str(p.relative_to(root)) for p in (root / "supabase" / "migrations").glob("*.sql")]
    base = [p for p in existing if p not in added]
    labels = {label.strip() for label in (os.environ.get("PR_LABELS") or "").split(",") if label.strip()}
    problems = check(changes, base, lambda p: (root / p).read_text(encoding="utf-8"), labels)
    for problem in problems:
        print(problem)
    return 1 if problems else 0


if __name__ == "__main__":
    sys.exit(main())
