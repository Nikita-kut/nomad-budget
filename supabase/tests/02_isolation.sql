begin;

do $$
declare
    v_a uuid := tests.create_user('a@example.test');
    v_b uuid := tests.create_user('b@example.test');
    r record;
    v_a_ids text[];
    v_b_ids text[];
    v_row jsonb;
    v_pk_type text;
    v_n bigint;
    v_before text;
    v_fails text[] := '{}';
    v_checked int := 0;
    v_col text;
    v_col_type text;
    v_value text;
    v_detail text;
    v_unprobed text[] := '{}';
    v_unprobed_delete text[] := '{}';
begin
    perform tests.seed_finance(v_a, 'A');
    perform tests.seed_finance(v_b, 'B');
    v_before := tests.fingerprint(v_a);

    for r in select * from tests.user_tables() loop
        if r.owner_sql is null or r.pk is null then
            v_fails := v_fails || format('%s: не классифицирована', r.name);
            continue;
        end if;
        v_a_ids := tests.owned_ids(r.name, r.owner_sql, r.pk, v_a);
        v_b_ids := tests.owned_ids(r.name, r.owner_sql, r.pk, v_b);
        if cardinality(v_a_ids) = 0 or cardinality(v_b_ids) = 0 then
            v_fails := v_fails || format('%s: нет фикстуры — добавь строки в tests.seed_finance', r.name);
            continue;
        end if;
        execute format('select to_jsonb(t) from public.%I t where %I::text = $1', r.name, r.pk) into v_row using v_a_ids[1];
        select format_type(a.atttypid, a.atttypmod) into v_pk_type
        from pg_attribute a where a.attrelid = format('public.%I', r.name)::regclass and a.attname = r.pk;
        if v_pk_type = 'uuid' and r.pk <> 'user_id' then
            v_row := jsonb_set(v_row, array[r.pk], to_jsonb(gen_random_uuid()));
        end if;

        perform tests.become(v_b);

        execute format('select count(*) from public.%I where %I::text = any($1)', r.name, r.pk) into v_n using v_b_ids;
        if v_n <> cardinality(v_b_ids) then
            v_fails := v_fails || format('%s: B не видит свои строки (%s из %s) — проверка была бы холостой', r.name, v_n, cardinality(v_b_ids));
        end if;

        execute format('select count(*) from public.%I where %I::text = any($1)', r.name, r.pk) into v_n using v_a_ids;
        if v_n <> 0 then
            v_fails := v_fails || format('%s: B видит %s строк A', r.name, v_n);
        end if;

        execute 'reset role';
        select f.col, f.col_type into v_col, v_col_type from tests.free_column(r.name) f;
        if v_col is not null then
            execute format('select %I::text from public.%I where %I::text = $1', v_col, r.name, r.pk) into v_value using v_b_ids[1];
        end if;
        perform tests.become(v_b);

        if v_col is not null then
            begin
                execute format('update public.%I set %I = $1::%s', r.name, v_col, v_col_type) using v_value;
                get diagnostics v_n = row_count;
                raise exception using errcode = 'P0001', message = 'probe', detail = v_n::text;
            exception when raise_exception then
                if sqlerrm = 'probe' then
                    get stacked diagnostics v_detail = pg_exception_detail;
                    if v_detail::bigint > cardinality(v_b_ids) then
                        v_fails := v_fails || format('%s: update без where от B задел %s строк, своих %s — политика пропускает чужие', r.name, v_detail, cardinality(v_b_ids));
                    end if;
                else
                    v_fails := v_fails || format('%s: update без where — %s', r.name, sqlerrm);
                end if;
            when insufficient_privilege then
                null;
            when others then
                v_fails := v_fails || format('%s: update без where — %s %s', r.name, sqlstate, sqlerrm);
            end;
        else
            v_unprobed := v_unprobed || r.name;
        end if;

        begin
            execute format('delete from public.%I', r.name);
            get diagnostics v_n = row_count;
            raise exception using errcode = 'P0001', message = 'probe', detail = v_n::text;
        exception when raise_exception then
            if sqlerrm = 'probe' then
                get stacked diagnostics v_detail = pg_exception_detail;
                if v_detail::bigint > cardinality(v_b_ids) then
                    v_fails := v_fails || format('%s: delete без where от B задел %s строк, своих %s — политика пропускает чужие', r.name, v_detail, cardinality(v_b_ids));
                end if;
            else
                v_fails := v_fails || format('%s: delete без where — %s', r.name, sqlerrm);
            end if;
        when insufficient_privilege then
            null;
        when foreign_key_violation then
            v_unprobed_delete := v_unprobed_delete || r.name;
        when others then
            v_fails := v_fails || format('%s: delete без where — %s %s', r.name, sqlstate, sqlerrm);
        end;

        if r.owner_sql = 'user_id = $1' then
            begin
                execute format('update public.%I set user_id = $1 where %I::text = $2', r.name, r.pk) using v_a, v_b_ids[1];
                get diagnostics v_n = row_count;
                if v_n <> 0 then
                    v_fails := v_fails || format('%s: B передал свою строку A', r.name);
                end if;
            exception when insufficient_privilege then
                null;
            when others then
                v_fails := v_fails || format('%s: передача строки A отклонена не политикой (%s %s)', r.name, sqlstate, sqlerrm);
            end;
        end if;

        begin
            execute format('insert into public.%I select * from jsonb_populate_record(null::public.%I, $1)', r.name, r.name) using v_row;
            v_fails := v_fails || format('%s: B вставил строку от имени A', r.name);
        exception when others then
            if sqlstate <> '42501' then
                v_fails := v_fails || format('%s: вставка от имени A отклонена не политикой (%s %s)', r.name, sqlstate, sqlerrm);
            end if;
        end;

        execute 'reset role';
        v_checked := v_checked + 1;
    end loop;

    perform tests.become(v_b);
    select count(*) into v_n from public.account_balances where user_id = v_a;
    if v_n <> 0 then v_fails := v_fails || 'account_balances: B видит счета A'::text; end if;
    select count(*) into v_n from public.period_category_facts where user_id = v_a;
    if v_n <> 0 then v_fails := v_fails || 'period_category_facts: B видит факты A'::text; end if;
    execute 'reset role';

    if tests.fingerprint(v_a) <> v_before then
        v_fails := v_fails || 'данные A изменились после действий B'::text;
    end if;

    if cardinality(v_fails) > 0 then
        raise exception E'A2 изоляция — нарушений: %\n%', cardinality(v_fails), array_to_string(v_fails, E'\n');
    end if;
    raise notice 'A2 изоляция: ok, таблиц %, без пробы update (нет свободной колонки): %, delete без where упёрся в ссылки (покрывает A1): %', v_checked, v_unprobed, v_unprobed_delete;
end $$;

rollback;
