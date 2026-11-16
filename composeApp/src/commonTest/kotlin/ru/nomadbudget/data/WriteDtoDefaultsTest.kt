package ru.nomadbudget.data

import kotlinx.serialization.json.Json
import kotlinx.serialization.json.JsonObject
import kotlinx.serialization.json.jsonObject
import ru.nomadbudget.data.dto.TransactionInsertDto
import ru.nomadbudget.data.dto.UserSettingsDto
import kotlin.test.Test
import kotlin.test.assertFalse
import kotlin.test.assertTrue

class WriteDtoDefaultsTest {
    private val json = Json

    private fun keys(value: JsonObject) = value.keys

    @Test
    fun emptyModuleAndCurrencyListsAreSentSoTheLastSwitchedOffModuleCanBeTurnedBackOn() {
        val dto = UserSettingsDto(1, emptyList(), "RUB", 6, 0.0, null)
        val sent = keys(json.encodeToJsonElement(UserSettingsDto.serializer(), dto).jsonObject)
        assertTrue("modules_off" in sent)
        assertTrue("blocks_off" in sent)
        assertTrue("currencies" in sent)
    }

    @Test
    fun clearedTransactionFieldsAreSentSoAnEditCanRemoveThem() {
        val dto = TransactionInsertDto(
            txDate = "2026-11-01", type = "expense", accountId = "a", amount = 100,
            rateToBase = 1.0, amountBase = 100, rateSource = "manual",
        )
        val sent = keys(json.encodeToJsonElement(TransactionInsertDto.serializer(), dto).jsonObject)
        listOf("counter_account_id", "counter_amount", "category_id", "subcategory_id", "debt_id", "note").forEach {
            assertTrue(it in sent, it)
        }
        assertFalse("id" in sent)
    }
}
