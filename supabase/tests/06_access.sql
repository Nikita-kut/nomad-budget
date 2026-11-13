begin;

do $$
declare
    v_owner uuid := tests.create_user('owner@example.test');
    v_b uuid := tests.create_user('b@example.test');
    v_hash text := encode(extensions.digest('code-ok', 'sha256'), 'hex');
    v_id uuid;
    v_second uuid;
    v_n bigint;
    v_fails text[] := '{}';
begin
    insert into public.app_admins (user_id) values (v_owner);
    perform tests.seed_finance(v_b, 'B');

    insert into public.invites (email, code_hash, created_by, expires_at) values
        ('new@example.test', v_hash, v_owner, now() + interval '7 days'),
        ('old@example.test', encode(extensions.digest('code-expired', 'sha256'), 'hex'), v_owner, now() + interval '1 second'),
        ('gone@example.test', encode(extensions.digest('code-revoked', 'sha256'), 'hex'), v_owner, now() + interval '7 days');
    update public.invites set created_at = now() - interval '2 days', expires_at = now() - interval '1 day' where email = 'old@example.test';
    update public.invites set revoked_at = now() where email = 'gone@example.test';

    if public.claim_invite(v_hash, 'other@example.test') is not null then
        v_fails := v_fails || 'A6: приглашение погашено чужой почтой'::text;
    end if;
    if public.claim_invite(encode(extensions.digest('code-expired', 'sha256'), 'hex'), 'old@example.test') is not null then
        v_fails := v_fails || 'A6: просроченное приглашение погашено'::text;
    end if;
    if public.claim_invite(encode(extensions.digest('code-revoked', 'sha256'), 'hex'), 'gone@example.test') is not null then
        v_fails := v_fails || 'A6: отозванное приглашение погашено'::text;
    end if;
    if public.claim_invite(encode(extensions.digest('wrong', 'sha256'), 'hex'), 'new@example.test') is not null then
        v_fails := v_fails || 'A6: неверный код принят'::text;
    end if;

    v_id := public.claim_invite(v_hash, ' NEW@example.test ');
    if v_id is null then
        v_fails := v_fails || 'A6: верный код с той же почтой (регистр, пробелы) не принят'::text;
    end if;
    v_second := public.claim_invite(v_hash, 'new@example.test');
    if v_second is not null then
        v_fails := v_fails || 'A6: занятое приглашение занято второй раз'::text;
    end if;

    perform public.release_invite(gen_random_uuid());
    if public.claim_invite(v_hash, 'new@example.test') is not null then
        v_fails := v_fails || 'A6: чужой токен освободил занятое приглашение'::text;
    end if;
    perform public.release_invite(v_id);
    v_id := public.claim_invite(v_hash, 'new@example.test');
    if v_id is null then
        v_fails := v_fails || 'A6: после неудачного создания учётки приглашение не освободилось'::text;
    end if;
    perform public.complete_invite(v_id, v_b);
    perform public.complete_invite(v_id, v_b);
    if public.claim_invite(v_hash, 'new@example.test') is not null then
        v_fails := v_fails || 'A6: использованное приглашение погашено снова'::text;
    end if;
    select count(*) into v_n from public.owner_actions where user_id = v_b and action = 'invite_redeemed';
    if v_n <> 1 then
        v_fails := v_fails || 'A6: погашение не записано в журнал пользователя'::text;
    end if;

    begin
        perform public.complete_invite(gen_random_uuid(), v_b);
        v_fails := v_fails || 'A6: закрыто незанятое приглашение'::text;
    exception when raise_exception then
        null;
    end;

    insert into public.invites (email, code_hash, created_by, expires_at)
    values ('late@example.test', encode(extensions.digest('code-late', 'sha256'), 'hex'), v_owner, now() + interval '7 days');
    v_second := public.claim_invite(encode(extensions.digest('code-late', 'sha256'), 'hex'), 'late@example.test');
    update public.invites set revoked_at = now() where email = 'late@example.test';
    begin
        perform public.complete_invite(v_second, v_b);
        v_fails := v_fails || 'A6: приглашение, отозванное после занятия, закрылось'::text;
    exception when raise_exception then
        null;
    end;

    insert into public.account_blocks (user_id) values (v_b);
    perform tests.become(v_b);
    begin
        perform private.reject_blocked();
        v_fails := v_fails || 'A12: проверка блокировки пропустила заблокированного'::text;
    exception when insufficient_privilege then
        null;
    end;
    execute 'reset role';
    delete from public.account_blocks where user_id = v_b;
    perform tests.become(v_b);
    begin
        perform private.reject_blocked();
    exception when others then
        v_fails := v_fails || format('A12: проверка блокировки не пускает обычного пользователя (%s)', sqlstate);
    end;
    execute 'reset role';
    perform tests.become_anon();
    begin
        perform private.reject_blocked();
    exception when others then
        v_fails := v_fails || format('A12: проверка блокировки не пускает анонима (%s)', sqlstate);
    end;
    execute 'reset role';

    perform tests.become(v_b);
    if public.is_admin() then
        v_fails := v_fails || 'A7: обычный пользователь — админ'::text;
    end if;
    begin
        insert into public.app_admins (user_id) values (v_b);
        v_fails := v_fails || 'A7: пользователь сделал себя админом'::text;
    exception when insufficient_privilege then
        null;
    end;
    begin
        perform count(*) from public.invites;
        v_fails := v_fails || 'A7: пользователь читает приглашения'::text;
    exception when insufficient_privilege then
        null;
    end;
    begin
        perform public.claim_invite(v_hash, 'new@example.test');
        v_fails := v_fails || 'A7: пользователь вызывает погашение напрямую'::text;
    exception when insufficient_privilege then
        null;
    end;
    execute 'reset role';

    perform tests.become(v_owner);
    if not public.is_admin() then
        v_fails := v_fails || 'A7: владелец не админ'::text;
    end if;
    select count(*) into v_n from public.transactions where user_id = v_b;
    if v_n <> 0 then
        v_fails := v_fails || 'A8: админ видит операции пользователя'::text;
    end if;
    select count(*) into v_n from public.owner_actions where user_id = v_b;
    if v_n <> 0 then
        v_fails := v_fails || 'A8: админ читает журнал пользователя через API'::text;
    end if;
    execute 'reset role';

    if cardinality(v_fails) > 0 then
        raise exception E'Доступ — нарушений: %\n%', cardinality(v_fails), array_to_string(v_fails, E'\n');
    end if;
    raise notice 'A6–A8 приглашения и админ: ok';
end $$;

rollback;
