begin;

do $$
declare
    v_a uuid := tests.create_user('a@example.test');
    v_b uuid := tests.create_user('b@example.test');
    r record;
    v_child_pk text;
    v_child_owner text;
    v_parent_owner text;
    v_parent_pk text;
    v_foreign text;
    v_row jsonb;
    v_row_id text;
    v_n bigint;
    v_fails text[] := '{}';
    v_checked int := 0;
begin
    perform tests.seed_finance(v_a, 'A');
    perform tests.seed_finance(v_b, 'B');

    for r in
        select c.conname,
               child.relname::text as child,
               parent.relname::text as parent,
               (select a.attname::text from unnest(c.conkey) with ordinality k(attnum, i)
                  join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum
                 where a.attname <> 'user_id' limit 1) as col,
               (select a.attname::text from unnest(c.confkey) with ordinality k(attnum, i)
                  join pg_attribute a on a.attrelid = c.confrelid and a.attnum = k.attnum
                 where a.attname <> 'user_id' limit 1) as parent_col,
               (select format_type(a.atttypid, a.atttypmod) from unnest(c.conkey) with ordinality k(attnum, i)
                  join pg_attribute a on a.attrelid = c.conrelid and a.attnum = k.attnum
                 where a.attname <> 'user_id' limit 1) as col_type
        from pg_constraint c
        join pg_class child on child.oid = c.conrelid
        join pg_class parent on parent.oid = c.confrelid
        join pg_namespace n on n.oid = child.relnamespace and n.nspname = 'public'
        join pg_namespace pn on pn.oid = parent.relnamespace and pn.nspname = 'public'
        where c.contype = 'f'
          and parent.relname in (select name from tests.user_tables())
          and child.relname in (select name from tests.user_tables())
        order by 2, 4
    loop
        select owner_sql, pk into v_child_owner, v_child_pk from tests.user_tables() where name = r.child;
        select owner_sql into v_parent_owner from tests.user_tables() where name = r.parent;

        execute format('select p.%I::text from public.%I p where %s and not exists (select 1 from public.%I c where c.%I = p.%I) limit 1',
                       r.parent_col, r.parent, v_parent_owner, r.child, r.col, r.parent_col)
            into v_foreign using v_a;
        execute format('select to_jsonb(t), %I::text from public.%I t where %s order by (%I is null) limit 1',
                       v_child_pk, r.child, v_child_owner, r.col)
            into v_row, v_row_id using v_b;
        if v_foreign is null or v_row is null then
            v_fails := v_fails || format('%s.%s: нет фикстуры — у A нужен объект %s без ссылок из %s, у B строка %s', r.child, r.col, r.parent, r.child, r.child);
            continue;
        end if;

        perform tests.become(v_b);

        begin
            execute format('insert into public.%I select * from jsonb_populate_record(null::public.%I, $1)', r.child, r.child)
                using jsonb_set(jsonb_set(v_row, array[v_child_pk], to_jsonb(gen_random_uuid())), array[r.col], to_jsonb(v_foreign));
            v_fails := v_fails || format('%s.%s: B создал строку со ссылкой на объект A (%s)', r.child, r.col, r.parent);
        exception when foreign_key_violation or insufficient_privilege then
            null;
        when others then
            v_fails := v_fails || format('%s.%s: вставка отклонена не ссылкой (%s %s)', r.child, r.col, sqlstate, sqlerrm);
        end;

        begin
            execute format('update public.%I set %I = $1::%s where %I::text = $2', r.child, r.col, r.col_type, v_child_pk)
                using v_foreign, v_row_id;
            get diagnostics v_n = row_count;
            if v_n > 0 then
                v_fails := v_fails || format('%s.%s: B перевёл свою строку на объект A (%s)', r.child, r.col, r.parent);
            end if;
        exception when foreign_key_violation or insufficient_privilege then
            null;
        when others then
            v_fails := v_fails || format('%s.%s: правка отклонена не ссылкой (%s %s)', r.child, r.col, sqlstate, sqlerrm);
        end;

        execute 'reset role';
        v_checked := v_checked + 1;
    end loop;

    if v_checked < 13 then
        v_fails := v_fails || format('проверено ссылок %s, ожидалось не меньше 13 — каталог читается неверно', v_checked);
    end if;

    if cardinality(v_fails) > 0 then
        raise exception E'A3 чужие ссылки — нарушений: %\n%', cardinality(v_fails), array_to_string(v_fails, E'\n');
    end if;
    raise notice 'A3 чужие ссылки: ok, ссылок %', v_checked;
end $$;

rollback;
