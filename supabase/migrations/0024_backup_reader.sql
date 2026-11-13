-- Роль для ночного дампа: только чтение, без входа, пока владелец не задаст пароль вне миграций.
do $$
begin
    if not exists (select 1 from pg_roles where rolname = 'backup_reader') then
        create role backup_reader nologin bypassrls;
    end if;
end $$;

alter role backup_reader set default_transaction_read_only = on;

grant usage on schema public to backup_reader;
grant select on all tables in schema public to backup_reader;
grant select on all sequences in schema public to backup_reader;
alter default privileges in schema public grant select on tables to backup_reader;
alter default privileges in schema public grant select on sequences to backup_reader;

-- Пользователи Auth: у postgres нет права передавать доступ к auth, поэтому — функции владельца.
create schema if not exists backup;
revoke all on schema backup from public, anon, authenticated;
grant usage on schema backup to backup_reader;

create or replace function backup.auth_users()
returns setof jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select to_jsonb(u) from auth.users u order by u.created_at
$$;

create or replace function backup.auth_identities()
returns setof jsonb
language sql
stable
security definer
set search_path = ''
as $$
    select to_jsonb(i) from auth.identities i order by i.created_at
$$;

revoke execute on all functions in schema backup from public, anon, authenticated;
grant execute on all functions in schema backup to backup_reader;
