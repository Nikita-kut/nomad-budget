#!/usr/bin/env bash
set -u
ROOT="$(cd "$(dirname "$0")/.." && pwd)"
SRC="$ROOT/composeApp/src/commonMain/kotlin/ru/nomadbudget"
fail=0

rule() {
    local name="$1" pattern="$2" allow="$3"
    local hits
    hits=$(grep -rnE --include='*.kt' "$pattern" "$SRC" | grep -vE "${allow:-^\$}" || true)
    if [ -n "$hits" ]; then
        echo "✗ $name"
        echo "$hits" | sed "s|$ROOT/||" | head -20
        fail=1
    fi
}

rule "Material icons: use AppIcons (Lucide)" 'androidx\.compose\.material\.icons' ''
rule "Third-party chart libraries: use presentation/charts/Charts.kt" 'patrykandpatrick|vico' ''
rule "Literal colors outside the theme" 'Color\(0x[0-9A-Fa-f]+\)' '/presentation/theme/'
rule "FontWeight.Bold: use SemiBold" 'FontWeight\.Bold' '/presentation/theme/'
rule "Raw LazyColumn on screens: use CenteredLazyColumn" '(^|[^A-Za-z])LazyColumn\(' '/components/CenteredLazyColumn\.kt|/journal/JournalScreen\.kt'
rule "Not-null assertion !!" '[A-Za-z0-9_)\]]!!' ''
rule "println in app code" 'println\(' ''
rule "Ruble sign written by hand: format amounts with MoneyFormat" '"[^"]*[0-9] ?₽[^"]*"' '/presentation/format/|/domain/model/IsoCurrencies\.kt|/demo/'
rule "Hover-only hint: say «нажми или наведи»" '"[Нн]аведи[^"]*"' ''
rule "Obsolete terms in texts" '"[^"]*([Нн]акоплени[ея]|[Сс]лить|[Дд]ельта|опер\.|заполни таблицу курсов|Планирование)[^"]*"' '/presentation/components/Hints\.kt:.*гибкие'
rule "Text built from currency symbol next to a number: use MoneyFormat" '\$\{[^}]*\}[ ]?\$\{[^}]*\.symbol\}' '/presentation/format/'
rule "Raw Material buttons: use PrimaryButton, SecondaryButton, AddTile or TextButton" '(^|[^A-Za-z.])(Button|OutlinedButton|FilledTonalButton|ElevatedButton)\(' '/components/Buttons\.kt'
rule "Add actions as plain buttons: use AddTile" '(PrimaryButton|SecondaryButton)\([^)]*\)[^{]*\{ *Text\("Добавить [а-яё]' ''
rule "Domain imports other layers" '^import ru\.nomadbudget\.(presentation|data|di|demo)\.' '^[^:]*/(presentation|data|di|demo)/|/App\.kt'
rule "Presentation imports data" '^import ru\.nomadbudget\.data\.' '^[^:]*/(data|di|demo)/|/App\.kt'
rule "Data imports presentation" '^import ru\.nomadbudget\.presentation\.' '^[^:]*/(presentation|di|demo)/|/App\.kt'
rule "Named colors outside the theme" 'Color\.(White|Black|Gray|Red|Green|Blue|Yellow|LightGray|DarkGray)' '/presentation/theme/|/components/AppIcons\.kt'
rule "Own copies of format helpers: use presentation/format" 'fun (majorText|shortMonth|periodLabel|thousands|monthYear)\(' '/presentation/format/'

if [ "$fail" -ne 0 ]; then
    echo
    echo "Rules are described in docs/UI_RULES.md, docs/DECISIONS.md and docs/ARCHITECTURE.md."
    exit 1
fi
echo "✓ rules ok"
