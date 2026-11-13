-- Доступ для других людей: админ, приглашения, блокировка, журнал действий владельца.
-- Финансовых таблиц здесь нет: админ видит только эти служебные таблицы и учётки Auth.

create table public.app_admins (
    user_id    uuid primary key references auth.users (id) on delete cascade,
    created_at timestamptz not null default now()
);

create table public.invites (
    id         uuid primary key default gen_random_uuid(),
    email      text not null check (email = lower(trim(email)) and email like '%_@_%'),
    code_hash  text not null unique,
    created_by uuid references auth.users (id) on delete set null,
    created_at timestamptz not null default now(),
    expires_at timestamptz not null,
    claimed_at timestamptz,
    claim_token uuid,
    used_at    timestamptz,
    used_by    uuid references auth.users (id) on delete set null,
    revoked_at timestamptz,
    check (expires_at > created_at)
);

create table public.account_blocks (
    user_id    uuid primary key references auth.users (id) on delete cascade,
    blocked_at timestamptz not null default now()
);

create table public.owner_actions (
    id         uuid primary key default gen_random_uuid(),
    user_id    uuid not null references auth.users (id) on delete cascade,
    action     text not null check (action in ('invite_redeemed', 'reset_link_created', 'blocked', 'unblocked')),
    created_at timestamptz not null default now()
);

create index owner_actions_user_idx on public.owner_actions (user_id, created_at desc);

alter table public.app_admins     enable row level security;
alter table public.invites        enable row level security;
alter table public.account_blocks enable row level security;
alter table public.owner_actions  enable row level security;

create policy "owner_actions: own" on public.owner_actions
    for select to authenticated using (user_id = auth.uid());

revoke all on public.app_admins, public.invites, public.account_blocks from anon, authenticated;
revoke insert, update, delete, truncate on public.owner_actions from anon, authenticated;

comment on table public.owner_actions is
    'Что владелец сделал с учёткой пользователя (ссылка сброса, блокировка). Пользователь видит свои строки. Пишет только сервер';

create or replace function public.is_admin()
returns boolean
language sql
stable
security definer
set search_path = public
as $$
    select exists (select 1 from public.app_admins where user_id = auth.uid())
$$;

revoke execute on function public.is_admin() from public, anon;
grant execute on function public.is_admin() to authenticated;

-- Погашение приглашения в два шага: занять (атомарно, один победитель, со своим токеном), затем закрыть после создания учётки.
create or replace function public.claim_invite(p_code_hash text, p_email text)
returns uuid
language sql
security definer
set search_path = public
as $$
    update public.invites
    set claimed_at = now(), claim_token = gen_random_uuid()
    where code_hash = p_code_hash
      and email = lower(trim(p_email))
      and used_at is null
      and revoked_at is null
      and expires_at > now()
      and (claimed_at is null or claimed_at < now() - interval '2 minutes')
    returning claim_token
$$;

create or replace function public.release_invite(p_claim uuid)
returns void
language sql
security definer
set search_path = public
as $$
    update public.invites set claimed_at = null, claim_token = null where claim_token = p_claim and used_at is null
$$;

create or replace function public.complete_invite(p_claim uuid, p_user uuid)
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if exists (select 1 from public.invites where used_by = p_user and claim_token = p_claim) then
        return;
    end if;
    update public.invites set used_at = now(), used_by = p_user
    where claim_token = p_claim and used_at is null and revoked_at is null;
    if not found then
        raise exception 'invite claim is not active';
    end if;
    insert into public.owner_actions (user_id, action) values (p_user, 'invite_redeemed');
end $$;

revoke execute on function public.claim_invite(text, text) from public, anon, authenticated;
revoke execute on function public.release_invite(uuid) from public, anon, authenticated;
revoke execute on function public.complete_invite(uuid, uuid) from public, anon, authenticated;
grant execute on function public.claim_invite(text, text) to service_role;
grant execute on function public.release_invite(uuid) to service_role;
grant execute on function public.complete_invite(uuid, uuid) to service_role;

-- Заблокированный теряет доступ к данным сразу, не дожидаясь конца срока токена: проверка на каждый запрос PostgREST.
create schema if not exists private;
revoke all on schema private from public;
grant usage on schema private to anon, authenticated, authenticator;

create or replace function private.reject_blocked()
returns void
language plpgsql
stable
security definer
set search_path = ''
as $$
begin
    if auth.uid() is not null and exists (select 1 from public.account_blocks where user_id = auth.uid()) then
        raise exception 'account blocked' using errcode = '42501';
    end if;
end $$;

revoke execute on function private.reject_blocked() from public;
grant execute on function private.reject_blocked() to anon, authenticated, authenticator;

alter role authenticator set pgrst.db_pre_request = 'private.reject_blocked';
notify pgrst, 'reload config';
