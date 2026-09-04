package dev.isaacru.bolsawidgets.data.local

import androidx.room.Database
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import dev.isaacru.bolsawidgets.data.local.dao.CandleCacheDao
import dev.isaacru.bolsawidgets.data.local.dao.FxRateDao
import dev.isaacru.bolsawidgets.data.local.dao.PositionDao
import dev.isaacru.bolsawidgets.data.local.dao.QuoteCacheDao
import dev.isaacru.bolsawidgets.data.local.dao.WatchlistDao
import dev.isaacru.bolsawidgets.data.local.entity.CachedCandlesEntity
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
        CachedCandlesEntity::class,
    ],
    version = 3,
    exportSchema = true,
)
abstract class BolsaDatabase : RoomDatabase() {

    abstract fun positionDao(): PositionDao

    abstract fun watchlistDao(): WatchlistDao

    abstract fun quoteCacheDao(): QuoteCacheDao

    abstract fun fxRateDao(): FxRateDao

    abstract fun candleCacheDao(): CandleCacheDao

    companion object {
        const val NAME = "bolsa.db"

        /**
         * Adds the candle cache the sparkline widget draws from.
         *
         * A real migration rather than destructive recreation: the positions in this
         * database were typed in by hand and exist nowhere else.
         */
        val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `cached_candles` (" +
                        "`symbol` TEXT NOT NULL, " +
                        "`chartRange` TEXT NOT NULL, " +
                        "`seriesJson` TEXT NOT NULL, " +
                        "`fetchedAtEpochMillis` INTEGER NOT NULL, " +
                        "PRIMARY KEY(`symbol`, `chartRange`))",
                )
            }
        }

        /**
         * Adds the recurring contribution to a followed symbol.
         *
         * Two nullable columns rather than a new table: a contribution has no life of its
         * own, it is an attribute of the symbol and disappears with it. Both are added
         * with no default, so every existing row means "no plan" rather than "zero".
         */
        val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE `watchlist` ADD COLUMN `contributionAmount` REAL")
                db.execSQL("ALTER TABLE `watchlist` ADD COLUMN `contributionPeriod` TEXT")
            }
        }
    }
}
