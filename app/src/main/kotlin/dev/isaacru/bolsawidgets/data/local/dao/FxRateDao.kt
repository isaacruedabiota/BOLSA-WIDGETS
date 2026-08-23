package dev.isaacru.bolsawidgets.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.isaacru.bolsawidgets.data.local.entity.FxRateEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface FxRateDao {

    @Query("SELECT * FROM fx_rates")
    fun observeAll(): Flow<List<FxRateEntity>>

    @Query("SELECT * FROM fx_rates")
    suspend fun getAll(): List<FxRateEntity>

    @Query("SELECT * FROM fx_rates WHERE pair = :pair")
    suspend fun getByPair(pair: String): FxRateEntity?

    @Upsert
    suspend fun upsertAll(rates: List<FxRateEntity>)
}
