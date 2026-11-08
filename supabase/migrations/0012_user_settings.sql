-- Настройки пользователя: начало месяца бюджета, дни дохода, базовая валюта, параметры подушки, отметка онбординга.
-- capital_settings остаётся в схеме, но приложение читает и пишет cushion_months и annual_return отсюда.

create table public.user_settings (
    user_id          uuid primary key default auth.uid() references auth.users (id) on delete cascade,
    period_start_day smallint not null default 1 check (period_start_day between 1 and 28),
    income_days      smallint[] not null default '{}'
                     check (income_days <@ '{1,2,3,4,5,6,7,8,9,10,11,12,13,14,15,16,17,18,19,20,21,22,23,24,25,26,27,28,29,30,31}'::smallint[]),
    base_currency    text not null default 'RUB' references public.currencies (code),
    cushion_months   smallint not null default 6 check (cushion_months between 1 and 36),
    annual_return    numeric(6, 3) not null default 0 check (annual_return between 0 and 100),
    onboarded_at     timestamptz
);

alter table public.user_settings enable row level security;

create policy "user_settings: owner" on public.user_settings
    for all to authenticated using (user_id = auth.uid()) with check (user_id = auth.uid());

-- Существующие пользователи уже настроены: месяц с 5-го, параметры подушки из capital_settings.
insert into public.user_settings (user_id, period_start_day, cushion_months, annual_return, onboarded_at)
select
    u.id,
    5,
    coalesce(cs.cushion_months, 6),
    coalesce(cs.annual_return, 0),
    now()
from auth.users u
left join public.capital_settings cs on cs.user_id = u.id
on conflict (user_id) do nothing;

-- Новому пользователю: строка настроек и категории «Корректировка». Счета и категории создаёт онбординг.
create or replace function public.handle_new_user()
returns trigger
language plpgsql
security definer
set search_path = public
as $$
begin
    insert into public.user_settings (user_id) values (new.id) on conflict (user_id) do nothing;
    perform public.seed_correction_categories(new.id);
    return new;
end $$;

-- Удаление своего аккаунта: каскад снесёт все пользовательские таблицы.
create or replace function public.delete_own_account()
returns void
language plpgsql
security definer
set search_path = public
as $$
begin
    if auth.uid() is null then
        raise exception 'not authenticated';
    end if;
    delete from auth.users where id = auth.uid();
end $$;

revoke execute on function public.delete_own_account() from public, anon;
grant execute on function public.delete_own_account() to authenticated;

