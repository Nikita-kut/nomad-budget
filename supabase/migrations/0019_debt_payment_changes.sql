create table public.debt_payment_changes (
    id              uuid primary key default gen_random_uuid(),
    user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
    debt_id         uuid not null references public.debts (id) on delete cascade,
    effective_from  date not null,
    monthly_payment bigint not null check (monthly_payment >= 0),
    source_tx_id    uuid references public.transactions (id) on delete set null,
    created_at      timestamptz not null default now()
);

comment on table public.debt_payment_changes is
    'История ежемесячного платежа по кредиту: с какой даты какой платёж. Платёж месяца — последняя запись с effective_from не позже даты платежа этого месяца. Новая запись появляется после досрочки с уменьшением платежа';

create index debt_payment_changes_debt_idx on public.debt_payment_changes (debt_id, effective_from);

alter table public.debt_payment_changes enable row level security;

create policy "debt_payment_changes: owner" on public.debt_payment_changes
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

insert into public.debt_payment_changes (user_id, debt_id, effective_from, monthly_payment)
select d.user_id, d.id, coalesce(d.opened_on, d.created_at::date), d.monthly_payment
from public.debts d;
