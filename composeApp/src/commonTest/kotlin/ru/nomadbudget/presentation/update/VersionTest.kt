package ru.nomadbudget.presentation.update

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull
import kotlin.test.assertTrue

class VersionTest {
    private fun v(raw: String) = Version.parse(raw)!!

    @Test
    fun parsesTagsAndRejectsGarbage() {
        assertEquals(Version(0, 35, 0), Version.parse("v0.35.0"))
        assertEquals(Version(1, 2, 3), Version.parse(" 1.2.3 "))
        assertNull(Version.parse("1.2"))
        assertNull(Version.parse("1.2.x"))
        assertNull(Version.parse(null))
    }

    @Test
    fun comparesNumericallyNotAsText() {
        assertTrue(v("0.10.0") > v("0.9.9"))
        assertTrue(v("1.0.0") > v("0.99.99"))
        assertTrue(v("0.35.1") > v("0.35.0"))
    }

    @Test
    fun updateStateCoversEveryCase() {
        val info = VersionInfo(min = v("0.34.0"), latest = v("0.36.0"), apkUrl = null)
        assertEquals(UpdateState.Required(v("0.34.0")), updateState(v("0.33.9"), info))
        assertEquals(UpdateState.Available(v("0.36.0")), updateState(v("0.35.0"), info))
        assertEquals(UpdateState.Fine, updateState(v("0.36.0"), info))
        assertEquals(UpdateState.Fine, updateState(v("0.37.0"), info))
        assertEquals(UpdateState.Fine, updateState(v("0.0.0"), info))
        assertEquals(UpdateState.Fine, updateState(v("0.30.0"), null))
        assertEquals(UpdateState.Fine, updateState(null, info))
    }
}