-- Добавление валюты по коду ISO 4217 из белого списка. Курс подтянет ежедневный скрипт.
create or replace function public.ensure_currency(p_code text)
returns public.currencies
language plpgsql
security definer
set search_path = public
as $$
declare
    v_code  text := upper(trim(p_code));
    v_row   public.currencies;
    v_known constant text[] := array[
        'AED', 'AFN', 'ALL', 'AMD', 'ANG', 'AOA', 'ARS', 'AUD', 'AWG', 'AZN', 'BAM', 'BBD',
        'BDT', 'BGN', 'BHD', 'BIF', 'BMD', 'BND', 'BOB', 'BRL', 'BSD', 'BTN', 'BWP', 'BYN',
        'BZD', 'CAD', 'CDF', 'CHF', 'CLP', 'CNY', 'COP', 'CRC', 'CUP', 'CVE', 'CZK', 'DJF',
        'DKK', 'DOP', 'DZD', 'EGP', 'ERN', 'ETB', 'EUR', 'FJD', 'FKP', 'GBP', 'GEL', 'GHS',
        'GIP', 'GMD', 'GNF', 'GTQ', 'GYD', 'HKD', 'HNL', 'HTG', 'HUF', 'IDR', 'ILS', 'INR',
        'IQD', 'IRR', 'ISK', 'JMD', 'JOD', 'JPY', 'KES', 'KGS', 'KHR', 'KMF', 'KRW', 'KWD',
        'KYD', 'KZT', 'LAK', 'LBP', 'LKR', 'LRD', 'LSL', 'LYD', 'MAD', 'MDL', 'MGA', 'MKD',
        'MMK', 'MNT', 'MOP', 'MRU', 'MUR', 'MVR', 'MWK', 'MXN', 'MYR', 'MZN', 'NAD', 'NGN',
        'NIO', 'NOK', 'NPR', 'NZD', 'OMR', 'PAB', 'PEN', 'PGK', 'PHP', 'PKR', 'PLN', 'PYG',
        'QAR', 'RON', 'RSD', 'RUB', 'RWF', 'SAR', 'SBD', 'SCR', 'SDG', 'SEK', 'SGD', 'SHP',
        'SLE', 'SOS', 'SRD', 'SSP', 'STN', 'SYP', 'SZL', 'THB', 'TJS', 'TMT', 'TND', 'TOP',
        'TRY', 'TTD', 'TWD', 'TZS', 'UAH', 'UGX', 'USD', 'UYU', 'UZS', 'VES', 'VND', 'VUV',
        'WST', 'XAF', 'XCD', 'XOF', 'XPF', 'YER', 'ZAR', 'ZMW', 'ZWG'
    ];
    v_zero  constant text[] := array['BIF', 'CLP', 'DJF', 'GNF', 'ISK', 'JPY', 'KMF', 'KRW', 'PYG', 'RWF', 'UGX', 'VND', 'VUV', 'XAF', 'XOF', 'XPF'];
    v_three constant text[] := array['BHD', 'IQD', 'JOD', 'KWD', 'LYD', 'OMR', 'TND'];
begin
    if auth.uid() is null then
        raise exception 'not authenticated';
    end if;
    if v_code is null or v_code !~ '^[A-Z]{3}$' or not (v_code = any (v_known)) then
        raise exception 'unknown currency code: %', p_code;
    end if;

    insert into public.currencies (code, minor_units, symbol)
    values (
        v_code,
        case when v_code = any (v_zero) then 0 when v_code = any (v_three) then 3 else 2 end,
        case v_code
        when 'AMD' then '֏'
        when 'AUD' then 'A$'
        when 'AZN' then '₼'
        when 'BDT' then '৳'
        when 'BRL' then 'R$'
        when 'CAD' then 'C$'
        when 'CNY' then '¥'
        when 'CRC' then '₡'
        when 'CZK' then 'Kč'
        when 'DKK' then 'kr'
        when 'EUR' then '€'
        when 'GBP' then '£'
        when 'GEL' then '₾'
        when 'GHS' then '₵'
        when 'HKD' then 'HK$'
        when 'HUF' then 'Ft'
        when 'IDR' then 'Rp'
        when 'ILS' then '₪'
        when 'INR' then '₹'
        when 'ISK' then 'kr'
        when 'JPY' then '¥'
        when 'KHR' then '៛'
        when 'KRW' then '₩'
        when 'KZT' then '₸'
        when 'LAK' then '₭'
        when 'MNT' then '₮'
        when 'MXN' then 'MX$'
        when 'MYR' then 'RM'
        when 'NGN' then '₦'
        when 'NOK' then 'kr'
        when 'NZD' then 'NZ$'
        when 'PHP' then '₱'
        when 'PLN' then 'zł'
        when 'PYG' then '₲'
        when 'RUB' then '₽'
        when 'SEK' then 'kr'
        when 'SGD' then 'S$'
        when 'THB' then '฿'
        when 'TRY' then '₺'
        when 'TWD' then 'NT$'
        when 'UAH' then '₴'
        when 'USD' then '$'
        when 'VND' then '₫'
        when 'ZAR' then 'R'
            else v_code
        end
    )
    on conflict (code) do nothing;

    select * into v_row from public.currencies where code = v_code;
    return v_row;
end $$;

revoke execute on function public.ensure_currency(text) from public, anon;
grant execute on function public.ensure_currency(text) to authenticated;
