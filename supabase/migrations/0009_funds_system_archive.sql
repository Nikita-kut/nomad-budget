alter table public.accounts
    add column is_fund boolean not null default false,
    add column fund_monthly bigint check (fund_monthly is null or fund_monthly >= 0),
    add column fund_goal bigint check (fund_goal is null or fund_goal >= 0),
    add column fund_day smallint check (fund_day is null or fund_day between 1 and 31),
    add constraint accounts_fund_not_savings check (not (is_fund and is_savings));

alter table public.categories
    add column is_system boolean not null default false;

update public.categories set is_system = true where name = 'Корректировка';

alter table public.subcategories
    add column archived_at timestamptz;

create or replace function public.seed_correction_categories(p_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.categories (user_id, name, kind, sort_order, is_system) values
        (p_user, 'Корректировка', 'expense', 900, true),
        (p_user, 'Корректировка', 'income',  900, true)
    on conflict (user_id, kind, name) do update set is_system = true;
end $$;
