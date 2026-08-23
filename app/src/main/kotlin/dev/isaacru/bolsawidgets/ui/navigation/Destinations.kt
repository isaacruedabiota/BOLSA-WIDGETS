package dev.isaacru.bolsawidgets.ui.navigation

import kotlinx.serialization.Serializable

/** Type-safe navigation routes. Serialized by navigation-compose, so keep them stable. */

@Serializable
data object PortfolioRoute

@Serializable
data object WatchlistRoute

@Serializable
data object SettingsRoute

/** [positionId] is 0 for a brand new lot. */
@Serializable
data class PositionEditorRoute(val positionId: Long = 0L)
