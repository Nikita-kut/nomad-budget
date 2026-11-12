alter table public.user_settings
    add column currencies text[] not null default '{}';

comment on column public.user_settings.currencies is
    'Валюты пользователя: что видно в настройках, выборе валюты счёта и на экране курсов. Справочник currencies общий, этот список — личный. Валюта счёта показывается всегда, даже если её нет в списке';

update public.user_settings s
set currencies = coalesce((
    select array_agg(distinct code order by code)
    from (
        select a.currency as code from public.accounts a where a.user_id = s.user_id
        union
        select w.from_currency from public.rate_watches w where w.user_id = s.user_id
        union
        select w.to_currency from public.rate_watches w where w.user_id = s.user_id
    ) used
    where code <> s.base_currency
), '{}');
