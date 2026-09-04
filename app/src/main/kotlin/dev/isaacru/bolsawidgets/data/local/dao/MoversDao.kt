package dev.isaacru.bolsawidgets.data.local.dao

import androidx.room.Dao
import androidx.room.Query
import androidx.room.Upsert
import dev.isaacru.bolsawidgets.data.local.entity.CachedMoversEntity

@Dao
interface MoversDao {

    @Query("SELECT * FROM cached_movers WHERE direction = :direction")
    suspend fun get(direction: String): CachedMoversEntity?

    @Upsert
    suspend fun upsert(row: CachedMoversEntity)
}
