package ru.nomadbudget.data.local

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertNull

class UserScopedStoreTest {

    private val base = InMemoryStore()
    private var userId: String? = "alice"
    private val store = UserScopedStore(base) { userId }

    @Test
    fun put_prefixesKeyWithUserId() {
        store.put("accounts", "[1]")
        assertEquals("[1]", base.get("u:alice:accounts"))
        assertEquals("[1]", store.get("accounts"))
        assertNull(base.get("accounts"))
    }

    @Test
    fun otherUser_doesNotSeeData() {
        store.put("pending_transactions", "[tx]")
        userId = "bob"
        assertNull(store.get("pending_transactions"))
        assertEquals(0, PendingQueue(store).size)
        userId = "alice"
        assertEquals("[tx]", store.get("pending_transactions"))
    }

    @Test
    fun signedOut_usesAnonymousScope() {
        store.put("drafts", "a")
        userId = null
        assertNull(store.get("drafts"))
        store.put("drafts", "anon")
        assertEquals("anon", base.get("u:anon:drafts"))
        assertEquals("a", base.get("u:alice:drafts"))
    }

    @Test
    fun clear_removesOnlyOwnKeys_andKeepsDeviceKeys() {
        base.put("theme", "dark")
        store.put("accounts", "[1]")
        store.put("accounts:t", "123")
        userId = "bob"
        store.put("accounts", "[2]")
        userId = "alice"
        store.clear()
        assertNull(store.get("accounts"))
        assertNull(store.get("accounts:t"))
        assertEquals("dark", base.get("theme"))
        assertEquals("[2]", base.get("u:bob:accounts"))
    }

    @Test
    fun keys_returnsUnprefixedKeysOfCurrentUser() {
        store.put("lines:1", "a")
        store.put("lines:2", "b")
        store.put("rates", "c")
        userId = "bob"
        store.put("lines:9", "z")
        userId = "alice"
        assertEquals(listOf("lines:1", "lines:2"), store.keys("lines:").sorted())
        assertEquals(listOf("lines:1", "lines:2", "rates"), store.keys("").sorted())
    }

    @Test
    fun drafts_fromDeviceInbox_moveIntoUserScope() {
        val inbox = InMemoryStore()
        DraftsStore(inbox).add("кофе 300")
        val drafts = DraftsStore(store, inbox = inbox)
        assertEquals(1, drafts.count())
        assertNull(inbox.get(DraftsStore.KEY))
        assertEquals("кофе 300", drafts.all().single().text)
        userId = "bob"
        assertEquals(0, DraftsStore(store, inbox = inbox).count())
    }
}
