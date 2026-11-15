package ru.nomadbudget.presentation.onboarding

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import ru.nomadbudget.domain.model.SalaryCycle
import ru.nomadbudget.domain.model.UserSettings
import ru.nomadbudget.features.AppModule
import ru.nomadbudget.features.Feature
import ru.nomadbudget.features.FeatureState
import ru.nomadbudget.features.Stage
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class IntroScreenUiTest {
    private val today = kotlinx.datetime.LocalDate(2026, 11, 15)
    private fun state(off: Set<String> = emptySet()) = HomeState(today = today, period = SalaryCycle.periodContaining(today, 1), settings = UserSettings(modulesOff = off))
    private val saved = mutableListOf<Set<String>>()

    private fun androidx.compose.ui.test.ComposeUiTest.next(times: Int) = repeat(times) {
        onNodeWithTag(IntroTags.NEXT).performClick()
        waitForIdle()
    }

    @Test
    fun walkingThroughEndsWithSetupForNewUsers() = runComposeUiTest {
        var done = 0
        setContent { AppTheme { IntroScreen(state(), FeatureState.EVERYTHING, firstTime = true, onSetModules = { m, _ -> saved += m }, onDone = { done++ }) } }
        next(INTRO_PAGES.size - 1)
        onNodeWithText("Перейти к настройке").assertExists()
        next(1)
        assertEquals(1, done)
    }

    @Test
    fun capitalTogglePullsCushionAndCushionOffDropsCapital() = runComposeUiTest {
        setContent { AppTheme { IntroScreen(state(off = setOf("cushion", "capital")), FeatureState.EVERYTHING, firstTime = true, onSetModules = { m, _ -> saved += m }, onDone = {}) } }
        next(3)
        onNodeWithTag(IntroTags.module(AppModule.CAPITAL)).performClick()
        waitForIdle()
        assertEquals(emptySet(), saved.last())
    }

    @Test
    fun skipEndsRightAway() = runComposeUiTest {
        var done = 0
        setContent { AppTheme { IntroScreen(state(), FeatureState.EVERYTHING, firstTime = true, onSetModules = { _, _ -> }, onDone = { done++ }) } }
        onNodeWithTag(IntroTags.SKIP).performClick()
        waitForIdle()
        assertEquals(1, done)
    }

    @Test
    fun ownerOnlyModuleIsNotOffered() = runComposeUiTest {
        val stages = Feature.entries.associate { it.key to Stage.ALL } + Feature.entries.filter { it.module == AppModule.DEBTS }.associate { it.key to Stage.OWNER }
        setContent { AppTheme { IntroScreen(state(), FeatureState(stages = stages), firstTime = true, onSetModules = { _, _ -> }, onDone = {}) } }
        next(4)
        onNodeWithText("Кредиты без сюрпризов").assertExists()
        onNodeWithTag(IntroTags.module(AppModule.DEBTS)).assertDoesNotExist()
    }

    @Test
    fun reopenedIntroClosesWithDone() = runComposeUiTest {
        var done = 0
        setContent { AppTheme { IntroScreen(state(), FeatureState.EVERYTHING, firstTime = false, onSetModules = { _, _ -> }, onDone = { done++ }) } }
        next(INTRO_PAGES.size - 1)
        onNodeWithText("Готово").assertExists()
        next(1)
        assertEquals(1, done)
    }
}
