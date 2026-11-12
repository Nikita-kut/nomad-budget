-- Смена базовой валюты: пересчёт всех сумм в базе одной транзакцией, журнал со снимком для отката,
-- защита от операций, записанных устройством в старой базе.

alter table public.transactions
    add column base_currency text references public.currencies (code);

comment on column public.transactions.base_currency is
    'В какой базе клиент посчитал amount_base и rate_to_base. Если она не совпадает с базой пользователя, триггер пересчитывает суммы при записи. null — старый клиент, суммы принимаются как есть';

create table public.base_currency_changes (
    id            uuid primary key default gen_random_uuid(),
    user_id       uuid not null default auth.uid() references auth.users (id) on delete cascade,
    from_currency text not null references public.currencies (code),
    to_currency   text not null references public.currencies (code),
    changed_at    timestamptz not null default now(),
    reverted_at   timestamptz,
    snapshot      jsonb not null
);

comment on table public.base_currency_changes is
    'Журнал смен базовой валюты. snapshot — суммы в старой базе до пересчёта: transactions [id, rate_to_base, amount_base], budget_lines и capital_goals [id, сумма], periods [id, savings_target]. Пишут только change_base_currency и revert_base_currency';

create index base_currency_changes_user_idx on public.base_currency_changes (user_id, changed_at desc);

alter table public.base_currency_changes enable row level security;

create policy "base_currency_changes: owner reads" on public.base_currency_changes
    for select to authenticated using (user_id = auth.uid());

create or replace function public.rub_per_unit(p_code text, p_date date)
returns numeric
language sql
stable
security definer
set search_path = public
as $$
    select case when p_code = 'RUB' then 1::numeric else coalesce(
        (select r.rate from public.exchange_rates r
         where r.base = 'RUB' and r.quote = p_code and r.rate_date <= p_date
         order by r.rate_date desc, r.fetched_at desc limit 1),
        (select r.rate from public.exchange_rates r
         where r.base = 'RUB' and r.quote = p_code
         order by r.rate_date asc, r.fetched_at desc limit 1)
    ) end
$$;

create or replace function public.base_factor(p_from text, p_to text, p_date date)
returns numeric
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_from numeric := public.rub_per_unit(p_from, p_date);
    v_to   numeric := public.rub_per_unit(p_to, p_date);
begin
    if v_from is null then raise exception 'Нет курса для %', p_from; end if;
    if v_to is null then raise exception 'Нет курса для %', p_to; end if;
    return v_from / v_to;
end $$;

create or replace function public.convert_base_minor(p_minor bigint, p_from text, p_to text, p_date date)
returns bigint
language sql
stable
security definer
set search_path = public
as $$
    select round(
        p_minor::numeric
        / power(10::numeric, (select minor_units from public.currencies where code = p_from))
        * public.base_factor(p_from, p_to, p_date)
        * power(10::numeric, (select minor_units from public.currencies where code = p_to))
    )::bigint
$$;

-- Суммы одной операции в новой базе: точная сумма, если валюта счёта (или полученная при обмене) совпадает с базой, иначе пересчёт по курсам дня.
create or replace function public.rebase_transaction(
    p_type public.tx_type, p_date date, p_account uuid, p_counter_account uuid,
    p_amount bigint, p_counter_amount bigint, p_rate numeric, p_amount_base bigint,
    p_from text, p_to text,
    out amount_base bigint, out rate_to_base numeric
)
language plpgsql
stable
security definer
set search_path = public
as $$
declare
    v_account  text;
    v_counter  text;
    v_mu_acc   smallint;
    v_mu_to    smallint;
begin
    select a.currency, c.minor_units into v_account, v_mu_acc
    from public.accounts a join public.currencies c on c.code = a.currency where a.id = p_account;
    select minor_units into v_mu_to from public.currencies where code = p_to;
    if v_account = p_to then
        amount_base := p_amount;
        rate_to_base := 1;
        return;
    end if;
    if p_type = 'exchange' and p_counter_account is not null then
        select currency into v_counter from public.accounts where id = p_counter_account;
        if v_counter = p_to then
            amount_base := p_counter_amount;
            rate_to_base := (p_counter_amount::numeric / power(10::numeric, v_mu_to)) / (p_amount::numeric / power(10::numeric, v_mu_acc));
            return;
        end if;
    end if;
    amount_base := public.convert_base_minor(p_amount_base, p_from, p_to, p_date);
    rate_to_base := p_rate * public.base_factor(p_from, p_to, p_date);
