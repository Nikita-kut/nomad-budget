begin;

do $$
declare
    r record;
    v_fails text[] := '{}';
    v_client_rpcs constant text[] := array[
        'delete_own_account', 'ensure_currency', 'close_debt', 'reopen_debt',
        'change_base_currency', 'revert_base_currency'
    ];
begin
    for r in
        select c.relname from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relkind = 'r' and not c.relrowsecurity
    loop
        v_fails := v_fails || format('%s: RLS выключен', r.relname);
    end loop;

    for r in select name from tests.user_tables() where owner_sql is null or pk is null loop
        v_fails := v_fails || format('%s: таблица не классифицирована — нет user_id и её нет в tests.owned_via_parent или tests.shared_tables', r.name);
    end loop;

    for r in
        select p.tablename, p.policyname, p.roles, p.qual, p.with_check
        from pg_policies p
        where p.schemaname = 'public'
          and p.tablename not in (select name from tests.shared_tables)
    loop
        if r.qual = 'true' or r.with_check = 'true' then
            v_fails := v_fails || format('%s: политика «%s» открыта всем (true)', r.tablename, r.policyname);
        end if;
        if r.roles && array['anon', 'public']::name[] then
            v_fails := v_fails || format('%s: политика «%s» действует для %s', r.tablename, r.policyname, r.roles);
        end if;
    end loop;

    for r in
        select p.tablename, p.policyname, p.roles from pg_policies p
        where p.schemaname = 'public' and p.roles && array['anon', 'public']::name[]
          and p.tablename in (select name from tests.shared_tables)
    loop
        v_fails := v_fails || format('%s: политика «%s» открыта анониму', r.tablename, r.policyname);
    end loop;

    for r in
        select c.relname, c.reloptions from pg_class c join pg_namespace n on n.oid = c.relnamespace
        where n.nspname = 'public' and c.relkind in ('v', 'm')
          and not coalesce('security_invoker=true' = any(c.reloptions), false)
    loop
        v_fails := v_fails || format('%s: вьюха без security_invoker — обходит RLS', r.relname);
    end loop;

    for r in
        select c.relname, p.privilege_type
        from pg_class c join pg_namespace n on n.oid = c.relnamespace
        cross join (values ('SELECT'), ('INSERT'), ('UPDATE'), ('DELETE'), ('TRUNCATE')) as p(privilege_type)
        where n.nspname = 'public' and c.relkind in ('r', 'v', 'm')
          and has_table_privilege('anon', c.oid, p.privilege_type)
    loop
        v_fails := v_fails || format('%s: у anon есть %s', r.relname, r.privilege_type);
    end loop;

    for r in
        select p.oid::regprocedure as fn, p.prosecdef, p.proconfig, t.typname
        from pg_proc p
        join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
        join pg_type t on t.oid = p.prorettype
        where p.prokind = 'f'
    loop
        if r.prosecdef and not coalesce(exists (select 1 from unnest(r.proconfig) c where c like 'search_path=%'), false) then
            v_fails := v_fails || format('%s: security definer без search_path', r.fn);
        end if;
        if r.typname <> 'trigger' and has_function_privilege('anon', r.fn, 'execute') then
            v_fails := v_fails || format('%s: anon может вызвать', r.fn);
        end if;
    end loop;

    for r in select unnest(v_client_rpcs) as name loop
        if not exists (
            select 1 from pg_proc p join pg_namespace n on n.oid = p.pronamespace and n.nspname = 'public'
            where p.proname = r.name and has_function_privilege('authenticated', p.oid, 'execute')
        ) then
            v_fails := v_fails || format('%s: приложение вызывает, а authenticated не может', r.name);
        end if;
    end loop;

    if cardinality(v_fails) > 0 then
        raise exception E'A1 аудит каталога — нарушений: %\n%', cardinality(v_fails), array_to_string(v_fails, E'\n');
    end if;
    raise notice 'A1 аудит каталога: ok';
end $$;

rollback;
