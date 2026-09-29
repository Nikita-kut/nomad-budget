#!/usr/bin/env python3
"""Импорт истории из старой Excel-таблицы в Supabase: помесячные итоги по категориям.

Каждый лист workbook — один зарплатный месяц. Из листа берутся:
  * блок доходов (столбцы A–C до строки «Итого»): категория, план, факт;
  * блок расходов (под заголовком «Расходы, месяц, факт», столбцы B–E до «Итог»): категория, факт, план.

В БД создаются: архивные псевдосчета «Архив» и «Архив накопления», архивные категории для
старых названий, периоды, budget_lines (план) и операции с source='legacy', датированные
началом зарплатного периода.

Запуск:
  python3 tools/import/import_legacy.py <workbook.xlsx> --dry-run   # только отчёт
  SUPABASE_URL=... SUPABASE_SERVICE_KEY=... python3 tools/import/import_legacy.py <workbook.xlsx>

Повторный запуск безопасен: старые legacy-операции удаляются перед импортом.
Сам workbook в репозиторий не кладётся.
"""
from __future__ import annotations

import datetime as dt
import json
import os
import re
import sys
import urllib.parse
import urllib.request
from dataclasses import dataclass, field

try:
    import openpyxl
except ImportError:
    raise SystemExit("нужен openpyxl: python3 -m pip install --user openpyxl")

PAY_DAY = 5
SOURCE = "legacy"
ARCHIVE_ACCOUNT = "Архив"
ARCHIVE_SAVINGS = "Архив накопления"
ARCHIVE_ORDER = 1000

SHEET_RE = re.compile(r"^\s*(?:(\d{4})\s+)?(\d{2})\.(\d{2})-(\d{2})\.(\d{2})\s*$")

SKIP_INCOME = {"с прошлого месяца", "с долгом другим", "без долга другим"}
TO_SAVINGS_EXPENSE = {"себе"}

FROM_SAVINGS_KEYWORDS = ("у себя", "себя взял", "копилк", "кубышк", "брок", "инве", "со сбера", "с счёта", "с кредитки", "долями")

INCOME_KEYWORD_RULES = (
    (("зп", "зарплат", "оклад", "премия", "увольнен", "командировоч", "отпускн", "бонус"), "Зарплата"),
    (("продал", "продаж", "авито", "стол", "книг", "монитор", "одежд"), "Продажи"),
    (("кэшбек", "кешбек"), "Кешбек"),
    (("возврат", "долг", "отдали", "залог"), "Возврат"),
    (("подарок", "праздничн"), "Подарки"),
    (("бабушка", "мама", "даша", "юры", "макса", "двиги", "родствен", "алин"), "Родные"),
    (("стипенд", "пенси"), "Стипендия"),
    (("подработ", "% с продаж", "бизнес", "сквош"), "Подработка"),
    (("ставк",), "Ставки"),
    (("кредит",), "Кредит"),
)

EXPENSE_MAP = {
    "еда": "Еда",
    "транспорт": "Транспорт",
    "разное": "Разное",
    "квартплата": "Жильё",
    "квартплата и аренда жилья": "Жильё",
    "жильё": "Жильё",
    "долг": "Долг",
    "развлечения": "Развлечения",
    "связь": "Связь",
    "связь и интернет": "Связь",
}
INCOME_MAP = {
    "зп": "Зарплата",
    "зарплата": "Зарплата",
    "оклад": "Зарплата",
    "возврат": "Возврат",
    "кешбек": "Кешбек",
}


@dataclass
class Line:
    category: str
    kind: str
    plan: int
    fact: int


@dataclass
class MonthSheet:
    name: str
    start: dt.date
    end: dt.date
    period_start: dt.date
    period_end: dt.date
    lines: list[Line] = field(default_factory=list)
    to_savings: int = 0
    from_savings: int = 0

    @property
    def title(self) -> str:
        return f"{self.period_start:%d.%m} → {self.period_end:%d.%m}"


