-- Слежение за курсом банка: снимки курсов провайдера, настройки слежения, подписчики Telegram, данные бота.
-- Снимки и bot_info общие (пишет только сервисный ключ из GitHub Actions), слежение и подписчики — владельца.

create table public.bank_rate_snapshots (
    id            bigserial primary key,
    provider      text not null,
    from_currency text not null,
    to_currency   text not null,
    category      text not null,
    buy           numeric(24, 12) not null check (buy > 0),
    sell          numeric(24, 12) check (sell is null or sell > 0),
    cbr           numeric(24, 12) check (cbr is null or cbr > 0),
    taken_at      timestamptz not null default now()
);

comment on table public.bank_rate_snapshots is
    'buy = сколько to_currency банк даёт за 1 from_currency при покупке to за from; sell = сколько to_currency нужно отдать за 1 from_currency; cbr = курс ЦБ, to за 1 from';

create index bank_rate_snapshots_pair_idx
    on public.bank_rate_snapshots (provider, from_currency, to_currency, category, taken_at desc);

create table public.rate_watches (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
    provider      text not null default 'tbank',
    from_currency text not null references public.currencies (code),
    to_currency   text not null references public.currencies (code),
    category      text not null default 'DebitCardsTransfers',
    target        numeric(24, 12) check (target is null or target > 0),
    jump_percent  numeric(6, 3) not null default 1.5 check (jump_percent > 0),
    digest_hour   smallint default 10 check (digest_hour is null or digest_hour between 0 and 23),
    quiet_from    smallint not null default 18 check (quiet_from between 0 and 23),
    quiet_to      smallint not null default 10 check (quiet_to between 0 and 23),
    timezone      text not null default 'Europe/Moscow',
    enabled       boolean not null default true,
    link_code     text not null unique default substr(md5(random()::text || clock_timestamp()::text), 1, 16),
    state         jsonb not null default '{}'::jsonb,
    created_at    timestamptz not null default now(),
    check (from_currency <> to_currency),
    -- Сводка только внутри окна отправки: окно = [quiet_to, quiet_from), всё остальное — тишина.
    check (
        digest_hour is null
        or (quiet_from = quiet_to)
        or (quiet_to < quiet_from and digest_hour >= quiet_to and digest_hour < quiet_from)
        or (quiet_to > quiet_from and (digest_hour >= quiet_to or digest_hour < quiet_from))
    )
);

comment on table public.rate_watches is
    'Уведомления уходят только в окне [quiet_to, quiet_from) по timezone, по умолчанию 10:00–18:00 Europe/Moscow; события вне окна собираются в одно сообщение на его начало';

create index rate_watches_user_idx on public.rate_watches (user_id);

create table public.telegram_subscribers (
    id         uuid primary key default gen_random_uuid(),
    watch_id   uuid not null references public.rate_watches (id) on delete cascade,
    chat_id    bigint not null,
    title      text,
    created_at timestamptz not null default now(),
    unique (watch_id, chat_id)
);

create table public.bot_info (
    id            int primary key check (id = 1),
    username      text,
    update_offset bigint not null default 0,
    updated_at    timestamptz not null default now()
);

alter table public.bank_rate_snapshots   enable row level security;
alter table public.rate_watches          enable row level security;
alter table public.telegram_subscribers  enable row level security;
alter table public.bot_info              enable row level security;

create policy "bank_rate_snapshots: read" on public.bank_rate_snapshots
    for select to authenticated using (true);

create policy "bot_info: read" on public.bot_info
    for select to authenticated using (true);

create policy "rate_watches: owner" on public.rate_watches
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- state и link_code ведёт воркер: владелец их читает, но не меняет.
revoke update on public.rate_watches from authenticated;
grant update (provider, from_currency, to_currency, category, target, jump_percent, digest_hour, quiet_from, quiet_to, timezone, enabled)
    on public.rate_watches to authenticated;

create policy "telegram_subscribers: owner select" on public.telegram_subscribers
    for select to authenticated
    using (exists (select 1 from public.rate_watches w where w.id = watch_id and w.user_id = auth.uid()));

create policy "telegram_subscribers: owner delete" on public.telegram_subscribers
    for delete to authenticated
    using (exists (select 1 from public.rate_watches w where w.id = watch_id and w.user_id = auth.uid()));

-- Почасовые точки для графика: последний снимок каждого часа. Приложение читает отсюда, а не из сырых 15-минутных снимков.
create view public.bank_rate_snapshots_hourly
with (security_invoker = true) as
select distinct on (provider, from_currency, to_currency, category, date_trunc('hour', taken_at))
    id, provider, from_currency, to_currency, category, buy, sell, cbr, taken_at
from public.bank_rate_snapshots
order by provider, from_currency, to_currency, category, date_trunc('hour', taken_at), taken_at desc;
