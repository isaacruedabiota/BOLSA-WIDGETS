package dev.isaacru.bolsawidgets.data.local

import dev.isaacru.bolsawidgets.data.local.entity.CachedQuoteEntity
import dev.isaacru.bolsawidgets.data.local.entity.FxRateEntity
import dev.isaacru.bolsawidgets.data.local.entity.PositionEntity
import dev.isaacru.bolsawidgets.data.local.entity.WatchlistItemEntity
import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.ContributionPeriod
import dev.isaacru.bolsawidgets.domain.model.FxRate
import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.model.WatchlistItem
import java.time.Instant
import java.time.LocalDate

fun PositionEntity.toDomain() = Position(
    id = id,
    symbol = symbol,
    name = name,
    exchange = exchange,
    quantity = quantity,
    averageBuyPrice = averageBuyPrice,
    currency = currency,
    purchaseDate = LocalDate.ofEpochDay(purchaseDateEpochDay),
    notes = notes,
)

fun Position.toEntity() = PositionEntity(
    id = id,
    symbol = symbol.uppercase(),
    name = name,
    exchange = exchange,
    quantity = quantity,
    averageBuyPrice = averageBuyPrice,
    currency = currency.uppercase(),
    purchaseDateEpochDay = purchaseDate.toEpochDay(),
    notes = notes,
)

fun WatchlistItemEntity.toDomain() = WatchlistItem(
    symbol = symbol,
    name = name,
    sortOrder = sortOrder,
    // Anything the column cannot be read as a plan is no plan: a row half written by an
    // older version must not turn into a contribution of an unknown cadence.
    contribution = Contribution.of(
        amountEur = contributionAmount,
        period = ContributionPeriod.entries.firstOrNull { it.name == contributionPeriod },
    ),
)

fun WatchlistItem.toEntity() = WatchlistItemEntity(
    symbol = symbol.uppercase(),
    name = name,
    sortOrder = sortOrder,
    contributionAmount = contribution?.amountEur,
    contributionPeriod = contribution?.period?.name,
)

/**
 * Restores a cached row as a [Quote]. The market timestamp is not cached, so the fetch
 * time stands in for it; that is also the value the widgets label as "last refresh".
 */
fun CachedQuoteEntity.toDomain() = Quote(
    symbol = symbol,
    price = price,
    previousClose = previousClose,
    currency = currency,
    timestamp = Instant.ofEpochMilli(fetchedAtEpochMillis),
    shortName = shortName,
    exchange = exchange,
)

fun Quote.toCacheEntity(fetchedAt: Instant) = CachedQuoteEntity(
    symbol = symbol.uppercase(),
    price = price,
    previousClose = previousClose,
    currency = currency,
    shortName = shortName,
    exchange = exchange,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)

fun FxRateEntity.toDomain() = FxRate(
    pair = pair,
    rate = rate,
    fetchedAt = Instant.ofEpochMilli(fetchedAtEpochMillis),
)

fun FxRate.toEntity() = FxRateEntity(
    pair = pair.uppercase(),
    rate = rate,
    fetchedAtEpochMillis = fetchedAt.toEpochMilli(),
)