def normalize_income(label: str) -> str | None:
    """Имя категории дохода или None, если это снятие с накоплений."""
    key = label.lower()
    if key in INCOME_MAP:
        return INCOME_MAP[key]
    if any(word in key for word in FROM_SAVINGS_KEYWORDS):
        return None
    for keywords, target in INCOME_KEYWORD_RULES:
        if any(word in key for word in keywords):
            return target
    return label.strip()[:1].upper() + label.strip()[1:]


def kopecks(value) -> int:
    if value is None or value == "":
        return 0
    if isinstance(value, str):
        value = value.replace(" ", "").replace(" ", "").replace(",", ".").replace("₽", "")
        try:
            value = float(value)
        except ValueError:
            return 0
    return int(round(float(value) * 100))


def add_months(date: dt.date, months: int) -> dt.date:
    month_index = date.month - 1 + months
    year = date.year + month_index // 12
    month = month_index % 12 + 1
    return dt.date(year, month, 1)


def cycle_period(any_day: dt.date) -> tuple[dt.date, dt.date]:
    first = dt.date(any_day.year, any_day.month, 1)
    start_month = first if any_day.day >= PAY_DAY else add_months(first, -1)
    start = dt.date(start_month.year, start_month.month, PAY_DAY)
    next_month = add_months(start_month, 1)
    return start, dt.date(next_month.year, next_month.month, PAY_DAY)


def parse_sheet_names(names: list[str]) -> list[tuple[str, dt.date, dt.date]]:
    """Возвращает (имя, начало, конец). Год для листов без года выводится из порядка листов."""
    parsed: list[tuple[str, int | None, int, int, int, int]] = []
    for name in names:
        m = SHEET_RE.match(name)
        if not m:
            continue
        year, d1, m1, d2, m2 = m.groups()
        parsed.append((name, int(year) if year else None, int(d1), int(m1), int(d2), int(m2)))

    result = []
    year_cursor: int | None = None
    prev_month: int | None = None
    for name, year, d1, m1, d2, m2 in parsed:
        if year is not None:
            year_cursor, prev_month = year, m1
        else:
            if year_cursor is None:
                raise SystemExit(f"лист {name!r} без года идёт раньше листов с годом, не могу вывести год")
            if prev_month is not None and m1 > prev_month:
                year_cursor -= 1
            year, prev_month = year_cursor, m1
        start = dt.date(year, m1, d1)
        end_year = year + 1 if m2 < m1 else year
        end = dt.date(end_year, m2, d2)
        result.append((name, start, end))
    return result


def read_sheet(ws, name: str, start: dt.date, end: dt.date, period_start: dt.date, period_end: dt.date) -> MonthSheet:
    sheet = MonthSheet(name=name, start=start, end=end, period_start=period_start, period_end=period_end)
    rows = list(ws.iter_rows(min_row=1, max_row=40, max_col=6, values_only=True))

    def cell(row, col):
        return row[col] if col < len(row) else None

    def is_expense_header(row) -> bool:
        return str(cell(row, 1) or "").strip().lower().startswith("расходы, месяц")

    header_idx = next((i for i, r in enumerate(rows) if str(cell(r, 0) or "").strip().lower() == "категория"), None)
    if header_idx is not None:
        for row in rows[header_idx + 1:]:
            if is_expense_header(row):
                break
            label = str(cell(row, 0) or "").strip()
            if not label or label.lower() in ("категория", "итого"):
                continue
            if label.lower() == "итого":
                break
            plan, fact = kopecks(cell(row, 1)), kopecks(cell(row, 2))
            if label.lower() in SKIP_INCOME:
                continue
            target = normalize_income(label)
            if target is None:
                sheet.from_savings += fact
                continue
            sheet.lines.append(Line(target, "income", plan, fact))

    exp_idx = next((i for i, r in enumerate(rows) if is_expense_header(r)), None)
    if exp_idx is not None:
        for row in rows[exp_idx + 2:]:
            label = str(cell(row, 1) or "").strip()
            if not label:
                continue
            if label.lower() == "итог":
                break
            fact, plan = kopecks(cell(row, 2)), kopecks(cell(row, 4))
            key = label.lower()
            if key in TO_SAVINGS_EXPENSE:
                sheet.to_savings += fact
                continue
            sheet.lines.append(Line(EXPENSE_MAP.get(key, label), "expense", plan, fact))
    return sheet


