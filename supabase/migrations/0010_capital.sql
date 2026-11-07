create table public.capital_goals (
    id          uuid primary key default gen_random_uuid(),
    user_id     uuid not null default auth.uid() references auth.users (id) on delete cascade,
    name        text not null,
    amount_base bigint not null check (amount_base > 0),
    target_date date not null,
    created_at  timestamptz not null default now()
);

create table public.capital_settings (
    user_id        uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    cushion_months smallint not null default 6 check (cushion_months between 1 and 36),
    annual_return  numeric(6, 3) not null default 0 check (annual_return between 0 and 100)
);

alter table public.capital_goals    enable row level security;
alter table public.capital_settings enable row level security;

create policy "capital_goals: owner" on public.capital_goals
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

create policy "capital_settings: owner" on public.capital_settings
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
