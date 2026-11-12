-- Личные настройки чата в боте: пауза. Пишет только вебхук (service role), владелец слежения их не видит.

create table public.telegram_chat_prefs (
    chat_id      bigint primary key,
    paused_until timestamptz,
    updated_at   timestamptz not null default now()
);

comment on table public.telegram_chat_prefs is
    'Настройки чата в Telegram, общие для всех его подписок. paused_until — до какого момента не слать сигналы и сводки (/pause, /resume); /rate отвечает всегда. Политик нет: доступ только у service role';

alter table public.telegram_chat_prefs enable row level security;
