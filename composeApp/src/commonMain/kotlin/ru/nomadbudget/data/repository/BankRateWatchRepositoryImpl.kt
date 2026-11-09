package ru.nomadbudget.data.repository

import io.github.jan.supabase.SupabaseClient
import io.github.jan.supabase.postgrest.from
import io.github.jan.supabase.postgrest.query.Order
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.serialization.builtins.ListSerializer
import ru.nomadbudget.data.dto.BankRateSnapshotDto
import ru.nomadbudget.data.dto.BankRateWatchDto
import ru.nomadbudget.data.dto.BotInfoDto
import ru.nomadbudget.data.dto.TelegramSubscriberDto
import ru.nomadbudget.data.local.OfflineCache
import ru.nomadbudget.data.mapper.BankRateWatchMapper
import ru.nomadbudget.domain.model.BankRateCategory
import ru.nomadbudget.domain.model.BankRateSnapshot
import ru.nomadbudget.domain.model.BankRateWatch
import ru.nomadbudget.domain.model.BankRateWatchDraft
import ru.nomadbudget.domain.model.BankWatchData
import ru.nomadbudget.domain.model.Currency
import ru.nomadbudget.domain.repository.BankRateWatchRepository
import kotlin.time.Clock
import kotlin.time.Duration.Companion.days

class BankRateWatchRepositoryImpl(
    private val client: SupabaseClient,
    private val currencies: ru.nomadbudget.domain.repository.CurrencyRepository,
    private val cache: OfflineCache,
) : BankRateWatchRepository {

    override suspend fun load(currencies: List<Currency>): BankWatchData {
        val byCode = currencies.associateBy { it.code }
        val watch = cache.throughOrDefault(KEY_WATCH, ListSerializer(BankRateWatchDto.serializer()), emptyList()) {
            client.from(WATCHES).select { order("created_at", Order.ASCENDING); limit(1) }.decodeList<BankRateWatchDto>()
        }.firstOrNull()?.let { BankRateWatchMapper.toDomain(it, byCode) } ?: return BankWatchData(botUsername = botUsername())
        return coroutineScope {
            val history = async { history(watch) }
            val latest = async { latest(watch) }
            val subscribers = async { subscribers(watch.id) }
            val bot = async { botUsername() }
            BankWatchData(watch, history.await(), latest.await(), subscribers.await(), bot.await())
        }
    }

    override suspend fun create(draft: BankRateWatchDraft): BankRateWatch {
        val byCode = currencies.byCode()
        val row = client.from(WATCHES).insert(BankRateWatchMapper.toUpsert(draft)) { select() }.decodeSingle<BankRateWatchDto>()
        return requireNotNull(BankRateWatchMapper.toDomain(row, byCode)) { "Неизвестная валюта пары" }
    }

    override suspend fun update(id: String, draft: BankRateWatchDraft): BankRateWatch {
        val byCode = currencies.byCode()
        val row = client.from(WATCHES).update(BankRateWatchMapper.toUpsert(draft)) {
            filter { eq("id", id) }
            select()
        }.decodeSingle<BankRateWatchDto>()
        return requireNotNull(BankRateWatchMapper.toDomain(row, byCode)) { "Неизвестная валюта пары" }
    }

    override suspend fun setEnabled(id: String, enabled: Boolean) {
        client.from(WATCHES).update({ set("enabled", enabled) }) { filter { eq("id", id) } }
    }

    override suspend fun delete(id: String) {
        client.from(WATCHES).delete { filter { eq("id", id) } }
    }

    override suspend fun removeSubscriber(id: String) {
        client.from(SUBSCRIBERS).delete { filter { eq("id", id) } }
    }

    private suspend fun history(watch: BankRateWatch): List<BankRateSnapshot> {
        val since = (Clock.System.now() - HISTORY_DAYS.days).toString()
        return cache.throughOrDefault(KEY_HISTORY, ListSerializer(BankRateSnapshotDto.serializer()), emptyList()) {
            client.from(HOURLY_VIEW)
                .select {
                    filter {
                        eq("provider", watch.provider)
                        eq("from_currency", watch.from.code)
                        eq("to_currency", watch.to.code)
                        eq("category", watch.category.id)
                        gte("taken_at", since)
                    }
                    order("taken_at", Order.ASCENDING)
                    limit(HISTORY_ROWS)
                }
                .decodeList<BankRateSnapshotDto>()
        }.mapNotNull(BankRateWatchMapper::toDomain)
    }

    private suspend fun latest(watch: BankRateWatch): Map<BankRateCategory, BankRateSnapshot> = coroutineScope {
        BankRateCategory.entries.map { category ->
            async {
                val row = cache.throughOrDefault("$KEY_LATEST:${category.id}", ListSerializer(BankRateSnapshotDto.serializer()), emptyList()) {
                    client.from(SNAPSHOTS)
                        .select {
                            filter {
                                eq("provider", watch.provider)
                                eq("from_currency", watch.from.code)
                                eq("to_currency", watch.to.code)
                                eq("category", category.id)
                            }
                            order("taken_at", Order.DESCENDING)
                            limit(1)
                        }
                        .decodeList<BankRateSnapshotDto>()
                }.firstOrNull()?.let(BankRateWatchMapper::toDomain)
                category to row
            }
        }.mapNotNull { deferred -> deferred.await().let { (category, snapshot) -> snapshot?.let { category to it } } }.toMap()
    }

    private suspend fun subscribers(watchId: String) = cache.throughOrDefault(KEY_SUBSCRIBERS, ListSerializer(TelegramSubscriberDto.serializer()), emptyList()) {
        client.from(SUBSCRIBERS).select { filter { eq("watch_id", watchId) }; order("created_at", Order.ASCENDING) }.decodeList<TelegramSubscriberDto>()
    }.map(BankRateWatchMapper::toDomain)

    private suspend fun botUsername(): String? = cache.throughOrDefault(KEY_BOT, ListSerializer(BotInfoDto.serializer()), emptyList()) {
        client.from(BOT_INFO).select { limit(1) }.decodeList<BotInfoDto>()
    }.firstOrNull()?.username?.takeIf { it.isNotBlank() }

    private companion object {
        const val WATCHES = "rate_watches"
        const val SNAPSHOTS = "bank_rate_snapshots"
        const val HOURLY_VIEW = "bank_rate_snapshots_hourly"
        const val SUBSCRIBERS = "telegram_subscribers"
        const val BOT_INFO = "bot_info"
        const val KEY_WATCH = "bank_watch"
        const val KEY_HISTORY = "bank_watch_history"
        const val KEY_LATEST = "bank_watch_latest"
        const val KEY_SUBSCRIBERS = "bank_watch_subscribers"
        const val KEY_BOT = "bank_watch_bot"
        const val HISTORY_DAYS = 30
        const val HISTORY_ROWS: Long = 800
    }
}
