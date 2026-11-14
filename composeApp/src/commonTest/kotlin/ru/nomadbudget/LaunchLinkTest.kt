package ru.nomadbudget

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class LaunchLinkTest {
    private val code = "AbCdEfGhIjKlMnOpQrStUvWxYz0123456789_-abcde"

    @Test
    fun inviteAndResetAreReadFromTheFragment() {
        assertEquals(LaunchLink.Invite(code), parseLaunchFragment("#invite=$code"))
        assertEquals(LaunchLink.Reset("pkce_0123456789abcdef0123"), parseLaunchFragment("#reset=pkce_0123456789abcdef0123"))
    }

    @Test
    fun garbageAndEmptyFragmentsAreIgnored() {
        assertNull(parseLaunchFragment(""))
        assertNull(parseLaunchFragment("#"))
        assertNull(parseLaunchFragment("#invite="))
        assertNull(parseLaunchFragment("#invite=short"))
        assertNull(parseLaunchFragment("#invite=<script>alert(1)</script>xxxxxxxx"))
        assertNull(parseLaunchFragment("#other=$code"))
    }
}
