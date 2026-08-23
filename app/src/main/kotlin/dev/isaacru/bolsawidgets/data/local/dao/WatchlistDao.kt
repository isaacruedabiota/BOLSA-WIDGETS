package dev.isaacru.bolsawidgets.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Transaction
import androidx.room.Upsert
import dev.isaacru.bolsawidgets.data.local.entity.WatchlistItemEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface WatchlistDao {

    @Query("SELECT * FROM watchlist ORDER BY sortOrder ASC, symbol ASC")
    fun observeAll(): Flow<List<WatchlistItemEntity>>

    @Query("SELECT * FROM watchlist ORDER BY sortOrder ASC, symbol ASC")
    suspend fun getAll(): List<WatchlistItemEntity>

    @Query("SELECT * FROM watchlist WHERE symbol = :symbol")
    suspend fun getBySymbol(symbol: String): WatchlistItemEntity?

    @Query("SELECT COALESCE(MAX(sortOrder), -1) FROM watchlist")
    suspend fun maxSortOrder(): Int

    @Upsert
    suspend fun upsert(item: WatchlistItemEntity)

    @Upsert
    suspend fun upsertAll(items: List<WatchlistItemEntity>)

    @Query("DELETE FROM watchlist WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)

    @Query("DELETE FROM watchlist")
    suspend fun deleteAll()

    @Transaction
    suspend fun replaceOrder(symbolsInOrder: List<String>) {
        val existing = getAll().associateBy { it.symbol }
        val reordered = symbolsInOrder.mapIndexedNotNull { index, symbol ->
            existing[symbol]?.copy(sortOrder = index)
        }
        upsertAll(reordered)
    }
}
