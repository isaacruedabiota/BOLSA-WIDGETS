package dev.isaacru.bolsawidgets.domain.model

/** A watchlist entry paired with its last known price, which may be missing. */
data class WatchlistRow(
    val item: WatchlistItem,
    val quote: Quote?,
) {
    val symbol: String get() = item.symbol

    val displayName: String get() = item.name.ifBlank { quote?.shortName ?: item.symbol }
}
