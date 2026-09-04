package dev.isaacru.bolsawidgets.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Serialized by navigation-compose, so keep them stable. */

@Serializable
data object WatchlistRoute

@Serializable
data object SettingsRoute

/** The expanded view of one symbol. Also where a widget tap lands. */
@Serializable
data class SymbolDetailRoute(val symbol: String)
