package dev.isaacru.bolsawidgets.data.repository

import dev.isaacru.bolsawidgets.data.local.dao.MoversDao
import dev.isaacru.bolsawidgets.data.local.entity.CachedMoversEntity
import dev.isaacru.bolsawidgets.data.remote.HttpStatusException
import dev.isaacru.bolsawidgets.data.remote.retryWithBackoff
import dev.isaacru.bolsawidgets.data.remote.yahoo.YahooScreenerApi
import dev.isaacru.bolsawidgets.data.remote.yahoo.toDomain
import dev.isaacru.bolsawidgets.di.IoDispatcher
import dev.isaacru.bolsawidgets.domain.model.MarketMover
import dev.isaacru.bolsawidgets.domain.model.MarketMovers
import dev.isaacru.bolsawidgets.domain.model.MoverDirection
import dev.isaacru.bolsawidgets.domain.repository.MoversRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.async
import kotlinx.coroutines.coroutineScope
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * The day's ranking, cache first.
 *
 * Two requests, one per direction, run together. Either one failing leaves that half on
 * its cached value instead of blanking the screen — the same rule the quotes follow, for
 * the same reason: a list from an hour ago with its timestamp beats an error message.
 */
@Singleton
class MoversRepositoryImpl @Inject constructor(
    private val api: YahooScreenerApi,
    private val moversDao: MoversDao,
    private val json: Json,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : MoversRepository {

    override suspend fun getMovers(maxAge: Duration): MarketMovers = withContext(io) {
        val now = Instant.now(clock)
        val cached = MoverDirection.entries.associateWith { readCache(it) }
        val isFresh = cached.values.all { it != null && Duration.between(it.fetchedAt, now) < maxAge }
        if (isFresh) {
            return@withContext MarketMovers(
                gainers = cached.getValue(MoverDirection.GAINERS)?.movers.orEmpty(),
                losers = cached.getValue(MoverDirection.LOSERS)?.movers.orEmpty(),
                fetchedAt = cached.values.mapNotNull { it?.fetchedAt }.minOrNull(),
            )
        }

        val fetched = coroutineScope {
            val gainers = async { fetch(MoverDirection.GAINERS, now) }
            val losers = async { fetch(MoverDirection.LOSERS, now) }
            mapOf(
                MoverDirection.GAINERS to gainers.await(),
                MoverDirection.LOSERS to losers.await(),
            )
        }

        val resolved = MoverDirection.entries.associateWith { direction ->
            fetched[direction] ?: cached[direction]
        }
        MarketMovers(
            gainers = resolved[MoverDirection.GAINERS]?.movers.orEmpty(),
            losers = resolved[MoverDirection.LOSERS]?.movers.orEmpty(),
            // The older of the two halves, because that is how stale the screen really is.
            fetchedAt = resolved.values.mapNotNull { it?.fetchedAt }.minOrNull(),
        )
    }

    private suspend fun fetch(direction: MoverDirection, now: Instant): CachedRanking? = runCatching {
        retryWithBackoff(attempts = 2) {
            val response = api.screener(screenId = direction.screenId(), count = COUNT)
            if (!response.isSuccessful) {
                throw HttpStatusException(
                    response.code(),
                    "Yahoo screener answered HTTP " + response.code(),
                )
            }
            val movers = response.body()?.finance?.result?.firstOrNull()?.quotes.orEmpty()
                .mapNotNull { it.toDomain() }
            // An empty answer is not a ranking; keeping the previous one is more honest
            // than drawing an empty section.
            if (movers.isEmpty()) throw IllegalStateException("Empty ranking")
            movers
        }
    }.getOrNull()?.let { movers ->
        moversDao.upsert(
            CachedMoversEntity(
                direction = direction.name,
                payloadJson = json.encodeToString(movers.map { it.toWire() }),
                fetchedAtEpochMillis = now.toEpochMilli(),
            ),
        )
        CachedRanking(movers, now)
    }

    private suspend fun readCache(direction: MoverDirection): CachedRanking? {
        val row = moversDao.get(direction.name) ?: return null
        val movers = runCatching {
            json.decodeFromString<List<MoverWire>>(row.payloadJson).map { it.toDomain() }
        }.getOrNull() ?: return null
        return CachedRanking(movers, Instant.ofEpochMilli(row.fetchedAtEpochMillis))
    }

    private fun MoverDirection.screenId(): String = when (this) {
        MoverDirection.GAINERS -> YahooScreenerApi.DAY_GAINERS
        MoverDirection.LOSERS -> YahooScreenerApi.DAY_LOSERS
    }

    private data class CachedRanking(val movers: List<MarketMover>, val fetchedAt: Instant)

    private companion object {
        /** Enough to fill a screen and scroll a little, not enough to be a directory. */
        const val COUNT = 15
    }
}

/** Storage shape of one row of the ranking. */
@Serializable
private data class MoverWire(
    val s: String,
    val n: String,
    val e: String,
    val p: Double,
    val c: String,
    val d: Double,
)

private fun MarketMover.toWire() = MoverWire(
    s = symbol,
    n = name,
    e = exchange,
    p = price,
    c = currency,
    d = changePercent,
)

private fun MoverWire.toDomain() = MarketMover(
    symbol = s,
    name = n,
    exchange = e,
    price = p,
    currency = c,
    changePercent = d,
)
