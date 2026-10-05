# Nomad Budget

Личный мультивалютный бюджет на Kotlin Multiplatform + Compose Multiplatform, бэкенд Supabase.
Базовая валюта отчётов RUB, набор валют задаётся в таблице `currencies` и подхватывается приложением.

## Модули

- `composeApp` — общий код: UI, домен, данные. Таргеты Android и Web (Kotlin/Wasm).
- `androidApp` — Android-приложение, тонкая обёртка над `composeApp`, плюс виджет «Быстрая заметка» (Glance): текст с рабочего стола попадает во «Входящие» на вкладке Ввод и одним нажатием превращается в операцию.

## Запуск

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web, dev-сервер на http://localhost:8080
./gradlew :androidApp:installDebug                 # Android, нужен эмулятор или устройство
```

## Демо-режим

Приложение без входа и без базы, на вымышленных данных в памяти: полгода зарплатных месяцев, три валюты, накопления, кредиты, план. Изменения не сохраняются.

- Web: добавить `?demo` к адресу, например `http://localhost:8080/?demo`.
- Android: `adb shell am start -n ru.nomadbudget/.android.MainActivity --ez demo true`.

## Проверки

```bash
./gradlew :composeApp:testAndroidHostTest :composeApp:wasmJsBrowserTest
```

## Инфраструктура

- `supabase/` — схема БД как миграции, см. `supabase/README.md`.
- `tools/rates/fetch_rates.py` — обновление курсов, запускается по cron из GitHub Actions.
- `.github/workflows/` — проверка PR, деплой веба на GitHub Pages, курсы.