def assign_periods(ranges: list[tuple[str, dt.date, dt.date]]) -> list[tuple[str, dt.date, dt.date, dt.date, dt.date]]:
    """Листы идут подряд по месяцам, поэтому каждому следующему достаётся следующий зарплатный период.
    Если между листами разрыв больше периода, привязка сбрасывается на период, содержащий начало листа."""
    ordered = sorted(ranges, key=lambda r: r[1])
    result = []
    previous_end: dt.date | None = None
    for name, start, end in ordered:
        if previous_end is None or start >= previous_end + dt.timedelta(days=20):
            period_start, period_end = cycle_period(start)
        else:
            period_start = previous_end
            period_end = cycle_period(period_start)[1]
        result.append((name, start, end, period_start, period_end))
        previous_end = period_end
    starts = [r[3] for r in result]
    duplicates = sorted({s for s in starts if starts.count(s) > 1})
    if duplicates:
        raise SystemExit(f"два листа попали в один период: {', '.join(d.isoformat() for d in duplicates)}")
    return result


def load_workbook(path: str) -> list[MonthSheet]:
    wb = openpyxl.load_workbook(path, read_only=True, data_only=True)
    sheets = []
    for name, start, end, period_start, period_end in assign_periods(parse_sheet_names(wb.sheetnames)):
        sheets.append(read_sheet(wb[name], name, start, end, period_start, period_end))
    return sheets


class Supabase:
    def __init__(self) -> None:
        self.url = os.environ["SUPABASE_URL"].rstrip("/") + "/rest/v1/"
        self.key = os.environ["SUPABASE_SERVICE_KEY"]

    def request(self, path: str, method: str = "GET", body=None, prefer: str | None = None):
        headers = {"apikey": self.key, "Authorization": f"Bearer {self.key}", "Content-Type": "application/json"}
        if prefer:
            headers["Prefer"] = prefer
        data = json.dumps(body).encode() if body is not None else None
        req = urllib.request.Request(self.url + path, data=data, method=method, headers=headers)
        try:
            with urllib.request.urlopen(req, timeout=60) as resp:
                raw = resp.read()
                return json.loads(raw) if raw else None
        except urllib.error.HTTPError as e:
            raise SystemExit(f"{method} {path}: {e.code} {e.read().decode()[:300]}")

    def select(self, table: str, query: str = ""):
        return self.request(f"{table}?{query}")

    def insert(self, table: str, rows, on_conflict: str | None = None):
        path = table + (f"?on_conflict={urllib.parse.quote(on_conflict)}" if on_conflict else "")
        prefer = "return=representation" + (",resolution=merge-duplicates" if on_conflict else "")
        return self.request(path, "POST", rows, prefer)

    def delete(self, table: str, query: str):
        return self.request(f"{table}?{query}", "DELETE", prefer="return=minimal")


