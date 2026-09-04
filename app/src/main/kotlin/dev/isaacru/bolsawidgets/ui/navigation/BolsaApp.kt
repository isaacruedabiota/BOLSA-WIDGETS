package dev.isaacru.bolsawidgets.ui.navigation

import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.List
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.TrendingUp
import androidx.compose.material3.Icon
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.navigation.NavDestination
import androidx.navigation.NavDestination.Companion.hasRoute
import androidx.navigation.NavDestination.Companion.hierarchy
import androidx.navigation.NavGraph.Companion.findStartDestination
import androidx.navigation.NavHostController
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.currentBackStackEntryAsState
import androidx.navigation.compose.rememberNavController
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.ui.detail.SymbolDetailScreen
import dev.isaacru.bolsawidgets.ui.explore.ExploreScreen
import dev.isaacru.bolsawidgets.ui.settings.SettingsScreen
import dev.isaacru.bolsawidgets.ui.watchlist.WatchlistScreen
import kotlin.reflect.KClass

/** The three tabs of the bottom bar, in order. */
private enum class TopLevelTab(
    val route: Any,
    val routeClass: KClass<*>,
    val labelRes: Int,
    val icon: ImageVector,
) {
    WATCHLIST(WatchlistRoute, WatchlistRoute::class, R.string.nav_watchlist, Icons.AutoMirrored.Filled.List),
    EXPLORE(ExploreRoute, ExploreRoute::class, R.string.nav_explore, Icons.Filled.TrendingUp),
    SETTINGS(SettingsRoute, SettingsRoute::class, R.string.nav_settings, Icons.Filled.Settings),
}

@Composable
fun BolsaApp(
    deepLinkSymbol: String? = null,
    onDeepLinkHandled: () -> Unit = {},
    navController: NavHostController = rememberNavController(),
) {
    LaunchedEffect(deepLinkSymbol) {
        if (deepLinkSymbol != null) {
            navController.navigate(SymbolDetailRoute(deepLinkSymbol))
            onDeepLinkHandled()
        }
    }

    val backStackEntry by navController.currentBackStackEntryAsState()
    val currentDestination = backStackEntry?.destination
    val showBottomBar = TopLevelTab.entries.any { currentDestination.isOn(it.routeClass) }

    Scaffold(
        modifier = Modifier.fillMaxSize(),
        bottomBar = {
            if (showBottomBar) {
                NavigationBar {
                    TopLevelTab.entries.forEach { tab ->
                        NavigationBarItem(
                            selected = currentDestination.isOn(tab.routeClass),
                            onClick = { navController.navigateToTab(tab.route) },
                            icon = { Icon(tab.icon, contentDescription = null) },
                            label = { Text(stringResource(tab.labelRes)) },
                        )
                    }
                }
            }
        },
    ) { innerPadding ->
        NavHost(
            navController = navController,
            startDestination = WatchlistRoute,
            modifier = Modifier.fillMaxSize(),
        ) {
            composable<WatchlistRoute> {
                WatchlistScreen(
                    onOpenSymbol = { symbol -> navController.navigate(SymbolDetailRoute(symbol)) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            composable<ExploreRoute> {
                ExploreScreen(
                    onOpenSymbol = { symbol -> navController.navigate(SymbolDetailRoute(symbol)) },
                    modifier = Modifier.padding(innerPadding),
                )
            }
            composable<SettingsRoute> {
                SettingsScreen(modifier = Modifier.padding(innerPadding))
            }
            composable<SymbolDetailRoute> {
                SymbolDetailScreen(onBack = { navController.popBackStack() })
            }
        }
    }
}

/** Tab switching keeps one entry per tab and restores where the user left off. */
private fun NavHostController.navigateToTab(route: Any) {
    navigate(route) {
        popUpTo(graph.findStartDestination().id) { saveState = true }
        launchSingleTop = true
        restoreState = true
    }
}

private fun NavDestination?.isOn(routeClass: KClass<*>): Boolean =
    this?.hierarchy?.any { it.hasRoute(routeClass) } == true
