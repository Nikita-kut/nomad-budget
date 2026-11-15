-- Версии клиента: ниже min_version приложение просит обновиться, ниже latest_version — подсказывает. Пишет только сервер (релиз).

create table public.app_config (
    id             int primary key default 1 check (id = 1),
    min_version    text not null default '0.0.0' check (min_version ~ '^\d{1,3}\.\d{1,3}\.\d{1,3}$'),
    latest_version text not null default '0.0.0' check (latest_version ~ '^\d{1,3}\.\d{1,3}\.\d{1,3}$'),
    apk_url        text check (apk_url is null or apk_url ~ '^https://github\.com/[A-Za-z0-9_.-]+/[A-Za-z0-9_.-]+/releases/'),
    updated_at     timestamptz not null default now()
);

alter table public.app_config enable row level security;

create policy "app_config: read" on public.app_config
    for select to authenticated using (true);

revoke insert, update, delete, truncate on public.app_config from anon, authenticated;

insert into public.app_config (id, min_version, latest_version) values (1, '0.0.0', '0.35.0');
