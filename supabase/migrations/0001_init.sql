-- Nomad Budget: базовая схема.
-- Деньги хранятся в минимальных единицах валюты (bigint): копейки, центы, донги.
-- Базовая валюта отчётов — RUB. amount_base всегда в копейках.

create extension if not exists pgcrypto;

-- ---------- справочники ----------

create table public.currencies (
    code        text primary key,
    minor_units smallint not null check (minor_units between 0 and 4),
    symbol      text not null
);

insert into public.currencies (code, minor_units, symbol) values
    ('RUB', 2, '₽'),
    ('USD', 2, '$'),
    ('VND', 0, '₫');

create type public.account_kind  as enum ('card', 'account', 'cash', 'savings', 'investment');
create type public.category_kind as enum ('expense', 'income');
create type public.tx_type       as enum ('expense', 'income', 'transfer', 'exchange');
create type public.rate_source   as enum ('api', 'manual');

-- ---------- служебное ----------

create or replace function public.set_updated_at()
returns trigger language plpgsql as $$
begin
    new.updated_at = now();
    return new;
end $$;

-- ---------- курсы (общие для всех пользователей) ----------

create table public.exchange_rates (
    rate_date  date not null,
    base       text not null references public.currencies (code),
    quote      text not null references public.currencies (code),
    rate       numeric(20, 10) not null check (rate > 0),
    source     text not null default 'er-api',
    fetched_at timestamptz not null default now(),
    primary key (rate_date, base, quote, source)
);

comment on table public.exchange_rates is
    'rate = сколько base за 1 quote. base=RUB, quote=USD, rate=84.1975 → 1 USD = 84.1975 RUB';

-- ---------- пользовательские таблицы ----------

create table public.accounts (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
    name            text not null,
    currency        text not null references public.currencies (code),
    kind            public.account_kind not null,
    is_savings      boolean not null default false,
    opening_balance bigint not null default 0,
    sort_order      int not null default 0,
    archived_at     timestamptz,
    created_at      timestamptz not null default now()
);

create table public.categories (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    name        text not null,
    kind        public.category_kind not null,
    sort_order  int not null default 0,
    archived_at timestamptz,
    created_at  timestamptz not null default now(),
    unique (user_id, kind, name)
);

create table public.subcategories (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    category_id uuid not null references public.categories (id) on delete cascade,
    name        text not null,
    created_at  timestamptz not null default now(),
    unique (category_id, name)
);

create table public.periods (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null default auth.uid() references auth.users (id) on delete cascade,
    start_date date not null,
    end_date   date not null,
    title      text not null,
    created_at timestamptz not null default now(),
    unique (user_id, start_date),
    check (end_date > start_date)
);

create table public.debts (
    id                  uuid primary key default gen_random_uuid(),
    user_id             uuid not null default auth.uid() references auth.users (id) on delete cascade,
    name                text not null,
    currency            text not null references public.currencies (code),
    principal_remaining bigint not null default 0,
    monthly_payment     bigint not null default 0,
    rate_percent        numeric(6, 3),
    pay_day             smallint check (pay_day between 1 and 31),
    closed_at           timestamptz,
    created_at          timestamptz not null default now()
);

create table public.transactions (
    id                 uuid primary key default gen_random_uuid(),
    user_id            uuid not null default auth.uid() references auth.users (id) on delete cascade,
    tx_date            date not null,
    type               public.tx_type not null,
    account_id         uuid not null references public.accounts (id),
    amount             bigint not null check (amount > 0),
    counter_account_id uuid references public.accounts (id),
    counter_amount     bigint check (counter_amount is null or counter_amount > 0),
    category_id        uuid references public.categories (id),
    subcategory_id     uuid references public.subcategories (id),
    debt_id            uuid references public.debts (id),
    note               text,
    rate_to_base       numeric(20, 10) not null check (rate_to_base > 0),
    amount_base        bigint not null,
    rate_source        public.rate_source not null default 'api',
    source             text not null default 'app' check (source in ('app', 'legacy')),
    created_at         timestamptz not null default now(),
    updated_at         timestamptz not null default now(),

    constraint tx_counter_account_by_type check (
        (type in ('transfer', 'exchange')) = (counter_account_id is not null)
    ),
    constraint tx_counter_amount_for_exchange check (
        (type = 'exchange') = (counter_amount is not null)
    ),
    constraint tx_category_by_type check (
        (type in ('expense', 'income')) = (category_id is not null)
    ),
    constraint tx_not_same_account check (
        counter_account_id is null or counter_account_id <> account_id
    )
);

