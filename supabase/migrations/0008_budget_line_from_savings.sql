-- Строка плана оплачивается из накоплений, а не из дохода месяца.
alter table public.budget_lines
    add column from_savings boolean not null default false;
