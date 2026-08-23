package dev.isaacru.bolsawidgets.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.isaacru.bolsawidgets.data.local.entity.CachedQuoteEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface QuoteCacheDao {

    @Query("SELECT * FROM cached_quotes")
    fun observeAll(): Flow<List<CachedQuoteEntity>>

    @Query("SELECT * FROM cached_quotes WHERE symbol IN (:symbols)")
    fun observeBySymbols(symbols: List<String>): Flow<List<CachedQuoteEntity>>

    @Query("SELECT * FROM cached_quotes")
    suspend fun getAll(): List<CachedQuoteEntity>

    @Query("SELECT * FROM cached_quotes WHERE symbol = :symbol")
    suspend fun getBySymbol(symbol: String): CachedQuoteEntity?

    @Query("SELECT * FROM cached_quotes WHERE symbol IN (:symbols)")
    suspend fun getBySymbols(symbols: List<String>): List<CachedQuoteEntity>

    @Upsert
    suspend fun upsertAll(quotes: List<CachedQuoteEntity>)

    @Query("DELETE FROM cached_quotes WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
