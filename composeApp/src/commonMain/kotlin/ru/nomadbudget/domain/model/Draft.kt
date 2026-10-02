package ru.nomadbudget.domain.model

import kotlin.time.Instant

data class Draft(val id: String, val text: String, val createdAt: Instant)

object DraftParser {

    private val amountRegex = Regex("""(\d+(?:[.,]\d+)?)\s*(к|k|тыс\.?|m|м|млн\.?)?""", RegexOption.IGNORE_CASE)

    fun amountText(text: String): String? {
        val match = amountRegex.find(text) ?: return null
        val number = match.groupValues[1].replace(',', '.').toDoubleOrNull() ?: return null
        val multiplier = when (match.groupValues[2].lowercase().trimEnd('.')) {
            "к", "k", "тыс" -> 1_000.0
            "м", "m", "млн" -> 1_000_000.0
            else -> 1.0
        }
        val value = number * multiplier
        return if (value == value.toLong().toDouble()) value.toLong().toString() else value.toString().replace('.', ',')
    }

    fun noteWithoutAmount(text: String): String {
        val match = amountRegex.find(text) ?: return text.trim()
        return text.removeRange(match.range).replace(Regex("\\s{2,}"), " ").trim().trim('-', '—', ':').trim()
    }
}
