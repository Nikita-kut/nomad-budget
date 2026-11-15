package ru.nomadbudget.presentation.settings

import androidx.compose.ui.test.ExperimentalTestApi
import androidx.compose.ui.test.onFirst
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.performScrollToNode
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.v2.runComposeUiTest
import ru.nomadbudget.domain.model.UserSettings
import ru.nomadbudget.domain.model.Draft
import ru.nomadbudget.features.AppModule
import ru.nomadbudget.features.Feature
import ru.nomadbudget.features.FeatureState
import ru.nomadbudget.features.Stage
import ru.nomadbudget.presentation.home.HomeState
import ru.nomadbudget.presentation.theme.AppTheme
import kotlin.test.Test
import kotlin.test.assertEquals

@OptIn(ExperimentalTestApi::class)
class ModulesScreenUiTest {
    private val saved = mutableListOf<Pair<Set<String>, Set<String>>>()

    private fun androidx.compose.ui.test.ComposeUiTest.tap(tag: String) {
        onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst().performScrollToNode(androidx.compose.ui.test.hasTestTag(tag))
        onNodeWithTag(tag).performClick()
    }

    private fun state(off: Set<String> = emptySet(), blocks: Set<String> = emptySet()) =
        HomeState(
            today = kotlinx.datetime.LocalDate(2026, 11, 15),
            period = ru.nomadbudget.domain.model.SalaryCycle.periodContaining(kotlinx.datetime.LocalDate(2026, 11, 15), 1),
            settings = UserSettings(modulesOff = off, blocksOff = blocks),
        )

    @Test
    fun turningCapitalOnAlsoTurnsCushionOn() = runComposeUiTest {
        setContent { AppTheme { ModulesScreen(state(off = setOf("cushion", "capital")), FeatureState.EVERYTHING, { m, b -> saved += m to b }, onBack = null) } }
        tap(ModulesTags.module(AppModule.CAPITAL))
        waitForIdle()
        assertEquals(emptySet(), saved.single().first)
    }

    @Test
    fun turningCurrenciesOffAlsoTurnsBankWatchOff() = runComposeUiTest {
        setContent { AppTheme { ModulesScreen(state(), FeatureState.EVERYTHING, { m, b -> saved += m to b }, onBack = null) } }
        tap(ModulesTags.module(AppModule.CURRENCIES))
        waitForIdle()
        assertEquals(setOf("currencies", "bank_watch"), saved.single().first)
    }

    @Test
    fun moduleWithDataAsksBeforeTurningOff() = runComposeUiTest {
        val withDrafts = state().copy(drafts = listOf(Draft("d1", "кофе 300", kotlin.time.Instant.parse("2026-11-15T10:00:00Z"))))
        setContent { AppTheme { ModulesScreen(withDrafts, FeatureState.EVERYTHING, { m, b -> saved += m to b }, onBack = null) } }
        tap(ModulesTags.module(AppModule.INBOX))
        waitForIdle()
        assertEquals(emptyList(), saved)
        onNodeWithText("Выключить").performClick()
        waitForIdle()
        assertEquals(setOf("inbox"), saved.single().first)
    }

    @Test
    fun blockCanBeHiddenInsideAnEnabledModule() = runComposeUiTest {
        setContent { AppTheme { ModulesScreen(state(), FeatureState.EVERYTHING, { m, b -> saved += m to b }, onBack = null) } }
        tap(ModulesTags.block(Feature.DEBTS_MONTH_TILE))
        waitForIdle()
        assertEquals(setOf(Feature.DEBTS_MONTH_TILE.key), saved.single().second)
    }

    @Test
    fun ownerOnlyModuleIsNotOfferedToOthers() = runComposeUiTest {
        val stages = FeatureState.EVERYTHING.stages + Feature.entries.filter { it.module == AppModule.CHARTS }.associate { it.key to Stage.OWNER }
        setContent { AppTheme { ModulesScreen(state(), FeatureState(stages = stages), { m, b -> saved += m to b }, onBack = null) } }
        waitForIdle()
        val list = onAllNodes(androidx.compose.ui.test.hasScrollAction()).onFirst()
        list.performScrollToNode(androidx.compose.ui.test.hasTestTag(ModulesTags.module(AppModule.INBOX)))
        kotlin.test.assertFails { list.performScrollToNode(androidx.compose.ui.test.hasTestTag(ModulesTags.module(AppModule.CHARTS))) }
    }
}
