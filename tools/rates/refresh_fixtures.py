"""Примеры для ответа на «Обновить»: python3 tools/rates/refresh_fixtures.py пересобирает файл для вебхука."""
import datetime as dt
import json
import sys
from pathlib import Path

HERE = Path(__file__).resolve().parent
sys.path.insert(0, str(HERE))
import bank_watch as bw  # noqa: E402

TARGET = HERE.parents[1] / "supabase" / "functions" / "telegram-webhook" / "refresh_fixtures.json"

WEEK = [298.0 + (i % 17) * 0.25 for i in range(64)]
SMALL = [0.0118 + (i % 9) * 0.00005 for i in range(64)]


def context(**overrides) -> dict:
    base = {
        "provider": "tbank", "from": "RUB", "to": "VND", "target": 300.0, "threshold": 0.85, "values": sorted(WEEK),
        "median_spread": -0.045, "warmup": False, "cbr": 312.0, "trade_open": 630, "trade_close": 1110,
        "timezone": "Europe/Moscow", "holidays": ["2026-11-04"],
    }
    base.update(overrides)
    return base


def utc(day: int, hour: int, minute: int = 0) -> dt.datetime:
    return dt.datetime(2026, 11, day, hour, minute, tzinfo=dt.timezone.utc)


CASES = [
    ("good above target", context(), 302.1, utc(16, 9, 42)),
    ("bad", context(target=None), 297.5, utc(16, 10, 5)),
    ("normal", context(), 299.6, utc(16, 12)),
    ("warmup", context(warmup=True), 299.6, utc(16, 12)),
    ("night", context(), 299.6, utc(16, 19)),
    ("weekend", context(), 299.6, utc(15, 12)),
    ("holiday", context(holidays=["2026-11-16"]), 299.6, utc(16, 12)),
    ("no cbr", context(cbr=None, median_spread=None), 302.1, utc(16, 9)),
    ("small rate", context(**{"to": "USD", "values": sorted(SMALL), "cbr": 0.0125, "target": 0.012, "median_spread": -0.06}), 0.01219, utc(16, 9, 30)),
    ("other zone", context(timezone="Asia/Bangkok"), 302.1, utc(16, 9, 42)),
    ("big unit", context(**{"from": "VND", "to": "RUB", "values": [0.00321, 0.00322, 0.00323], "cbr": 0.0032, "target": None, "median_spread": None, "warmup": True}), 0.003215, utc(16, 9)),
]


def build() -> list[dict]:
    return [
        {"name": name, "context": ctx, "rate": rate, "now": now.isoformat(), "text": bw.compose_now(ctx, rate, now)}
        for name, ctx, rate, now in CASES
    ]


if __name__ == "__main__":
    TARGET.write_text(json.dumps(build(), ensure_ascii=False, indent=1) + "\n")
    print(f"written {TARGET.relative_to(HERE.parents[1])}")
