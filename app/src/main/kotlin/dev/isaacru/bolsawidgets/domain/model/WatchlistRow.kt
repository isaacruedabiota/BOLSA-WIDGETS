package dev.isaacru.bolsawidgets.domain.model

/** A watchlist entry paired with its last known price, which may be missing. */
data class WatchlistRow(
    val item: WatchlistItem,
    val quote: Quote?,
) {
    val symbol: String get() = item.symbol

    /**
     * What the app and the widgets print for this value.
     *
     * The user's own name wins, which is what makes renaming work everywhere at once. A
     * blank one is not a name: it falls back to the market's, so clearing the field is how
     * you undo a rename rather than a way to end up with an empty row.
     */
    val displayName: String get() = item.name.ifBlank { quote?.shortName ?: item.symbol }

    /**
     * True when the name on screen is the user's own rather than the market's.
     *
     * Adding a value stores the name the provider gave it, so a stored name is not by
     * itself a rename: it only counts as one when it differs from what the market calls
     * the thing today. Renaming something to exactly its market name is, reasonably
     * enough, the same as not renaming it.
     */
    val hasCustomName: Boolean
        get() = item.name.isNotBlank() && !item.name.equals(quote?.shortName, ignoreCase = true)
}
