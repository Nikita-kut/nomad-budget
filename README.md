# Nomad Budget

Личный мультивалютный бюджет (RUB / USD / VND) на Kotlin Multiplatform + Compose Multiplatform, бэкенд Supabase.

## Модули

- `composeApp` — общий код: UI, домен, данные. Таргеты Android и Web (Kotlin/Wasm).
- `androidApp` — Android-приложение, тонкая обёртка над `composeApp`.

## Запуск

```bash
./gradlew :composeApp:wasmJsBrowserDevelopmentRun   # web, dev-сервер на http://localhost:8080
./gradlew :androidApp:installDebug                 # Android, нужен эмулятор или устройство
```
