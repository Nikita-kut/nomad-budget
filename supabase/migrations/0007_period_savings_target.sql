-- План «Себе» на зарплатный месяц: сколько отложить в накопления, в минимальных единицах базовой валюты.
alter table public.periods
    add column savings_target bigint check (savings_target is null or savings_target >= 0);
