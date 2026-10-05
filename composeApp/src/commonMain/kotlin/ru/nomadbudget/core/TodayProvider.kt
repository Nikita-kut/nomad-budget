package ru.nomadbudget.core

import kotlinx.datetime.LocalDate
import kotlinx.datetime.TimeZone
import kotlinx.datetime.todayIn
import kotlin.time.Clock

fun interface TodayProvider {
    fun today(): LocalDate
}

object SystemToday : TodayProvider {
    override fun today(): LocalDate = Clock.System.todayIn(TimeZone.currentSystemDefault())
}
