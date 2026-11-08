package ru.nomadbudget.presentation.format

fun plural(count: Long, one: String, few: String, many: String): String {
    val absolute = if (count < 0) -count else count
    val lastTwo = absolute % 100
    val last = absolute % 10
    val word = when {
        lastTwo in 11L..14L -> many
        last == 1L -> one
        last in 2L..4L -> few
        else -> many
    }
    return "$count $word"
}

fun plural(count: Int, one: String, few: String, many: String): String = plural(count.toLong(), one, few, many)

fun operations(count: Int): String = plural(count, "операция", "операции", "операций")
