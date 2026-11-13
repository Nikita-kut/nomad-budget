#!/usr/bin/env bash
set -uo pipefail

cd "$(dirname "$0")/../.."

eval "$(supabase status -o env | grep -E '^(DB_URL|API_URL|ANON_KEY)=')"
: "${DB_URL:?supabase status не вернул DB_URL}"
: "${API_URL:?supabase status не вернул API_URL}"
: "${ANON_KEY:?supabase status не вернул ANON_KEY}"

failed=0

psql "$DB_URL" -q -v ON_ERROR_STOP=1 -f supabase/tests/helpers.sql || exit 1

for f in supabase/tests/[0-9]*.sql; do
    echo "== $f"
    if ! psql "$DB_URL" -q -v ON_ERROR_STOP=1 -f "$f"; then
        failed=1
    fi
done

http_code() {
    curl -s -o /dev/null -w '%{http_code}' "$@"
}

echo "== HTTP"
code=$(http_code -X POST "$API_URL/auth/v1/signup" -H "apikey: $ANON_KEY" -H 'Content-Type: application/json' \
    -d '{"email":"stranger@example.test","password":"stranger-password-1"}')
if [[ "$code" == 2* ]]; then
    echo "A5 регистрация без приглашения открыта: $code"
    failed=1
fi

code=$(http_code "$API_URL/rest/v1/accounts?select=id" -H "apikey: $ANON_KEY")
if [[ "$code" == 2* ]]; then
    echo "A5 anon читает accounts через REST: $code"
    failed=1
fi

code=$(http_code -X POST "$API_URL/rest/v1/rpc/delete_own_account" -H "apikey: $ANON_KEY" -H 'Content-Type: application/json' -d '{}')
if [[ "$code" == 2* ]]; then
    echo "A5 anon вызывает delete_own_account через REST: $code"
    failed=1
fi

if [[ $failed -ne 0 ]]; then
    echo "Проверки базы: есть нарушения"
    exit 1
fi
echo "Проверки базы: ok"
