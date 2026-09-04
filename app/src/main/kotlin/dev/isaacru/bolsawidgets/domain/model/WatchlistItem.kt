package dev.isaacru.bolsawidgets.domain.model

/**
 * A followed symbol that is not necessarily held. [sortOrder] drives the widget order.
 *
 * [contribution] is what the user puts into it every month or week, when they have said
 * so. It is optional because most followed symbols are only watched: a null here means
 * "no plan", not "zero euros".
 */
data class WatchlistItem(
    val symbol: String,
    val name: String,
    val sortOrder: Int,
    val contribution: Contribution? = null,
)
