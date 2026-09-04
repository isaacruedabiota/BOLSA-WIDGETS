package dev.isaacru.bolsawidgets.data.repository

import dev.isaacru.bolsawidgets.data.local.dao.WatchlistDao
import dev.isaacru.bolsawidgets.data.local.entity.WatchlistItemEntity
import dev.isaacru.bolsawidgets.data.local.toDomain
import dev.isaacru.bolsawidgets.data.local.toEntity
import dev.isaacru.bolsawidgets.di.IoDispatcher
import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.WatchlistItem
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class WatchlistRepositoryImpl @Inject constructor(
    private val watchlistDao: WatchlistDao,
    @param:IoDispatcher private val io: CoroutineDispatcher,
) : WatchlistRepository {

    override fun observeItems(): Flow<List<WatchlistItem>> =
        watchlistDao.observeAll().map { rows -> rows.map { it.toDomain() } }

    override suspend fun getItems(): List<WatchlistItem> = withContext(io) {
        watchlistDao.getAll().map { it.toDomain() }
    }

    override suspend fun add(symbol: String, name: String) = withContext(io) {
        val normalized = symbol.trim().uppercase()
        if (normalized.isEmpty() || watchlistDao.getBySymbol(normalized) != null) {
            return@withContext
        }
        watchlistDao.upsert(
            WatchlistItemEntity(
                symbol = normalized,
                name = name.ifBlank { normalized },
                sortOrder = watchlistDao.maxSortOrder() + 1,
            ),
        )
    }

    override suspend fun remove(symbol: String) = withContext(io) {
        watchlistDao.deleteBySymbol(symbol.trim().uppercase())
    }

    override suspend fun setContribution(
        symbol: String,
        contribution: Contribution?,
    ) = withContext(io) {
        val existing = watchlistDao.getBySymbol(symbol.trim().uppercase()) ?: return@withContext
        watchlistDao.upsert(
            existing.copy(
                contributionAmount = contribution?.amountEur,
                contributionPeriod = contribution?.period?.name,
            ),
        )
    }

    override suspend fun reorder(symbolsInOrder: List<String>) = withContext(io) {
        watchlistDao.replaceOrder(symbolsInOrder.map { it.trim().uppercase() })
    }

    override suspend fun replaceAll(items: List<WatchlistItem>) = withContext(io) {
        watchlistDao.replaceAll(
            items.mapIndexed { index, item -> item.copy(sortOrder = index).toEntity() },
        )
    }
}