end $$;

create or replace function public.change_base_currency(p_code text)
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user uuid := auth.uid();
    v_old  text;
    v_new  text := upper(trim(p_code));
    v_snapshot jsonb;
begin
    if v_user is null then
        raise exception 'not authenticated';
    end if;
    select base_currency into v_old from public.user_settings where user_id = v_user for update;
    if v_old is null then
        raise exception 'Нет настроек пользователя';
    end if;
    if v_new = v_old then
        return;
    end if;
    perform public.ensure_currency(v_new);
    perform public.base_factor(v_old, v_new, current_date);

    v_snapshot := jsonb_build_object(
        'transactions', coalesce((select jsonb_agg(jsonb_build_array(t.id, t.rate_to_base, t.amount_base))
                                  from public.transactions t where t.user_id = v_user), '[]'::jsonb),
        'budget_lines', coalesce((select jsonb_agg(jsonb_build_array(b.id, b.planned_base))
                                  from public.budget_lines b where b.user_id = v_user), '[]'::jsonb),
        'periods', coalesce((select jsonb_agg(jsonb_build_array(p.id, p.savings_target))
                             from public.periods p where p.user_id = v_user and p.savings_target is not null), '[]'::jsonb),
        'capital_goals', coalesce((select jsonb_agg(jsonb_build_array(g.id, g.amount_base))
                                   from public.capital_goals g where g.user_id = v_user), '[]'::jsonb)
    );

    perform set_config('nomad.base_change', 'on', true);
    update public.user_settings
    set base_currency = v_new,
        currencies = array(select distinct c from unnest(currencies || v_old) c where c <> v_new order by c)
    where user_id = v_user;

    update public.transactions t
    set amount_base = r.amount_base, rate_to_base = r.rate_to_base, base_currency = v_new
    from public.transactions s
    cross join lateral public.rebase_transaction(
        s.type, s.tx_date, s.account_id, s.counter_account_id, s.amount, s.counter_amount, s.rate_to_base, s.amount_base, v_old, v_new
    ) r
    where t.id = s.id and s.user_id = v_user;

    update public.budget_lines b
    set planned_base = public.convert_base_minor(b.planned_base, v_old, v_new, p.start_date)
    from public.periods p
    where p.id = b.period_id and b.user_id = v_user;

    update public.periods
    set savings_target = public.convert_base_minor(savings_target, v_old, v_new, start_date)
    where user_id = v_user and savings_target is not null;

    update public.capital_goals
    set amount_base = greatest(1, public.convert_base_minor(amount_base, v_old, v_new, current_date))
    where user_id = v_user;

    insert into public.base_currency_changes (user_id, from_currency, to_currency, snapshot)
    values (v_user, v_old, v_new, v_snapshot);
    perform set_config('nomad.base_change', '', true);
end $$;

create or replace function public.revert_base_currency()
returns void
language plpgsql
security definer
set search_path = public
as $$
declare
    v_user   uuid := auth.uid();
    v_change public.base_currency_changes;
