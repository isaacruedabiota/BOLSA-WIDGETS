package dev.isaacru.bolsawidgets.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import dev.isaacru.bolsawidgets.data.local.dao.FxRateDao
import dev.isaacru.bolsawidgets.data.local.dao.PositionDao
import dev.isaacru.bolsawidgets.data.local.dao.QuoteCacheDao
import dev.isaacru.bolsawidgets.data.local.dao.WatchlistDao
import dev.isaacru.bolsawidgets.data.local.entity.CachedQuoteEntity
import dev.isaacru.bolsawidgets.data.local.entity.FxRateEntity
import dev.isaacru.bolsawidgets.data.local.entity.PositionEntity
import dev.isaacru.bolsawidgets.data.local.entity.WatchlistItemEntity

@Database(
    entities = [
        PositionEntity::class,
        WatchlistItemEntity::class,
        CachedQuoteEntity::class,
        FxRateEntity::class,
    ],
    version = 1,
    exportSchema = true,
)
abstract class BolsaDatabase : RoomDatabase() {

    abstract fun positionDao(): PositionDao

    abstract fun watchlistDao(): WatchlistDao

    abstract fun quoteCacheDao(): QuoteCacheDao

    abstract fun fxRateDao(): FxRateDao

    companion object {
        const val NAME = "bolsa.db"
    }
}
