# Supabase

Схема БД как код. Миграции применяются по порядку номеров.

| Файл | Что |
|---|---|
| `migrations/0001_init.sql` | справочники, типы, таблицы, индексы, RLS-политики, вьюхи агрегатов |
| `migrations/0002_defaults_for_new_user.sql` | триггер: новому пользователю создаются стартовые счета, категории, подкатегории |

## Применить

Вариант 1, без установки чего-либо: Supabase Dashboard → SQL Editor → вставить содержимое файла → Run. Строго по порядку.

Вариант 2, Supabase CLI:

```bash
brew install supabase/tap/supabase
supabase login
supabase link --project-ref <ref>
supabase db push
```

## Принципы схемы

- Суммы в `bigint`, в минимальных единицах валюты. `currencies.minor_units` говорит, сколько знаков: RUB 2, USD 2, VND 0.
- Базовая валюта RUB. У транзакции хранятся `rate_to_base` и `amount_base` на дату записи, задним числом не пересчитываются.
- `exchange_rates` общая таблица, пишет только сервисный ключ из GitHub Actions, читают все.
- Обмен валюты — `type = 'exchange'`: `account_id` / `amount` что отдал, `counter_account_id` / `counter_amount` что получил. Фактический курс = отношение сумм.
- Перевод в накопления — `type = 'transfer'` на счёт с `is_savings = true`. В расходы не попадает.
- `user_id` заполняется по умолчанию из `auth.uid()`, клиент его не передаёт. RLS на всех таблицах: строка видна только владельцу.
- Вьюхи с `security_invoker = true`, чтобы RLS базовых таблиц действовал и через них.
