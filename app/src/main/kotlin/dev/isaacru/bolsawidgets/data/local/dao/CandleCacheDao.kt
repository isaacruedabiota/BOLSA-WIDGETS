package dev.isaacru.bolsawidgets.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.isaacru.bolsawidgets.data.local.entity.CachedCandlesEntity

@Dao
interface CandleCacheDao {

    @Query("SELECT * FROM cached_candles WHERE symbol = :symbol AND chartRange = :chartRange")
    suspend fun get(symbol: String, chartRange: String): CachedCandlesEntity?

    @Upsert
    suspend fun upsert(series: CachedCandlesEntity)

    @Query("DELETE FROM cached_candles WHERE symbol = :symbol")
    suspend fun deleteBySymbol(symbol: String)
}
