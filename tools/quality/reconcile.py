#!/usr/bin/env python3
"""Сверка данных на проде перед релизом: только чтение, только локально.

Запуск: NOMAD_OWNER_EMAIL=<почта владельца> python3 tools/quality/reconcile.py
Токен берётся из Keychain, его сохраняет `supabase login`. В CI не запускать: вывод содержит суммы владельца.
Подробности (названия, id, суммы) печатаются только по владельцу. По другим пользователям — только число нарушений под обезличенным номером.

Две части:
1. Целостность данных — правила, которые должны выполняться всегда. Нарушение — код выхода 1, релиз не делаем.
2. Факты текущего месяца бюджета — плановые цифры, посчитанные SQL-ем независимо от приложения. Их сверяют с экранами «План» и «Кредиты».
"""
from __future__ import annotations

import base64
import hashlib
import json
import os
import re
import subprocess
import sys
import urllib.request
from pathlib import Path

LINKED_REF = Path(__file__).resolve().parents[2] / "supabase" / ".temp" / "project-ref"


def project_ref() -> str:
    ref = os.environ.get("SUPABASE_PROJECT_REF") or (LINKED_REF.read_text().strip() if LINKED_REF.exists() else "")
    if not ref:
        sys.exit("Нет project ref: выполни `supabase link` или задай SUPABASE_PROJECT_REF")
    return ref

CHECKS: list[tuple[str, str]] = [
    ("закрытый кредит с ненулевым остатком",
     "select user_id, name from debts where closed_at is not null and principal_remaining <> 0"),
    ("отрицательный остаток кредита",
     "select user_id, name from debts where principal_remaining < 0"),
    ("у кредита нет начальной записи в истории платежей",
     "select d.user_id, d.name from debts d where not exists (select 1 from debt_payment_changes c where c.debt_id = d.id)"),
    ("последний платёж в истории не совпадает с текущим платежом кредита",
     """select d.user_id, d.name from debts d join lateral (
            select monthly_payment from debt_payment_changes c where c.debt_id = d.id and c.effective_from <= current_date
            order by effective_from desc, created_at desc limit 1) last on true
        where last.monthly_payment <> d.monthly_payment
          and not exists (select 1 from debt_payment_changes f where f.debt_id = d.id and f.effective_from > current_date)"""),
    ("операция посчитана не в текущей базе",
     """select t.user_id, t.id from transactions t join user_settings s on s.user_id = t.user_id
        where t.base_currency is not null and t.base_currency <> s.base_currency"""),
    ("сумма в базе не равна сумме операции, хотя счёт в базовой валюте",
     """select t.user_id, t.id from transactions t join accounts a on a.id = t.account_id join user_settings s on s.user_id = t.user_id
        where a.currency = s.base_currency and t.type <> 'exchange' and t.amount_base <> t.amount"""),
    ("досрочка по кредиту: тело не равно сумме платежа",
     """select t.user_id, t.id from transactions t join debts d on d.id = t.debt_id join accounts a on a.id = t.account_id
        where t.debt_early and a.currency = d.currency and t.debt_principal is not null and t.debt_principal <> t.amount"""),
    ("тело платежа больше суммы платежа",
     """select t.user_id, t.id from transactions t join debts d on d.id = t.debt_id join accounts a on a.id = t.account_id
        where a.currency = d.currency and t.debt_principal > t.amount"""),
    ("платёж по кредиту раньше даты открытия",
     "select t.user_id, t.id from transactions t join debts d on d.id = t.debt_id where d.opened_on is not null and t.tx_date < d.opened_on"),
    ("платёж по кредиту позже даты закрытия",
     "select t.user_id, t.id from transactions t join debts d on d.id = t.debt_id where d.closed_at is not null and t.tx_date > d.closed_at::date"),
    ("план досрочки у закрытого кредита на месяц после закрытия",
     "select d.user_id, d.name from debt_month_plans p join debts d on d.id = p.debt_id where d.closed_at is not null and p.period_start > d.closed_at::date"),
    ("базовая валюта в списке дополнительных валют",
     "select user_id, base_currency from user_settings where base_currency = any (currencies)"),
    ("операция в будущем",
     "select user_id, id from transactions where tx_date > current_date + 1"),
    ("перевод или обмен на тот же счёт",
     "select user_id, id from transactions where counter_account_id = account_id"),
    ("нет курса к рублю для валюты счёта на дату операции",
     """select t.user_id, t.id from transactions t join accounts a on a.id = t.account_id
        where a.currency <> 'RUB' and not exists (
            select 1 from exchange_rates r where r.base = 'RUB' and r.quote = a.currency and r.rate_date <= t.tx_date)"""),
]

