#!/usr/bin/env bash
set -euo pipefail
dump="$1"
: "${RECIPIENT:?нужен BACKUP_AGE_RECIPIENT}"
: "${DEPLOY_KEY:?нужен BACKUP_DEPLOY_KEY}"
keep_days=30
stamp=$(date -u +%Y-%m-%d)

mkdir -p ~/.ssh
echo "$DEPLOY_KEY" > ~/.ssh/backup_deploy
chmod 600 ~/.ssh/backup_deploy
ssh-keyscan -t ed25519 github.com >> ~/.ssh/known_hosts 2>/dev/null
export GIT_SSH_COMMAND="ssh -i ~/.ssh/backup_deploy -o IdentitiesOnly=yes"

repo="$RUNNER_TEMP/backups"
git clone -q --depth 1 git@github.com:Nikita-kut/nomad-budget-backups.git "$repo"
mkdir -p "$repo/dumps"
tar -C "$dump" -czf - public.sql auth_users.jsonl auth_identities.jsonl counts.txt | age -r "$RECIPIENT" > "$repo/dumps/$stamp.tar.gz.age"
rm -rf "$dump"

cutoff=$(date -u -d "-$keep_days days" +%Y-%m-%d)
for f in "$repo"/dumps/*.tar.gz.age; do
    name=$(basename "$f" .tar.gz.age)
    if [[ "$name" < "$cutoff" ]]; then
        rm -f "$f"
    fi
done

cat > "$repo/README.md" <<MD
# Nomad Budget — бэкапы

Ночные дампы прода, зашифрованы age, хранятся $keep_days дней. История ветки не копится: каждую ночь — один коммит с актуальными файлами, поэтому удалённый дамп не остаётся в истории.

Расшифровать (ключ только у владельца): \`age -d -i ~/nomad-backup.age.key dumps/ДАТА.tar.gz.age | tar -xzf -\`
MD

cd "$repo"
git config user.name "nomad-budget-ci"
git config user.email "nomad-budget-ci@users.noreply.github.com"
git checkout -q --orphan snapshot
git add -A
git commit -q -m "Backups on $stamp: $(ls dumps | wc -l) dumps"
git push -q -f origin snapshot:main
echo "Сохранено: dumps/$stamp.tar.gz.age, всего дампов $(ls dumps | wc -l)"
