package dev.isaacru.bolsawidgets.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.isaacru.bolsawidgets.data.local.entity.PositionEntity
import kotlinx.coroutines.flow.Flow

@Dao
interface PositionDao {

    @Query("SELECT * FROM positions ORDER BY symbol ASC, purchaseDateEpochDay ASC, id ASC")
    fun observeAll(): Flow<List<PositionEntity>>

    @Query("SELECT DISTINCT UPPER(symbol) FROM positions ORDER BY 1 ASC")
    fun observeSymbols(): Flow<List<String>>

    @Query("SELECT * FROM positions ORDER BY symbol ASC, purchaseDateEpochDay ASC, id ASC")
    suspend fun getAll(): List<PositionEntity>

    @Query("SELECT * FROM positions WHERE id = :id")
    suspend fun getById(id: Long): PositionEntity?

    @Upsert
    suspend fun upsert(position: PositionEntity): Long

    @Query("DELETE FROM positions WHERE id = :id")
    suspend fun deleteById(id: Long)

    @Query("DELETE FROM positions")
    suspend fun deleteAll()
}
