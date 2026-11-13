create schema if not exists tests;

create or replace function tests.create_user(p_email text)
returns uuid
language plpgsql
as $$
declare
    v_id uuid := gen_random_uuid();
begin
    insert into auth.users (id, instance_id, aud, role, email, encrypted_password, email_confirmed_at,
                            raw_app_meta_data, raw_user_meta_data, created_at, updated_at)
    values (v_id, '00000000-0000-0000-0000-000000000000', 'authenticated', 'authenticated', p_email,
            crypt('test-password-' || p_email, gen_salt('bf')), now(),
            '{"provider":"email","providers":["email"]}', '{}', now(), now());
    return v_id;
end $$;

create or replace function tests.seed_finance(p_user uuid, p_tag text)
returns void
language plpgsql
as $$
declare
    v_card     uuid;
    v_cushion  uuid;
    v_fund     uuid;
    v_expense  uuid;
    v_income   uuid;
    v_sub      uuid;
    v_period   uuid;
    v_debt     uuid;
    v_tx       uuid;
    v_watch    uuid;
begin
    insert into public.accounts (user_id, name, currency, kind) values (p_user, p_tag || ' card', 'RUB', 'card') returning id into v_card;
    insert into public.accounts (user_id, name, currency, kind, is_savings) values (p_user, p_tag || ' cushion', 'RUB', 'savings', true) returning id into v_cushion;
    insert into public.accounts (user_id, name, currency, kind, is_fund, fund_monthly) values (p_user, p_tag || ' fund', 'USD', 'account', true, 1000) returning id into v_fund;
    insert into public.categories (user_id, name, kind) values (p_user, p_tag || ' food', 'expense') returning id into v_expense;
    insert into public.categories (user_id, name, kind) values (p_user, p_tag || ' salary', 'income') returning id into v_income;
    insert into public.subcategories (user_id, category_id, name) values (p_user, v_expense, p_tag || ' cafe') returning id into v_sub;
    insert into public.periods (user_id, start_date, end_date, title) values (p_user, date '2026-11-01', date '2026-12-01', p_tag || ' Nov') returning id into v_period;
    insert into public.debts (user_id, name, currency, principal_remaining, monthly_payment, pay_day) values (p_user, p_tag || ' loan', 'RUB', 500000, 10000, 15) returning id into v_debt;
    insert into public.transactions (user_id, tx_date, type, account_id, amount, category_id, subcategory_id, rate_to_base, amount_base)
        values (p_user, date '2026-11-03', 'expense', v_card, 1500, v_expense, v_sub, 1, 1500);
    insert into public.transactions (user_id, tx_date, type, account_id, amount, counter_account_id, rate_to_base, amount_base)
        values (p_user, date '2026-11-04', 'transfer', v_card, 2000, v_cushion, 1, 2000);
    insert into public.transactions (user_id, tx_date, type, account_id, amount, category_id, debt_id, debt_principal, rate_to_base, amount_base)
        values (p_user, date '2026-11-15', 'expense', v_card, 10000, v_expense, v_debt, 8000, 1, 10000) returning id into v_tx;
    insert into public.budget_lines (user_id, period_id, category_id, subcategory_id, planned_base)
        values (p_user, v_period, v_expense, v_sub, 30000);
    insert into public.balance_checks (user_id, account_id, check_date, actual_balance, computed_balance)
        values (p_user, v_card, date '2026-11-10', 100, 100);
    insert into public.capital_goals (user_id, name, amount_base, target_date) values (p_user, p_tag || ' goal', 1000000, date '2027-12-31');
    insert into public.capital_settings (user_id) values (p_user) on conflict do nothing;
    insert into public.debt_month_plans (user_id, debt_id, period_start, early_amount) values (p_user, v_debt, date '2026-11-01', 5000);
    insert into public.debt_payment_changes (user_id, debt_id, effective_from, monthly_payment, source_tx_id) values (p_user, v_debt, date '2026-11-01', 9000, v_tx);
    insert into public.base_currency_changes (user_id, from_currency, to_currency, reverted_at, snapshot) values (p_user, 'USD', 'RUB', now(), '{}');
    insert into public.rate_watches (user_id, from_currency, to_currency) values (p_user, 'RUB', 'USD') returning id into v_watch;
    insert into public.telegram_subscribers (watch_id, chat_id, title) values (v_watch, abs(hashtext(p_tag)), p_tag);
end $$;

create or replace function tests.become(p_user uuid)
returns void
language plpgsql
as $$
begin
    perform set_config('request.jwt.claims', json_build_object('sub', p_user, 'role', 'authenticated')::text, true);
    perform set_config('request.jwt.claim.sub', p_user::text, true);
    execute 'set local role authenticated';
end $$;

create or replace function tests.become_anon()
returns void
language plpgsql
as $$
begin
    perform set_config('request.jwt.claims', '{"role":"anon"}', true);
    perform set_config('request.jwt.claim.sub', '', true);
    execute 'set local role anon';
end $$;

create table if not exists tests.shared_tables (name text primary key, why text not null);
truncate tests.shared_tables;
insert into tests.shared_tables values
    ('currencies', 'справочник валют, только чтение'),
    ('exchange_rates', 'курсы ЦБ, общие'),
    ('exchange_holidays', 'праздники биржи, общие'),
    ('bank_rate_snapshots', 'снимки курса банка, общие'),
    ('bot_info', 'состояние бота, пишет воркер'),
    ('telegram_chat_prefs', 'пауза чата бота, без политик, пишут только воркер и вебхук');

create table if not exists tests.owned_via_parent (name text primary key, owner_sql text not null);
truncate tests.owned_via_parent;
insert into tests.owned_via_parent values
    ('telegram_subscribers', 'watch_id in (select w.id from public.rate_watches w where w.user_id = $1)');

create or replace function tests.user_tables()
returns table (name text, owner_sql text, pk text)
language sql
stable
as $$
    select c.relname::text,
           coalesce(v.owner_sql,
                    case when exists (select 1 from pg_attribute a where a.attrelid = c.oid and a.attname = 'user_id' and not a.attisdropped)
                         then 'user_id = $1' end),
           (select a.attname::text from pg_index i join pg_attribute a on a.attrelid = i.indrelid and a.attnum = i.indkey[0]
             where i.indrelid = c.oid and i.indisprimary)
    from pg_class c
    join pg_namespace n on n.oid = c.relnamespace and n.nspname = 'public'
    left join tests.owned_via_parent v on v.name = c.relname
    where c.relkind = 'r'
      and c.relname not in (select s.name from tests.shared_tables s)
    order by 1
$$;

create or replace function tests.owned_ids(p_table text, p_owner_sql text, p_pk text, p_user uuid)
returns text[]
language plpgsql
as $$
declare
    v_ids text[];
begin
    execute format('select coalesce(array_agg(%I::text), ''{}'') from public.%I where %s', p_pk, p_table, p_owner_sql)
        into v_ids using p_user;
    return v_ids;
end $$;

create or replace function tests.fingerprint(p_user uuid)
returns text
language plpgsql
as $$
declare
    r record;
    v_part text;
    v_all text := '';
begin
    for r in select * from tests.user_tables() loop
        execute format('select coalesce(md5(string_agg(t::text, ''|'' order by t::text)), ''-'') from public.%I t where %s',
                       r.name, r.owner_sql)
            into v_part using p_user;
        v_all := v_all || r.name || ':' || v_part || ';';
    end loop;
    return md5(v_all);
end $$;
