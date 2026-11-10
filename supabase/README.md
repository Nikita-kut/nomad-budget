# Supabase

Схема БД как код. Миграции применяются по порядку номеров.

| Файл | Что |
|---|---|
| `migrations/0001_init.sql` | справочники, типы, таблицы, индексы, RLS-политики, вьюхи агрегатов |
| `migrations/0002_defaults_for_new_user.sql` | триггер: новому пользователю создаются стартовые счета, категории, подкатегории |
| `migrations/0003…0011` | корректировки, подкатегории плана, кредиты, цели, капитал, нейтральный seed |
| `migrations/0012_user_settings.sql` | `user_settings` (начало месяца бюджета, дни дохода, базовая валюта, подушка, онбординг), триггер без стартовых счетов, RPC `delete_own_account` и `ensure_currency` |
| `migrations/0013_bank_rate_watch.sql` | слежение за курсом банка: `bank_rate_snapshots` (общие снимки, пишет воркер), `rate_watches` (настройки владельца, `state` и `link_code` ведёт воркер), `telegram_subscribers`, `bot_info`, почасовая вьюха `bank_rate_snapshots_hourly` |
| `migrations/0014_watch_trigger.sql` | pg_cron + pg_net: запуск проверки курса каждые 15 минут через `workflow_dispatch` |
| `migrations/0015_watch_windows.sql` | окна слежения: `rate_watches.good_percentile / improve_percent / trade_from / trade_to`, таблица `exchange_holidays` (неторговые дни биржи, обновлять раз в год), `bank_rate_snapshots.in_hours` с заполнением истории, функция `exchange_in_hours`, сброс `state` |

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

- Суммы в `bigint`, в минимальных единицах валюты. `currencies.minor_units` говорит, сколько знаков после запятой у валюты.
- В миграциях только RUB и USD. Другие валюты пользователь добавляет сам из приложения через RPC `ensure_currency` (белый список ISO 4217); скрипт курсов подхватывает их на следующем запуске.
- Базовая валюта RUB. У транзакции хранятся `rate_to_base` и `amount_base` на дату записи, задним числом не пересчитываются.
- `exchange_rates` общая таблица, пишет только сервисный ключ из GitHub Actions, читают все.
- `bank_rate_snapshots` и `bot_info` — тоже общие, пишет воркер слежения за курсом. В `rate_watches` владелец меняет настройки, а колонки `state` и `link_code` — только воркер (column-level grant).
- Обмен валюты — `type = 'exchange'`: `account_id` / `amount` что отдал, `counter_account_id` / `counter_amount` что получил. Фактический курс = отношение сумм.
- Перевод в накопления — `type = 'transfer'` на счёт с `is_savings = true`. В расходы не попадает.
- `user_id` заполняется по умолчанию из `auth.uid()`, клиент его не передаёт. RLS на всех таблицах: строка видна только владельцу.
- Вьюхи с `security_invoker = true`, чтобы RLS базовых таблиц действовал и через них.
