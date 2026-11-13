#!/usr/bin/env bash
set -euo pipefail
out="$1"
mkdir -p "$out"
: "${PROD_DB_URL:?нужен PROD_DB_URL}"

docker run --rm -e PGURL="$PROD_DB_URL" -v "$out:/out" postgres:17 \
    sh -c 'pg_dump "$PGURL" --data-only --schema=public --no-owner --no-privileges --quote-all-identifiers -f /out/public.sql'

psql "$PROD_DB_URL" -q -v ON_ERROR_STOP=1 -c "\copy (select * from backup.auth_users()) to '$out/auth_users.jsonl'"
psql "$PROD_DB_URL" -q -v ON_ERROR_STOP=1 -c "\copy (select * from backup.auth_identities()) to '$out/auth_identities.jsonl'"
psql "$PROD_DB_URL" -q -v ON_ERROR_STOP=1 -At -f tools/backup/counts.sql > "$out/counts.txt"

test -s "$out/public.sql"
test -s "$out/counts.txt"
echo "Дамп снят: таблиц $(wc -l < "$out/counts.txt")"
