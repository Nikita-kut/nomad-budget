# Nomad Budget

Личный мультивалютный бюджет на Kotlin Multiplatform + Compose Multiplatform, бэкенд Supabase.
Базовая валюта отчётов RUB, остальные валюты пользователь добавляет сам по коду ISO 4217.

## Модули

- `composeApp` — общий код: UI, домен, данные. Таргеты Android и Web (Kotlin/Wasm).
- `androidApp` — Android-приложение, тонкая обёртка над `composeApp`, плюс виджет «Быстрая заметка» (Glance): текст с рабочего стола попадает во «Входящие» в форме новой операции и одним нажатием превращается в операцию.

## Запуск

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web, dev-сервер на http://localhost:8080
./gradlew :androidApp:installDebug                 # Android, нужен эмулятор или устройство
```

## Демо-режим

Приложение без входа и без базы, на вымышленных данных в памяти: полгода месяцев бюджета, три валюты, накопления, кредиты, план. Изменения не сохраняются.

- Web: добавить `?demo` к адресу, например `http://localhost:8080/?demo`.
- Android: `adb shell am start -n ru.nomadbudget/.android.MainActivity --ez demo true`.

## Планирование

Как планировать месяц, накопления и траты из накоплений: [docs/PLANNING.md](docs/PLANNING.md).

## Документы

- [docs/DECISIONS.md](docs/DECISIONS.md) — принятые решения: месяц, остаток, честные цифры, доступ, публичность.
- [docs/ARCHITECTURE.md](docs/ARCHITECTURE.md) — слои и где что лежит.
- [docs/UI_RULES.md](docs/UI_RULES.md) — дизайн-система, тексты, валюты, кнопки.
- [docs/BANK_RATE_WATCH.md](docs/BANK_RATE_WATCH.md) — слежение за курсом банка и уведомления в Telegram.

## Слежение за курсом банка

На экране «Курсы» можно следить за курсом банка по одной паре валют: цель, резкие скачки, ежедневная сводка — в Telegram, вместе с курсом ЦБ и разницей с ним. Проверка каждые 15 минут из GitHub Actions. Настройка бота — [docs/BANK_RATE_WATCH.md](docs/BANK_RATE_WATCH.md).

## Проверки

```bash
bash tools/check_rules.sh
python3 -m unittest tools/rates/test_bank_watch.py
./gradlew :composeApp:testAndroidHostTest :composeApp:wasmJsBrowserTest
```

## Инфраструктура

- `supabase/` — схема БД как миграции, см. `supabase/README.md`.
- `tools/rates/fetch_rates.py` — обновление курсов, запускается по cron из GitHub Actions.
- `tools/rates/bank_watch.py` — слежение за курсом банка и Telegram-бот, cron каждые 15 минут; секрет `TELEGRAM_BOT_TOKEN`.
- `.github/workflows/` — проверка PR, деплой веба на GitHub Pages, курсы, слежение за курсом банка.
