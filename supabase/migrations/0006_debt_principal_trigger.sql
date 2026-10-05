-- Сколько тела кредита погасила операция (в минимальных единицах валюты кредита) и было ли это досрочное погашение.
-- Остаток кредита меняет триггер: вставка уменьшает, удаление возвращает, правка возвращает старое и применяет новое.
alter table public.transactions
    add column debt_principal bigint,
    add column debt_early boolean not null default false;

alter table public.transactions
    add constraint tx_debt_principal_needs_debt check (
        debt_principal is null or (debt_id is not null and debt_principal >= 0)
    );

create or replace function public.apply_debt_principal()
returns trigger
language plpgsql
security invoker
set search_path = public
as $$
begin
    if tg_op in ('UPDATE', 'DELETE') and old.debt_id is not null and old.debt_principal is not null then
        update public.debts
        set principal_remaining = principal_remaining + old.debt_principal
        where id = old.debt_id;
    end if;
    if tg_op in ('INSERT', 'UPDATE') and new.debt_id is not null and new.debt_principal is not null then
        update public.debts
        set principal_remaining = principal_remaining - new.debt_principal
        where id = new.debt_id;
    end if;
    return null;
end;
$$;

create trigger transactions_apply_debt_principal
    after insert or delete or update of debt_id, debt_principal on public.transactions
    for each row execute function public.apply_debt_principal();
