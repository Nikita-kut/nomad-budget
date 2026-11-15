-- Модули и стадии фич. Расчёты от них не зависят: это только видимость в приложении.

create table public.feature_stages (
    key        text primary key check (key ~ '^[a-z_]+\.[a-z_]+$'),
    stage      text not null default 'owner' check (stage in ('owner', 'all', 'off')),
    updated_at timestamptz not null default now()
);

alter table public.feature_stages enable row level security;

create policy "feature_stages: read" on public.feature_stages
    for select to authenticated using (true);

revoke insert, update, delete, truncate on public.feature_stages from anon, authenticated;

-- Уже выпущенные блоки открыты всем. Новые ключи добавляются только со стадией owner (проверка в CI).
insert into public.feature_stages (key, stage) values
    ('debts.screen', 'all'),
    ('debts.month_tile', 'all'),
    ('debts.plan', 'all'),
    ('cushion.header', 'all'),
    ('cushion.month_tile', 'all'),
    ('cushion.plan', 'all'),
    ('cushion.accounts', 'all'),
    ('funds.header', 'all'),
    ('funds.month', 'all'),
    ('funds.plan', 'all'),
    ('funds.accounts', 'all'),
    ('capital.screen', 'all'),
    ('capital.plan_forecast', 'all'),
    ('charts.screen', 'all'),
    ('currencies.exchange', 'all'),
    ('currencies.rates', 'all'),
    ('currencies.accounts', 'all'),
    ('currencies.settings', 'all'),
    ('bank_watch.rates', 'all'),
    ('inbox.entry', 'all'),
    ('tickets.button', 'all');

alter table public.user_settings
    add column modules_off text[] not null default '{}' check (cardinality(modules_off) <= 32),
    add column blocks_off text[] not null default '{}' check (cardinality(blocks_off) <= 128);

comment on column public.user_settings.modules_off is 'Модули, которые пользователь выключил (ключи из реестра Features.kt). Пусто — всё включено';
comment on column public.user_settings.blocks_off is 'Блоки внутри включённых модулей, которые пользователь скрыл';