FACTS_SQL = """
with s as (select * from user_settings where user_id = '{owner}'),
p as (
    select start_date, end_date, savings_target, id from periods
    where user_id = (select user_id from s) and start_date <= current_date and end_date > current_date
    order by start_date desc limit 1
),
life as (select a.* from accounts a where a.user_id = (select user_id from s) and not a.is_savings and not a.is_fund and (a.archived_at is null or a.archived_at::date >= (select start_date from p))),
carry as (
    select coalesce(sum(a.opening_balance
        + coalesce((select sum(case when t.type = 'income' then t.amount when t.type in ('expense', 'transfer', 'exchange') then -t.amount end)
                    from transactions t where t.account_id = a.id and t.tx_date < (select start_date from p)), 0)
        + coalesce((select sum(coalesce(t.counter_amount, t.amount)) from transactions t
                    where t.counter_account_id = a.id and t.tx_date < (select start_date from p)), 0)), 0) as v
    from life a where a.currency = (select base_currency from s)
),
plan as (
    select coalesce(sum(b.planned_base) filter (where c.kind = 'income'), 0) as income,
           coalesce(sum(b.planned_base) filter (where c.kind = 'expense'), 0) as envelopes
    from budget_lines b join categories c on c.id = b.category_id where b.period_id = (select id from p)
),
funds as (select coalesce(sum(fund_monthly), 0) as v from accounts where user_id = (select user_id from s) and is_fund and archived_at is null and currency = (select base_currency from s))
select json_build_object(
    'period', (select start_date from p), 'base', (select base_currency from s),
    'carry_over_base_accounts', (select v from carry), 'income_plan', (select income from plan),
    'envelopes_plan', (select envelopes from plan), 'savings_target', (select savings_target from p),
    'funds_monthly_base', (select v from funds),
    'foreign_life_accounts', (select count(*) from life where currency <> (select base_currency from s)),
    'minor_units', (select minor_units from currencies where code = (select base_currency from s))
)
"""

DEBTS_SQL = """
with s as (select * from user_settings where user_id = '{owner}'),
p as (select start_date, end_date from periods where user_id = (select user_id from s) and start_date <= current_date and end_date > current_date order by start_date desc limit 1)
select d.name, d.currency, d.closed_at::date as closed, d.monthly_payment,
    coalesce(sum(t.amount) filter (where not t.debt_early), 0) as regular_paid,
    coalesce(sum(t.amount) filter (where t.debt_early and a.is_savings), 0) as early_from_cushion,
    coalesce(sum(t.amount) filter (where t.debt_early and not a.is_savings), 0) as early_from_life,
    (select early_amount from debt_month_plans m where m.debt_id = d.id and m.period_start = (select start_date from p)) as early_plan,
    (select from_cushion from debt_month_plans m where m.debt_id = d.id and m.period_start = (select start_date from p)) as plan_from_cushion
from debts d
left join transactions t on t.debt_id = d.id and t.tx_date >= (select start_date from p) and t.tx_date < (select end_date from p)
left join accounts a on a.id = t.account_id and a.currency = d.currency
where d.user_id = (select user_id from s) and (d.closed_at is null or d.closed_at::date >= (select start_date from p))
group by d.id order by d.name
"""


def token() -> str:
    raw = subprocess.run(["security", "find-generic-password", "-s", "Supabase CLI", "-w"], capture_output=True, text=True, check=True).stdout.strip()
    raw = raw.removeprefix("go-keyring-base64:")
    if len(raw) > 60:
        try:
            raw = base64.b64decode(raw).decode()
        except ValueError:
            pass
    return raw


