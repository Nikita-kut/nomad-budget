# Staging

Отдельный проект Supabase и сайт https://nikita-kut.github.io/nomad-budget-staging/. Только тестовые данные, реальные данные сюда не попадают.

- Мерж в `main` → workflow «Staging»: миграции на staging-проект, сборка сайта с `NOMAD_ENV=staging`, публикация в репозиторий `nomad-budget-staging`.
- Прод-сайт выкладывается только по тегу `v*` после подтверждения владельца в окружении `production`.

## Тестовые пользователи

`reset.py` удаляет всех пользователей `@example.test` и создаёт заново: `a@`, `b@`, `admin@` с данными, `blocked@` с данными и блокировкой, `fresh@` без данных.

```bash
STAGING_PROJECT_REF=<ref> STAGING_TEST_PASSWORD=<пароль> python3 tools/staging/reset.py
```

Пароль в репозитории не хранится. Без `STAGING_TEST_PASSWORD` он генерируется и выбрасывается.

## Проверка

`smoke.py` через публичный API staging проверяет:
- регистрация без приглашения закрыта;
- аноним не читает данные;
- `a@` и `b@` входят и видят только свои счета;
- `b@` не может переименовать счёт `a@`;
- `blocked@` не входит.

```bash
STAGING_SUPABASE_URL=<url> STAGING_SUPABASE_KEY=<publishable> STAGING_TEST_PASSWORD=<пароль> python3 tools/staging/smoke.py
```
