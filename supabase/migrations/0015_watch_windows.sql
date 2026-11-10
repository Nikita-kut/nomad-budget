create table public.exchange_holidays (
    date date primary key
);

comment on table public.exchange_holidays is
    'Будние дни, когда на валютном рынке биржи нет торгов. Список ведётся по https://www.moex.com/ru/tradingcalendar и дополняется раз в год';

insert into public.exchange_holidays (date) values
    ('2026-01-01'), ('2026-01-02'), ('2026-01-07'), ('2026-02-23'), ('2026-03-09'),
    ('2026-05-01'), ('2026-05-11'), ('2026-06-12'), ('2026-11-04'), ('2026-12-31')
on conflict do nothing;

alter table public.exchange_holidays enable row level security;

create policy "exchange_holidays: read" on public.exchange_holidays
    for select to authenticated using (true);

create or replace function public.exchange_in_hours(p_at timestamptz, p_from int default 10, p_to int default 19)
returns boolean
language sql
stable
set search_path = public
as $$
    select extract(isodow from p_at at time zone 'Europe/Moscow') between 1 and 5
        and extract(hour from p_at at time zone 'Europe/Moscow') >= p_from
        and extract(hour from p_at at time zone 'Europe/Moscow') < p_to
        and not exists (select 1 from public.exchange_holidays h where h.date = (p_at at time zone 'Europe/Moscow')::date);
$$;

revoke execute on function public.exchange_in_hours(timestamptz, int, int) from public, anon;
grant execute on function public.exchange_in_hours(timestamptz, int, int) to authenticated, service_role;

alter table public.bank_rate_snapshots add column in_hours boolean;

update public.bank_rate_snapshots set in_hours = public.exchange_in_hours(taken_at);

alter table public.bank_rate_snapshots
    alter column in_hours set default public.exchange_in_hours(now()),
    alter column in_hours set not null;

comment on column public.bank_rate_snapshots.in_hours is
    'Снимок сделан в часы торгов биржи (будний неторговый день и вечер/ночь — false). Сигналы, процентиль и медиана считаются только по true';

create or replace view public.bank_rate_snapshots_hourly
with (security_invoker = true) as
select distinct on (provider, from_currency, to_currency, category, date_trunc('hour', taken_at))
    id, provider, from_currency, to_currency, category, buy, sell, cbr, taken_at, in_hours
from public.bank_rate_snapshots
order by provider, from_currency, to_currency, category, date_trunc('hour', taken_at), taken_at desc;

alter table public.rate_watches
    add column good_percentile smallint not null default 85 check (good_percentile between 50 and 99),
    add column improve_percent numeric(6, 3) not null default 0.5 check (improve_percent > 0),
    add column trade_from smallint not null default 10 check (trade_from between 0 and 23),
    add column trade_to smallint not null default 19 check (trade_to between 1 and 24),
    add constraint rate_watches_trade_hours_check check (trade_from < trade_to);

comment on column public.rate_watches.jump_percent is 'Не используется: сигнал «скачок» убран, колонка оставлена для старых клиентов';
comment on column public.rate_watches.good_percentile is 'Выгодно = курс лучше стольких процентов замеров за 7 дней в часы биржи; зеркально для «не меняй»';
comment on column public.rate_watches.improve_percent is 'Повтор сообщения внутри окна — только при изменении курса на столько процентов к прошлому сообщению';
comment on column public.rate_watches.trade_from is 'Часы торгов биржи [trade_from, trade_to) по Europe/Moscow, будни без exchange_holidays';

comment on table public.rate_watches is
    'Сигналы — только в часы биржи [trade_from, trade_to) по Europe/Moscow. Сообщения уходят в окне [quiet_to, quiet_from) по timezone, по умолчанию 10:00–18:00; события вне окна попадают одной строкой в ежедневную сводку';

revoke update on public.rate_watches from authenticated;
grant update (provider, from_currency, to_currency, category, target, jump_percent, good_percentile, improve_percent, trade_from, trade_to,
              digest_hour, quiet_from, quiet_to, timezone, enabled)
    on public.rate_watches to authenticated;

update public.rate_watches
set state = jsonb_strip_nulls(jsonb_build_object(
    'last', state -> 'last',
    'last_at', state -> 'last_at',
    'last_digest_date', state -> 'last_digest_date'
));