create index transactions_user_date_idx on public.transactions (user_id, tx_date desc);
create index transactions_user_category_idx on public.transactions (user_id, category_id);
create index transactions_user_account_idx on public.transactions (user_id, account_id);

create trigger transactions_set_updated_at
    before update on public.transactions
    for each row execute function public.set_updated_at();

create table public.budget_lines (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
    period_id    uuid not null references public.periods (id) on delete cascade,
    category_id  uuid not null references public.categories (id) on delete cascade,
    planned_base bigint not null default 0,
    unique (period_id, category_id)
);

create table public.balance_checks (
    id               uuid primary key default gen_random_uuid(),
    user_id          uuid not null default auth.uid() references auth.users (id) on delete cascade,
    account_id       uuid not null references public.accounts (id) on delete cascade,
    check_date       date not null,
    actual_balance   bigint not null,
    computed_balance bigint not null,
    note             text,
    created_at       timestamptz not null default now()
);

-- ---------- Row Level Security ----------

alter table public.currencies     enable row level security;
alter table public.exchange_rates enable row level security;
alter table public.accounts       enable row level security;
alter table public.categories     enable row level security;
alter table public.subcategories  enable row level security;
alter table public.periods        enable row level security;
alter table public.debts          enable row level security;
alter table public.transactions   enable row level security;
alter table public.budget_lines   enable row level security;
alter table public.balance_checks enable row level security;

-- Справочники: читать могут все залогиненные, писать только service role (он обходит RLS).
create policy "currencies: read" on public.currencies
    for select to authenticated using (true);

create policy "exchange_rates: read" on public.exchange_rates
    for select to authenticated using (true);

-- Пользовательские таблицы: одна политика на всё — строка видна и изменяема только владельцу.
create policy "accounts: owner" on public.accounts
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "categories: owner" on public.categories
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "subcategories: owner" on public.subcategories
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "periods: owner" on public.periods
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "debts: owner" on public.debts
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "transactions: owner" on public.transactions
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "budget_lines: owner" on public.budget_lines
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "balance_checks: owner" on public.balance_checks
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- ---------- вьюхи для агрегатов ----------
-- security_invoker: вьюха выполняется с правами читающего, RLS базовых таблиц применяется.

create view public.account_balances
with (security_invoker = true) as
select
    a.id as account_id,
    a.user_id,
    a.currency,
    a.opening_balance
        + coalesce((select sum(t.amount) from public.transactions t
                    where t.account_id = a.id and t.type = 'income'), 0)
        - coalesce((select sum(t.amount) from public.transactions t
                    where t.account_id = a.id and t.type in ('expense', 'transfer', 'exchange')), 0)
        + coalesce((select sum(t.amount) from public.transactions t
                    where t.counter_account_id = a.id and t.type = 'transfer'), 0)
        + coalesce((select sum(t.counter_amount) from public.transactions t
                    where t.counter_account_id = a.id and t.type = 'exchange'), 0)
        as balance
from public.accounts a;

create view public.period_category_facts
with (security_invoker = true) as
select
    p.id as period_id,
    p.user_id,
    t.category_id,
    t.type,
    sum(t.amount_base) as fact_base,
    count(*)           as tx_count
from public.periods p
join public.transactions t
  on t.user_id = p.user_id
 and t.tx_date >= p.start_date
 and t.tx_date <  p.end_date
where t.type in ('expense', 'income')
group by p.id, p.user_id, t.category_id, t.type;