def query(sql: str, key: str) -> list[dict]:
    body = json.dumps({"query": sql, "read_only": True}).encode()
    api = f"https://api.supabase.com/v1/projects/{project_ref()}/database/query"
    request = urllib.request.Request(api, data=body, method="POST", headers={"Authorization": f"Bearer {key}", "Content-Type": "application/json"})
    with urllib.request.urlopen(request, timeout=60) as response:
        return json.loads(response.read())


MINOR_UNITS = 2


def rub(minor) -> str:
    return "—" if minor is None else f"{int(minor) / 10 ** MINOR_UNITS:,.{MINOR_UNITS}f}".replace(",", " ")


UUID = re.compile(r"^[0-9a-f]{8}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{4}-[0-9a-f]{12}$")


def alias(user_id: str) -> str:
    return "пользователь " + hashlib.sha256(user_id.encode()).hexdigest()[:6]


def describe(rows: list[dict], owner: str) -> str:
    mine = [str(r["detail"]) for r in rows if r["user_id"] == owner]
    others: dict[str, int] = {}
    for r in rows:
        if r["user_id"] != owner:
            others[alias(r["user_id"])] = others.get(alias(r["user_id"]), 0) + 1
    parts = []
    if mine:
        parts.append(f"владелец {len(mine)} — {', '.join(mine[:5])}")
    parts += [f"{name}: {count}" for name, count in sorted(others.items())]
    return "; ".join(parts)


def owner_id(key: str) -> str:
    email = (os.environ.get("NOMAD_OWNER_EMAIL") or "").strip().lower()
    if not email:
        sys.exit("Задай NOMAD_OWNER_EMAIL — почту владельца: подробности печатаются только по нему")
    rows = query("select id from auth.users where lower(email) = '" + email.replace("'", "''") + "'", key)
    if len(rows) != 1 or not UUID.match(str(rows[0]["id"])):
        sys.exit("Владелец с такой почтой не найден")
    return str(rows[0]["id"])


def main() -> int:
    key = token()
    owner = owner_id(key)
    failed = 0
    print("Целостность данных (все пользователи)")
    for title, sql in CHECKS:
        rows = [{"user_id": str(r["user_id"]), "detail": r[list(r)[1]]} for r in query(sql, key)]
        mark = "ok  " if not rows else "FAIL"
        failed += bool(rows)
        print(f"  {mark} {title}" + (f": {describe(rows, owner)}" if rows else ""))
    global MINOR_UNITS
    facts = query(FACTS_SQL.format(owner=owner), key)[0]["json_build_object"]
    MINOR_UNITS = int(facts.get("minor_units") or 2)
    print(f"\nФакты месяца владельца с {facts['period']}, база {facts['base']}")
    print(f"  доход по плану          {rub(facts['income_plan'])}")
    print(f"  остаток с прошлого (счета в базе) {rub(facts['carry_over_base_accounts'])}" + (f"  + валютных счетов: {facts['foreign_life_accounts']}" if facts["foreign_life_accounts"] else ""))
    print(f"  конверты по плану       {rub(facts['envelopes_plan'])}")
    print(f"  «Себе»                  {rub(facts['savings_target'])}")
    print(f"  копилки в месяц (база)  {rub(facts['funds_monthly_base'])}")
    print("\nКредиты в этом месяце (суммы в валюте кредита, платежи с других валют не учтены): платёж · внесено обычным · досрочка с подушки · досрочка с жизни · план досрочки")
    for row in query(DEBTS_SQL.format(owner=owner), key):
        closed = f" (закрыт {row['closed']})" if row["closed"] else ""
        plan = f"{rub(row['early_plan'])}{' с подушки' if row['plan_from_cushion'] else ''}" if row["early_plan"] is not None else "—"
        print(f"  {row['name']}{closed}: {rub(row['monthly_payment'])} · {rub(row['regular_paid'])} · {rub(row['early_from_cushion'])} · {rub(row['early_from_life'])} · {plan}")
    print("\nСверь с экраном «План»: «Не распределено» = доход + остаток − кредиты (обычные платежи и досрочка с жизни) − копилки − конверты − «Себе».")
    print("Закрытый в этом месяце кредит входит суммой, внесённой с жизни; досрочка с подушки в план не входит.")
    if failed:
        print(f"\n{failed} нарушений — релиз не делаем, сначала разобраться.")
    return 1 if failed else 0


if __name__ == "__main__":
    sys.exit(main())
