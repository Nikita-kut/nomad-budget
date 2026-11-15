-- Обращения пользователей: создаёт сам пользователь, статус меняет только админ (через админ-функцию), владельцу уходит уведомление без текста.

create table public.tickets (
    id              uuid primary key default gen_random_uuid(),
    number          bigint generated always as identity unique,
    user_id         uuid not null default auth.uid() references auth.users (id) on delete cascade,
    created_at      timestamptz not null default now(),
    kind            text not null check (kind in ('bug', 'idea', 'question')),
    body            text not null check (char_length(body) between 1 and 2000),
    screen          text check (screen is null or (char_length(screen) <= 64 and screen ~ '^[A-Za-z0-9_.:-]+$')),
    mark_x          real check (mark_x is null or mark_x between 0 and 1),
    mark_y          real check (mark_y is null or mark_y between 0 and 1),
    tech            jsonb not null default '{}'::jsonb check (pg_column_size(tech) <= 2048),
    screenshot_path text,
    status          text not null default 'new' check (status in ('new', 'in_progress', 'done', 'rejected')),
    status_at       timestamptz not null default now(),
    notified_at     timestamptz,
    check (screenshot_path is null or screenshot_path ~ ('^' || user_id::text || '/[0-9a-f-]{36}\.png$'))
);

create index tickets_user_idx on public.tickets (user_id, created_at desc);
create index tickets_unnotified_idx on public.tickets (created_at) where notified_at is null;

alter table public.tickets enable row level security;

create policy "tickets: own read" on public.tickets
    for select to authenticated using (user_id = auth.uid());

create policy "tickets: own create" on public.tickets
    for insert to authenticated with check (user_id = auth.uid() and status = 'new' and notified_at is null);

revoke update, delete, truncate on public.tickets from anon, authenticated;
revoke insert on public.tickets from authenticated;
grant insert (kind, body, screen, mark_x, mark_y, tech, screenshot_path) on public.tickets to authenticated;

create or replace function public.limit_tickets()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    if (select count(*) from public.tickets where user_id = new.user_id and created_at > now() - interval '1 day') >= 20 then
        raise exception 'too many tickets today' using errcode = 'P0001', hint = 'Не больше 20 обращений в сутки';
    end if;
    return new;
end $$;

create trigger tickets_limit
    before insert on public.tickets
    for each row execute function public.limit_tickets();

-- Скриншоты: приватная корзина, путь <user_id>/<uuid>.png, до 2 МБ, только PNG. Пользователь кладёт и читает только свои, перезаписать и удалить не может.
insert into storage.buckets (id, name, public, file_size_limit, allowed_mime_types)
values ('tickets', 'tickets', false, 2097152, array['image/png'])
on conflict (id) do update set public = false, file_size_limit = excluded.file_size_limit, allowed_mime_types = excluded.allowed_mime_types;

create policy "tickets bucket: own upload" on storage.objects
    for insert to authenticated
    with check (bucket_id = 'tickets' and (storage.foldername(name))[1] = auth.uid()::text and name ~ '^[0-9a-f-]{36}/[0-9a-f-]{36}\.png$');

create policy "tickets bucket: own read" on storage.objects
    for select to authenticated
    using (bucket_id = 'tickets' and (storage.foldername(name))[1] = auth.uid()::text);
