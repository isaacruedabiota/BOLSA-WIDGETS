package dev.isaacru.bolsawidgets.data.local.entity

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

@Entity(
    tableName = "positions",
    indices = [Index("symbol")],
)
data class PositionEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0L,
    val symbol: String,
    val name: String,
    val exchange: String,
    val quantity: Double,
    val averageBuyPrice: Double,
    val currency: String,
    /** Stored as epoch day so the column stays sortable and timezone free. */
    val purchaseDateEpochDay: Long,
    val notes: String,
)

@Entity(tableName = "watchlist")
data class WatchlistItemEntity(
    @PrimaryKey val symbol: String,
    val name: String,
    val sortOrder: Int,
)

/**
 * Last known price for a symbol. This table is what keeps the widgets useful in
 * airplane mode, so it is written on every successful fetch and never cleared on error.
 */
@Entity(tableName = "cached_quotes")
data class CachedQuoteEntity(
    @PrimaryKey val symbol: String,
    val price: Double,
    val previousClose: Double,
    val currency: String,
    val shortName: String?,
    val exchange: String?,
    /** Epoch millis of the moment the app stored this row. */
    val fetchedAtEpochMillis: Long,
)

@Entity(tableName = "fx_rates")
data class FxRateEntity(
    /** Concatenated ISO codes, e.g. "USDEUR". */
    @PrimaryKey val pair: String,
    val rate: Double,
    val fetchedAtEpochMillis: Long,
)
