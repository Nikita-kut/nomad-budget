# Nomad Budget

Личный мультивалютный бюджет на Kotlin Multiplatform + Compose Multiplatform, бэкенд Supabase.
Базовая валюта отчётов RUB, набор валют задаётся в таблице `currencies` и подхватывается приложением.

## Модули

- `composeApp` — общий код: UI, домен, данные. Таргеты Android и Web (Kotlin/Wasm).
- `androidApp` — Android-приложение, тонкая обёртка над `composeApp`.

## Запуск

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web, dev-сервер на http://localhost:8080
./gradlew :androidApp:installDebug                 # Android, нужен эмулятор или устройство
```

## Проверки

```bash
./gradlew :composeApp:testAndroidHostTest :composeApp:wasmJsBrowserTest
```

## Инфраструктура

- `supabase/` — схема БД как миграции, см. `supabase/README.md`.
- `tools/rates/fetch_rates.py` — обновление курсов, запускается по cron из GitHub Actions.
- `.github/workflows/` — проверка PR, деплой веба на GitHub Pages, курсы.
