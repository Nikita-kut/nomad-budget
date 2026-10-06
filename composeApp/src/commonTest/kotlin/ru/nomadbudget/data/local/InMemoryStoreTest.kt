package ru.nomadbudget.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class InMemoryStoreTest {

    @Test
    fun clear_removesEverything() {
        val store = InMemoryStore()
        store.put("cache", "data")
        store.put("drafts", "notes")
        store.clear()
        assertNull(store.get("cache"))
        assertNull(store.get("drafts"))
        assertEquals(0, DraftsStore(store).count())
    }
}
