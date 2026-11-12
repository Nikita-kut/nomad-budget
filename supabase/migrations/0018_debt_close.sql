alter table public.debts
    add column opened_on date,
    add column close_reason text check (close_reason is null or close_reason in ('paid_off', 'early', 'manual')),
    add column written_off bigint not null default 0 check (written_off >= 0);

update public.debts d
set opened_on = coalesce((select min(t.tx_date) from public.transactions t where t.debt_id = d.id), d.created_at::date);

alter table public.debts alter column opened_on set default current_date;

comment on column public.debts.opened_on is 'С какого дня кредит учитывается в месяцах бюджета; для старых — дата первого платежа';
comment on column public.debts.close_reason is 'Почему закрыт: paid_off — выплачен, early — погашен досрочно, manual — закрыт вручную';
comment on column public.debts.written_off is 'Сколько остатка списано при закрытии (хвост от расхождения процентов с банком). Не доход и не расход';

create or replace function public.close_debt(p_id uuid, p_reason text, p_on date, p_write_off boolean)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
    update public.debts
    set closed_at = p_on::timestamptz,
        close_reason = p_reason,
        written_off = case when p_write_off then greatest(principal_remaining, 0) else 0 end,
        principal_remaining = case when p_write_off then least(principal_remaining, 0) else principal_remaining end
    where id = p_id and closed_at is null;
end $$;

create or replace function public.reopen_debt(p_id uuid)
returns void
language plpgsql
security invoker
set search_path = public
as $$
begin
    update public.debts
    set closed_at = null,
        close_reason = null,
        principal_remaining = principal_remaining + written_off,
        written_off = 0
    where id = p_id;
end $$;

revoke execute on function public.close_debt(uuid, text, date, boolean) from public, anon;
revoke execute on function public.reopen_debt(uuid) from public, anon;
grant execute on function public.close_debt(uuid, text, date, boolean) to authenticated;
grant execute on function public.reopen_debt(uuid) to authenticated;