begin
    if v_user is null then
        raise exception 'not authenticated';
    end if;
    perform 1 from public.user_settings where user_id = v_user for update;
    select * into v_change from public.base_currency_changes
    where user_id = v_user order by changed_at desc limit 1;
    if v_change.id is null or v_change.reverted_at is not null then
        raise exception 'Нечего откатывать';
    end if;
    if exists (select 1 from public.transactions
               where user_id = v_user and greatest(created_at, updated_at) > v_change.changed_at) then
        raise exception 'После смены базы уже есть новые или изменённые операции, откат невозможен';
    end if;

    perform set_config('nomad.base_change', 'on', true);
    update public.user_settings
    set base_currency = v_change.from_currency,
        currencies = array(select distinct c from unnest(currencies || v_change.to_currency) c where c <> v_change.from_currency order by c)
    where user_id = v_user;

    update public.transactions t
    set rate_to_base = (e ->> 1)::numeric, amount_base = (e ->> 2)::bigint, base_currency = v_change.from_currency
    from jsonb_array_elements(v_change.snapshot -> 'transactions') e
    where t.id = (e ->> 0)::uuid and t.user_id = v_user;

    update public.budget_lines b
    set planned_base = coalesce(
        (select (e ->> 1)::bigint from jsonb_array_elements(v_change.snapshot -> 'budget_lines') e where (e ->> 0)::uuid = b.id),
        public.convert_base_minor(b.planned_base, v_change.to_currency, v_change.from_currency, p.start_date))
    from public.periods p
    where p.id = b.period_id and b.user_id = v_user;

    update public.periods p
    set savings_target = coalesce(
        (select (e ->> 1)::bigint from jsonb_array_elements(v_change.snapshot -> 'periods') e where (e ->> 0)::uuid = p.id),
        public.convert_base_minor(p.savings_target, v_change.to_currency, v_change.from_currency, p.start_date))
    where p.user_id = v_user and p.savings_target is not null;

    update public.capital_goals g
    set amount_base = coalesce(
        (select (e ->> 1)::bigint from jsonb_array_elements(v_change.snapshot -> 'capital_goals') e where (e ->> 0)::uuid = g.id),
        greatest(1, public.convert_base_minor(g.amount_base, v_change.to_currency, v_change.from_currency, current_date)))
    where g.user_id = v_user;

    update public.base_currency_changes set reverted_at = now() where id = v_change.id;
    perform set_config('nomad.base_change', '', true);
end $$;

-- Прямая запись base_currency разрешена, только пока сумм в базе нет (онбординг). Иначе значение остаётся прежним: менять — через change_base_currency.
create or replace function public.guard_base_currency()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if new.base_currency is distinct from old.base_currency
       and coalesce(current_setting('nomad.base_change', true), '') <> 'on'
       and (exists (select 1 from public.transactions where user_id = old.user_id)
            or exists (select 1 from public.budget_lines where user_id = old.user_id)
            or exists (select 1 from public.capital_goals where user_id = old.user_id)) then
        new.base_currency := old.base_currency;
    end if;
    return new;
end $$;

create trigger user_settings_guard_base_currency
    before update on public.user_settings
    for each row execute function public.guard_base_currency();

-- Операция, посчитанная устройством в старой базе (очередь без сети), пересчитывается в текущую.
create or replace function public.rebase_incoming_transaction()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
declare
    v_base text;
    v_rebased record;
begin
    if new.base_currency is null then
        return new;
    end if;
    select base_currency into v_base from public.user_settings where user_id = new.user_id;
    if v_base is null or v_base = new.base_currency then
        return new;
    end if;
    select * into v_rebased from public.rebase_transaction(
        new.type, new.tx_date, new.account_id, new.counter_account_id, new.amount, new.counter_amount,
        new.rate_to_base, new.amount_base, new.base_currency, v_base);
    new.amount_base := v_rebased.amount_base;
    new.rate_to_base := v_rebased.rate_to_base;
    new.base_currency := v_base;
    return new;
end $$;

create trigger transactions_rebase_incoming
    before insert or update of amount_base, rate_to_base, base_currency on public.transactions
    for each row execute function public.rebase_incoming_transaction();

revoke execute on function public.rub_per_unit(text, date) from public, anon;
revoke execute on function public.base_factor(text, text, date) from public, anon;
revoke execute on function public.convert_base_minor(bigint, text, text, date) from public, anon;
revoke execute on function public.rebase_transaction(public.tx_type, date, uuid, uuid, bigint, bigint, numeric, bigint, text, text) from public, anon, authenticated;
revoke execute on function public.change_base_currency(text) from public, anon;
revoke execute on function public.revert_base_currency() from public, anon;
grant execute on function public.change_base_currency(text) to authenticated;
grant execute on function public.revert_base_currency() to authenticated;
