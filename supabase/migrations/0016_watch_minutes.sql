alter table public.rate_watches
    add column trade_open smallint not null default 630 check (trade_open between 0 and 1439),
    add column trade_close smallint not null default 1110 check (trade_close between 1 and 1440),
    add column digest_at smallint default 630 check (digest_at is null or digest_at between 0 and 1439),
    add constraint rate_watches_trade_minutes_check check (trade_open < trade_close);

update public.rate_watches
set trade_open = 630,
    trade_close = 1110,
    digest_at = case when digest_hour is null then null when digest_hour = 10 then 630 else digest_hour * 60 end;

alter table public.rate_watches
    add constraint rate_watches_digest_window_check check (
        digest_at is null
        or quiet_from = quiet_to
        or (quiet_to < quiet_from and digest_at >= quiet_to * 60 and digest_at < quiet_from * 60)
        or (quiet_to > quiet_from and (digest_at >= quiet_to * 60 or digest_at < quiet_from * 60))
    );

comment on column public.rate_watches.trade_open is
    'Начало часов биржи в минутах от полуночи Europe/Moscow; [trade_open, trade_close), будни без exchange_holidays. По замерам банк переходит на дневной курс в 10:00–10:15 и на ночной в 18:45–18:51, поэтому по умолчанию 10:30–18:30';
comment on column public.rate_watches.digest_at is 'Время сводки в минутах от полуночи по timezone слежения; null — без сводки';
comment on column public.rate_watches.trade_from is 'Не используется: заменено trade_open, оставлено для старых клиентов';
comment on column public.rate_watches.trade_to is 'Не используется: заменено trade_close, оставлено для старых клиентов';
comment on column public.rate_watches.digest_hour is 'Не используется: заменено digest_at, оставлено для старых клиентов';

create or replace function public.exchange_in_minutes(p_at timestamptz, p_open int default 630, p_close int default 1110)
returns boolean
language sql
stable
set search_path = public
as $$
    select extract(isodow from p_at at time zone 'Europe/Moscow') between 1 and 5
        and extract(hour from p_at at time zone 'Europe/Moscow') * 60 + extract(minute from p_at at time zone 'Europe/Moscow') >= p_open
        and extract(hour from p_at at time zone 'Europe/Moscow') * 60 + extract(minute from p_at at time zone 'Europe/Moscow') < p_close
        and not exists (select 1 from public.exchange_holidays h where h.date = (p_at at time zone 'Europe/Moscow')::date);
$$;

revoke execute on function public.exchange_in_minutes(timestamptz, int, int) from public, anon;
grant execute on function public.exchange_in_minutes(timestamptz, int, int) to authenticated, service_role;

update public.bank_rate_snapshots set in_hours = public.exchange_in_minutes(taken_at);

alter table public.bank_rate_snapshots alter column in_hours set default public.exchange_in_minutes(now());

alter table public.bot_info add column webhook_signature text;

revoke update on public.rate_watches from authenticated;
grant update (provider, from_currency, to_currency, category, target, jump_percent, good_percentile, improve_percent, trade_from, trade_to,
              trade_open, trade_close, digest_hour, digest_at, quiet_from, quiet_to, timezone, enabled)
    on public.rate_watches to authenticated;