def import_sheets(sheets: list[MonthSheet]) -> None:
    db = Supabase()
    users = db.select("accounts", "select=user_id&limit=1")
    if not users:
        raise SystemExit("в БД нет ни одного счёта, сначала зарегистрируйся в приложении")
    user_id = users[0]["user_id"]
    now = dt.datetime.now(dt.timezone.utc).isoformat()

    def ensure_account(name: str, savings: bool) -> str:
        found = db.select("accounts", f"select=id&user_id=eq.{user_id}&name=eq.{urllib.parse.quote(name)}")
        if found:
            return found[0]["id"]
        row = {"user_id": user_id, "name": name, "currency": "RUB", "kind": "savings" if savings else "account",
               "is_savings": savings, "sort_order": ARCHIVE_ORDER, "archived_at": now}
        return db.insert("accounts", [row])[0]["id"]

    archive_id = ensure_account(ARCHIVE_ACCOUNT, savings=False)
    savings_id = ensure_account(ARCHIVE_SAVINGS, savings=True)

    categories = {(c["kind"], c["name"].lower()): c["id"] for c in db.select("categories", f"select=id,name,kind&user_id=eq.{user_id}")}

    def ensure_category(name: str, kind: str) -> str:
        key = (kind, name.lower())
        if key not in categories:
            row = {"user_id": user_id, "name": name, "kind": kind, "sort_order": ARCHIVE_ORDER, "archived_at": now}
            categories[key] = db.insert("categories", [row])[0]["id"]
        return categories[key]

    db.delete("transactions", f"user_id=eq.{user_id}&source=eq.{SOURCE}")

    period_rows = [{"user_id": user_id, "start_date": s.period_start.isoformat(), "end_date": s.period_end.isoformat(), "title": s.title} for s in sheets]
    db.insert("periods", period_rows, on_conflict="user_id,start_date")
    period_ids = {p["start_date"]: p["id"] for p in db.select("periods", f"select=id,start_date&user_id=eq.{user_id}")}

    budget_rows, tx_rows = [], []
    for s in sheets:
        period_id = period_ids[s.period_start.isoformat()]
        date = s.period_start.isoformat()
        note = f"импорт: {s.name.strip()}"
        for line in s.lines:
            category_id = ensure_category(line.category, line.kind)
            if line.plan > 0:
                budget_rows.append({"user_id": user_id, "period_id": period_id, "category_id": category_id, "planned_base": line.plan})
            if line.fact > 0:
                tx_rows.append({"user_id": user_id, "tx_date": date, "type": line.kind, "account_id": archive_id, "amount": line.fact,
                                "category_id": category_id, "rate_to_base": 1, "amount_base": line.fact, "rate_source": "manual",
                                "source": SOURCE, "note": note})
        if s.to_savings > 0:
            tx_rows.append({"user_id": user_id, "tx_date": date, "type": "transfer", "account_id": archive_id, "counter_account_id": savings_id,
                            "amount": s.to_savings, "rate_to_base": 1, "amount_base": s.to_savings, "rate_source": "manual", "source": SOURCE, "note": note})
        if s.from_savings > 0:
            tx_rows.append({"user_id": user_id, "tx_date": date, "type": "transfer", "account_id": savings_id, "counter_account_id": archive_id,
                            "amount": s.from_savings, "rate_to_base": 1, "amount_base": s.from_savings, "rate_source": "manual", "source": SOURCE, "note": note})

    for chunk in range(0, len(budget_rows), 200):
        db.insert("budget_lines", budget_rows[chunk:chunk + 200], on_conflict="period_id,category_id")
    for chunk in range(0, len(tx_rows), 200):
        db.insert("transactions", tx_rows[chunk:chunk + 200])
    print(f"импортировано: {len(sheets)} месяцев, {len(tx_rows)} операций, {len(budget_rows)} строк плана")


def report(sheets: list[MonthSheet]) -> None:
    known = set(EXPENSE_MAP.values()) | set(INCOME_MAP.values())
    unknown: dict[tuple[str, str], int] = {}
    total_tx = 0
    print(f"{'лист':22} {'период':15} {'доход':>12} {'расход':>12} {'себе':>10} {'у себя':>10}")
    for s in sheets:
        income = sum(l.fact for l in s.lines if l.kind == "income")
        expense = sum(l.fact for l in s.lines if l.kind == "expense")
        total_tx += sum(1 for l in s.lines if l.fact > 0) + (s.to_savings > 0) + (s.from_savings > 0)
        print(f"{s.name.strip():22} {s.title:15} {income / 100:12,.0f} {expense / 100:12,.0f} {s.to_savings / 100:10,.0f} {s.from_savings / 100:10,.0f}")
        for l in s.lines:
            if l.category not in known:
                unknown[(l.kind, l.category)] = unknown.get((l.kind, l.category), 0) + 1
    print(f"\nмесяцев: {len(sheets)}, операций: {total_tx}")
    if unknown:
        print("категории вне карты (будут созданы архивными):")
        for (kind, name), n in sorted(unknown.items(), key=lambda x: (-x[1], x[0])):
            print(f"  {kind:8} {name!r:40} в {n} мес.")


def main() -> None:
    args = [a for a in sys.argv[1:] if not a.startswith("--")]
    if not args:
        raise SystemExit(__doc__)
    sheets = load_workbook(args[0])
    report(sheets)
    if "--dry-run" in sys.argv:
        return
    import_sheets(sheets)


if __name__ == "__main__":
    main()
