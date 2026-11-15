begin;

do $$
declare
    v uuid := tests.create_user('m@example.test');
    v_fails text[] := '{}';
begin
    perform tests.become(v);

    begin
        update public.user_settings set modules_off = '{debts,bank_watch}', blocks_off = '{funds.plan,cushion.header}' where user_id = v;
    exception when others then
        v_fails := v_fails || format('пользователь не может сохранить свои модули (%s %s)', sqlstate, sqlerrm);
    end;

    begin
        update public.user_settings set cushion_months = 7 where user_id = v;
    exception when others then
        v_fails := v_fails || format('пользователь не может сохранить настройки (%s %s)', sqlstate, sqlerrm);
    end;

    begin
        update public.user_settings set modules_off = array[repeat('x', 100)] where user_id = v;
        v_fails := v_fails || 'принят слишком длинный ключ модуля'::text;
    exception when check_violation then
        null;
    end;

    begin
        update public.user_settings set blocks_off = array['bad key;drop'] where user_id = v;
        v_fails := v_fails || 'принят ключ блока с мусором'::text;
    exception when check_violation then
        null;
    end;

    begin
        update public.feature_stages set stage = 'off' where key = 'charts.screen';
        v_fails := v_fails || 'пользователь меняет стадию фичи'::text;
    exception when insufficient_privilege then
        null;
    end;
    execute 'reset role';

    if cardinality(v_fails) > 0 then
        raise exception E'Модули — нарушений: %\n%', cardinality(v_fails), array_to_string(v_fails, E'\n');
    end if;
    raise notice 'Модули: ok';
end $$;

rollback;
