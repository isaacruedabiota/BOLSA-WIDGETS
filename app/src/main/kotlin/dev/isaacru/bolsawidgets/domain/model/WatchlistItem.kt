package dev.isaacru.bolsawidgets.domain.model

/** A followed symbol that is not necessarily held. [sortOrder] drives the widget order. */
data class WatchlistItem(
    val symbol: String,
    val name: String,
    val sortOrder: Int,
)
