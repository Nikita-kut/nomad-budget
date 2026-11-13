begin;

do $$
declare
    v_a uuid := tests.create_user('a@example.test');
    v_b uuid := tests.create_user('b@example.test');
    v_a_debt uuid;
    v_before text;
    r record;
    v_call text;
    v_cases jsonb;
    v_must_succeed constant text[] := array[
        'change_base_currency(text)', 'revert_base_currency()', 'ensure_currency(text)', 'delete_own_account()'
    ];
    v_sig text;
    v_fails text[] := '{}';
    v_checked int := 0;
begin
    perform tests.seed_finance(v_a, 'A');
    perform tests.seed_finance(v_b, 'B');
    select id into v_a_debt from public.debts where user_id = v_a limit 1;
    v_before := tests.fingerprint(v_a);

    v_cases := jsonb_build_object(
        'close_debt(uuid,text,date,boolean)', format('select public.close_debt(%L, ''manual'', date ''2026-11-20'', true)', v_a_debt),
        'reopen_debt(uuid)', format('select public.reopen_debt(%L)', v_a_debt),
        'ensure_currency(text)', 'select public.ensure_currency(''EUR'')',
        'change_base_currency(text)', 'select public.change_base_currency(''USD'')',
        'revert_base_currency()', 'select public.revert_base_currency()',
        'exchange_in_hours(timestamp with time zone,integer,integer)', 'select public.exchange_in_hours(now())',
        'exchange_in_minutes(timestamp with time zone,integer,integer)', 'select public.exchange_in_minutes(now())',
        'rub_per_unit(text,date)', 'select public.rub_per_unit(''USD'', current_date)',
        'base_factor(text,text,date)', 'select public.base_factor(''USD'', ''RUB'', current_date)',
        'convert_base_minor(bigint,text,text,date)', 'select public.convert_base_minor(100, ''USD'', ''RUB'', current_date)',
        'delete_own_account()', 'select public.delete_own_account()'
    );

    for r in
        select p.oid::regprocedure::text as sig, p.proname::text as name
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
        join pg_type t on t.oid = p.prorettype
        where p.prokind = 'f' and t.typname <> 'trigger'
          and has_function_privilege('authenticated', p.oid, 'execute')
        order by (p.proname = 'delete_own_account'), 1
    loop
        v_call := v_cases ->> replace(r.sig, 'public.', '');
        if v_call is null then
            v_fails := v_fails || format('%s: authenticated может вызвать, а тест-кейса нет — добавь в 04_rpc.sql', r.sig);
            continue;
        end if;
        v_sig := replace(r.sig, 'public.', '');
        perform tests.become(v_b);
        begin
            execute v_call;
        exception when others then
            if v_sig = any(v_must_succeed) then
                v_fails := v_fails || format('%s: у B вызов упал (%s %s) — кейс холостой, функция не дошла до записи', r.sig, sqlstate, sqlerrm);
            end if;
        end;
        execute 'reset role';
        if tests.fingerprint(v_a) <> v_before then
            v_fails := v_fails || format('%s: вызов от B изменил данные A', r.sig);
            v_before := tests.fingerprint(v_a);
        end if;
        v_checked := v_checked + 1;
    end loop;

    if exists (select 1 from auth.users where id = v_b) then
        v_fails := v_fails || 'delete_own_account: B не удалился — кейс холостой'::text;
    end if;
    if not exists (select 1 from auth.users where id = v_a) then
        v_fails := v_fails || 'delete_own_account от B удалил A'::text;
    end if;

    if cardinality(v_fails) > 0 then
        raise exception E'A4 функции — нарушений: %\n%', cardinality(v_fails), array_to_string(v_fails, E'\n');
    end if;
    raise notice 'A4 функции: ok, проверено %', v_checked;
end $$;

rollback;
