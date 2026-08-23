package dev.isaacru.bolsawidgets.data.repository

import dev.isaacru.bolsawidgets.data.local.dao.FxRateDao
import dev.isaacru.bolsawidgets.data.local.dao.QuoteCacheDao
import dev.isaacru.bolsawidgets.data.local.toCacheEntity
import dev.isaacru.bolsawidgets.data.local.toDomain
import dev.isaacru.bolsawidgets.data.local.toEntity
import dev.isaacru.bolsawidgets.di.IoDispatcher
import dev.isaacru.bolsawidgets.domain.calc.CurrencyConverter
import dev.isaacru.bolsawidgets.domain.model.Candle
import dev.isaacru.bolsawidgets.domain.model.CandleInterval
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.FxRate
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import dev.isaacru.bolsawidgets.domain.provider.QuoteProvider
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.RefreshOutcome
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.time.Clock
import java.time.Duration
import java.time.Instant
import javax.inject.Inject
import javax.inject.Singleton

/**
 * Cache-first market data.
 *
 * Reads never touch the network: they observe Room, which is what makes the widgets keep
 * working in airplane mode. Writes only happen on success, so a failed refresh silently
 * leaves the previous value and its timestamp in place.
 */
@Singleton
class QuoteRepositoryImpl @Inject constructor(
    private val quoteCacheDao: QuoteCacheDao,
    private val fxRateDao: FxRateDao,
    private val providers: Map<ProviderId, @JvmSuppressWildcards QuoteProvider>,
    private val settingsRepository: SettingsRepository,
    private val clock: Clock,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : QuoteRepository {

    override fun observeQuotes(): Flow<Map<String, Quote>> =
        quoteCacheDao.observeAll().map { rows ->
            rows.associate { it.symbol.uppercase() to it.toDomain() }
        }

    override fun observeQuote(symbol: String): Flow<Quote?> {
        val normalized = symbol.uppercase()
        return quoteCacheDao.observeBySymbols(listOf(normalized))
            .map { rows -> rows.firstOrNull()?.toDomain() }
    }

    override fun observeConverter(): Flow<CurrencyConverter> =
        fxRateDao.observeAll().map { rows -> CurrencyConverter(rows.map { it.toDomain() }) }

    override suspend fun getCachedQuotes(symbols: List<String>): Map<String, Quote> =
        withContext(io) {
            if (symbols.isEmpty()) {
                emptyMap()
            } else {
                quoteCacheDao.getBySymbols(symbols.map { it.uppercase() })
                    .associate { it.symbol.uppercase() to it.toDomain() }
            }
        }

    override suspend fun refreshQuotes(symbols: List<String>): RefreshOutcome = withContext(io) {
        val now = Instant.now(clock)
        val requested = symbols.map { it.uppercase() }.distinct()
        if (requested.isEmpty()) return@withContext RefreshOutcome.nothingToDo(now)

        val fetched = runCatching { activeProvider().getQuotes(requested) }.getOrDefault(emptyList())
        if (fetched.isNotEmpty()) {
            quoteCacheDao.upsertAll(fetched.map { it.toCacheEntity(now) })
        }

        val updated = fetched.map { it.symbol.uppercase() }
        RefreshOutcome(
            requested = requested,
            updated = updated,
            failed = requested - updated.toSet(),
            finishedAt = now,
        )
    }

    override suspend fun refreshFxRates(
        currencies: Set<String>,
        maxAge: Duration,
    ): RefreshOutcome = withContext(io) {
        val now = Instant.now(clock)
        val targets = currencies
            .map { it.uppercase() }
            .filterNot { it == CurrencyConverter.EUR || it.isBlank() }
            .distinct()
        if (targets.isEmpty()) return@withContext RefreshOutcome.nothingToDo(now)

        val cached = fxRateDao.getAll().associateBy { it.pair }
        val stale = targets.filter { currency ->
            val row = cached[FxRate.pairOf(currency, CurrencyConverter.EUR)]
            row == null ||
                Duration.between(Instant.ofEpochMilli(row.fetchedAtEpochMillis), now) >= maxAge
        }
        if (stale.isEmpty()) return@withContext RefreshOutcome.nothingToDo(now)

        val provider = activeProvider()
        val refreshed = stale.mapNotNull { currency ->
            runCatching { provider.getFxRate(currency, CurrencyConverter.EUR) }
                .getOrNull()
                ?.takeIf { it > 0.0 }
                ?.let { rate ->
                    FxRate(FxRate.pairOf(currency, CurrencyConverter.EUR), rate, now)
                }
        }
        if (refreshed.isNotEmpty()) {
            fxRateDao.upsertAll(refreshed.map { it.toEntity() })
        }

        val updated = refreshed.map { it.pair }
        RefreshOutcome(
            requested = stale.map { FxRate.pairOf(it, CurrencyConverter.EUR) },
            updated = updated,
            failed = stale.map { FxRate.pairOf(it, CurrencyConverter.EUR) } - updated.toSet(),
            finishedAt = now,
        )
    }

    override suspend fun getCandles(
        symbol: String,
        range: ChartRange,
        interval: CandleInterval,
    ): List<Candle> = withContext(io) {
        activeProvider().getCandles(symbol.uppercase(), range, interval)
    }

    override suspend fun resolveSymbol(symbol: String): Quote? = withContext(io) {
        val normalized = symbol.trim().uppercase()
        if (normalized.isEmpty()) return@withContext null

        val quote = runCatching { activeProvider().getQuote(normalized) }.getOrNull()
            ?: return@withContext null
        // Cache it right away so the symbol shows a price the moment it is added.
        quoteCacheDao.upsertAll(listOf(quote.toCacheEntity(Instant.now(clock))))
        // A price the app cannot convert is worth nothing to a EUR portfolio, so the
        // symbol brings its FX rate with it. Best effort: the quote stands either way,
        // and the hourly cap still applies.
        runCatching { refreshFxRates(setOf(quote.currency), FX_MAX_AGE) }
        quote
    }

    /**
     * The provider chosen in Ajustes, falling back to Yahoo when the stored one cannot
     * run (the Twelve Data stub has no API key, for instance).
     */
    private suspend fun activeProvider(): QuoteProvider {
        val selected = providers[settingsRepository.current().providerId]
        return selected?.takeIf { it.isConfigured() } ?: providers.getValue(ProviderId.YAHOO)
    }

    private companion object {
        /** The spec caps FX refreshes at one per hour. */
        val FX_MAX_AGE: Duration = Duration.ofHours(1)
    }
}
