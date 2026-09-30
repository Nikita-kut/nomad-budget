-- Плановое досрочное погашение сверх обязательного платежа, в минимальных единицах валюты кредита.
alter table public.debts
    add column extra_payment bigint not null default 0 check (extra_payment >= 0);
