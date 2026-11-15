package ru.nomadbudget.features

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class FeaturesTest {
    private val allSets: List<Set<String>> = (0 until (1 shl AppModule.entries.size)).map { mask ->
        AppModule.entries.filterIndexed { i, _ -> mask and (1 shl i) != 0 }.map { it.key }.toSet()
    }

    @Test
    fun keysAreUniqueAndWellFormed() {
        val keys = Feature.entries.map { it.key }
        assertEquals(keys.size, keys.toSet().size)
        keys.forEach { assertTrue(Regex("^[a-z_]+\\.[a-z_]+$").matches(it), it) }
        Feature.entries.forEach { f -> f.module?.let { assertTrue(f.key.startsWith(it.key + "."), f.key) } }
        AppModule.entries.forEach { m -> assertTrue(Feature.entries.any { it.module == m }, "у модуля ${m.key} нет блоков") }
    }

    @Test
    fun dependenciesAreClosedForEveryOffSet() {
        allSets.forEach { off ->
            val on = enabledModules(off)
            on.forEach { module -> assertTrue(module.requires.all { it in on }, "$off: ${module.key} включён без зависимостей") }
            AppModule.entries.filter { it.key !in off && it.requires.all { r -> r in on } }.forEach { assertTrue(it in on, "$off: ${it.key}") }
        }
    }

    @Test
    fun turningOnPullsDependenciesAndTurningOffDropsDependents() {
        val on = enabledModules(withModule(setOf("cushion", "capital"), AppModule.CAPITAL, on = true))
        assertTrue(AppModule.CAPITAL in on && AppModule.CUSHION in on)
        val off = enabledModules(withModule(emptySet(), AppModule.CURRENCIES, on = false))
        assertFalse(AppModule.CURRENCIES in off)
        assertFalse(AppModule.BANK_WATCH in off)
    }

    @Test
    fun visibilityIsStageTimesModuleTimesPersonalChoiceForEveryCombination() {
        val stages = listOf(null) + Stage.entries
        Feature.entries.forEach { feature ->
            stages.forEach { stage ->
                listOf(true, false).forEach { admin ->
                    listOf(true, false).forEach { hidden ->
                        listOf(emptySet(), feature.module?.let { setOf(it.key) } ?: emptySet()).forEach { off ->
                            val state = FeatureState(
                                stages = stage?.let { mapOf(feature.key to it) } ?: emptyMap(),
                                isAdmin = admin,
                                modulesOff = off,
                                blocksOff = if (hidden) setOf(feature.key) else emptySet(),
                            )
                            val stageOk = stage == Stage.ALL || (stage == Stage.OWNER && admin)
                            val moduleOk = feature.module == null || feature.module.key !in off
                            assertEquals(stageOk && moduleOk && !hidden, state.isOn(feature), "$feature $stage admin=$admin hidden=$hidden off=$off")
                        }
                    }
                }
            }
        }
    }

    @Test
    fun unknownStageHidesAndOwnerStageHidesFromOthers() {
        assertFalse(FeatureState().isOn(Feature.CHARTS_SCREEN))
        val owner = FeatureState(stages = mapOf(Feature.CHARTS_SCREEN.key to Stage.OWNER))
        assertFalse(owner.isOn(Feature.CHARTS_SCREEN))
        assertTrue(owner.copy(isAdmin = true).isOn(Feature.CHARTS_SCREEN))
    }

    @Test
    fun moduleWithDisabledDependencyIsOffEvenIfNotListed() {
        val state = FeatureState.EVERYTHING.copy(modulesOff = setOf("currencies"))
        assertFalse(state.isOn(Feature.BANK_WATCH_RATES))
        assertFalse(state.isOn(AppModule.BANK_WATCH))
    }
}
