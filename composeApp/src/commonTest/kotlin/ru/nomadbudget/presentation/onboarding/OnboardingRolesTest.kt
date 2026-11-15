package ru.nomadbudget.presentation.onboarding

import ru.nomadbudget.domain.model.AccountRole
import ru.nomadbudget.features.FeatureGate
import ru.nomadbudget.features.FeatureState
import kotlin.test.AfterTest
import kotlin.test.Test
import kotlin.test.assertEquals

class OnboardingRolesTest {
    @AfterTest
    fun reset() {
        FeatureGate.state = FeatureState.EVERYTHING
    }

    @Test
    fun rolesFollowModules() {
        assertEquals(AccountRole.entries.toList(), onboardingRoles())
        FeatureGate.state = FeatureState.EVERYTHING.copy(modulesOff = setOf("funds"))
        assertEquals(listOf(AccountRole.LIFE, AccountRole.CUSHION).sorted(), onboardingRoles().sorted())
        FeatureGate.state = FeatureState.EVERYTHING.copy(modulesOff = setOf("funds", "cushion"))
        assertEquals(listOf(AccountRole.LIFE), onboardingRoles())
    }

    @Test
    fun everyIntroModuleIsARealModuleAndPagesStartWithWelcome() {
        assertEquals(IntroArt.WELCOME, INTRO_PAGES.first().art)
        assertEquals(IntroArt.HELP, INTRO_PAGES.last().art)
        assertEquals(ru.nomadbudget.features.AppModule.entries.toSet(), INTRO_PAGES.flatMap { it.modules }.toSet())
    }
}
