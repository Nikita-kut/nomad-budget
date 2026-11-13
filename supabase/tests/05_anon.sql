begin;

do $$
declare
    v_a uuid := tests.create_user('a@example.test');
    r record;
    v_fails text[] := '{}';
    v_n bigint;
    v_checked int := 0;
begin
    perform tests.seed_finance(v_a, 'A');

    for r in
        select c.relname::text as name from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relkind in ('r', 'v', 'm')
        order by 1
    loop
        perform tests.become_anon();
        begin
            execute format('select count(*) from public.%I', r.name) into v_n;
            v_fails := v_fails || format('%s: anon читает (%s строк)', r.name, v_n);
        exception when insufficient_privilege then
            null;
        end;
        begin
            execute format('insert into public.%I default values', r.name);
            v_fails := v_fails || format('%s: anon вставил строку', r.name);
        exception when insufficient_privilege then
            null;
        when others then
            v_fails := v_fails || format('%s: вставка anon отклонена не правами (%s)', r.name, sqlstate);
        end;
        execute 'reset role';
        v_checked := v_checked + 1;
    end loop;

    for r in
        select p.oid::regprocedure::text as sig
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
        join pg_type t on t.oid = p.prorettype
        where p.prokind = 'f' and t.typname <> 'trigger' and p.pronargs = 0
    loop
        perform tests.become_anon();
        begin
            execute format('select %s', r.sig);
            v_fails := v_fails || format('%s: anon вызвал', r.sig);
        exception when insufficient_privilege then
            null;
        when others then
            v_fails := v_fails || format('%s: вызов anon отклонён не правами (%s)', r.sig, sqlstate);
        end;
        execute 'reset role';
    end loop;

    if cardinality(v_fails) > 0 then
        raise exception E'A5 аноним — нарушений: %\n%', cardinality(v_fails), array_to_string(v_fails, E'\n');
    end if;
    raise notice 'A5 аноним: ok, объектов %', v_checked;
end $$;

rollback;
