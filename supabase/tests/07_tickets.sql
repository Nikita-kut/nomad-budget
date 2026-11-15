begin;

do $$
declare
    v_a uuid := tests.create_user('a@example.test');
    v_b uuid := tests.create_user('b@example.test');
    v_id uuid;
    v_n bigint;
    v_fails text[] := '{}';
begin
    perform tests.become(v_a);

    insert into public.tickets (kind, body, screen, mark_x, mark_y, tech)
    values ('bug', 'Не сходится план', 'planning', 0.5, 0.25, '{"platform":"web"}')
    returning id into v_id;
    select count(*) into v_n from public.tickets where id = v_id and user_id = v_a and status = 'new';
    if v_n <> 1 then
        v_fails := v_fails || 'обращение создаётся не от имени автора или не в статусе new'::text;
    end if;

    begin
        insert into public.tickets (kind, body, status) values ('bug', 'x', 'done');
        v_fails := v_fails || 'пользователь создал обращение сразу в статусе done'::text;
    exception when insufficient_privilege then
        null;
    end;

    begin
        insert into public.tickets (kind, body, screenshot_path) values ('bug', 'x', v_b::text || '/' || gen_random_uuid()::text || '.png');
        v_fails := v_fails || 'обращение ссылается на скриншот в чужой папке'::text;
    exception when check_violation then
        null;
    end;

    begin
        insert into public.tickets (kind, body, screen) values ('bug', 'x', '<b>month</b>');
        v_fails := v_fails || 'экран с разметкой принят'::text;
    exception when check_violation then
        null;
    end;

    begin
        insert into public.tickets (kind, body) values ('bug', repeat('x', 2001));
        v_fails := v_fails || 'текст длиннее 2000 символов принят'::text;
    exception when check_violation then
        null;
    end;

    begin
        update public.tickets set status = 'done' where id = v_id;
        v_fails := v_fails || 'автор сменил статус обращения'::text;
    exception when insufficient_privilege then
        null;
    end;

    begin
        delete from public.tickets where id = v_id;
        v_fails := v_fails || 'автор удалил обращение'::text;
    exception when insufficient_privilege then
        null;
    end;

    for i in 1..19 loop
        insert into public.tickets (kind, body) values ('idea', 'idea ' || i);
    end loop;
    begin
        insert into public.tickets (kind, body) values ('idea', 'one too many');
        v_fails := v_fails || 'лимит 20 обращений в сутки не сработал'::text;
    exception when raise_exception then
        null;
    end;
    execute 'reset role';

    perform tests.become(v_b);
    select count(*) into v_n from public.tickets where user_id = v_a;
    if v_n <> 0 then
        v_fails := v_fails || 'B видит обращения A'::text;
    end if;
    insert into public.tickets (kind, body) values ('question', 'Как добавить валюту?');
    execute 'reset role';

    if cardinality(v_fails) > 0 then
        raise exception E'Обращения — нарушений: %\n%', cardinality(v_fails), array_to_string(v_fails, E'\n');
    end if;
    raise notice 'Обращения: ok';
end $$;

rollback;
