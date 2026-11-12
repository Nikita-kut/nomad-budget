create table public.debt_month_plans (
    id           uuid primary key default gen_random_uuid(),
    user_id      uuid not null default auth.uid() references auth.users (id) on delete cascade,
    debt_id      uuid not null references public.debts (id) on delete cascade,
    period_start date not null,
    early_amount bigint not null check (early_amount >= 0),
    from_cushion boolean not null default false,
    unique (debt_id, period_start)
);

comment on table public.debt_month_plans is
    'Досрочка по кредиту в плане конкретного месяца бюджета (period_start — первый день месяца бюджета). Без строки действует шаблон debts.extra_payment с жизни. Досрочка из подушки в уравнение месяца не входит';

alter table public.debt_month_plans enable row level security;

create policy "debt_month_plans: owner" on public.debt_month_plans
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());
