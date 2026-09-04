package dev.isaacru.bolsawidgets.ui.navigation

import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Bookmarks
import androidx.compose.material.icons.outlined.Explore
import androidx.compose.material.icons.outlined.Tune
import androidx.compose.material.icons.rounded.Bookmarks
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.Tune
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
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

/**
 * The three tabs of the bottom bar, in order.
 *
 * Two icons each: the outlined one is the resting state and the filled one marks where you
 * are, which is how a bar reads at a glance without leaning on the label.
 */
private enum class TopLevelTab(
    val route: Any,
    val routeClass: KClass<*>,
    val labelRes: Int,
    val icon: ImageVector,
    val selectedIcon: ImageVector,
) {
    WATCHLIST(
        WatchlistRoute,
        WatchlistRoute::class,
        R.string.nav_watchlist,
        Icons.Outlined.Bookmarks,
        Icons.Rounded.Bookmarks,
    ),
    EXPLORE(
        ExploreRoute,
        ExploreRoute::class,
        R.string.nav_explore,
        Icons.Outlined.Explore,
        Icons.Rounded.Explore,
    ),
    SETTINGS(
        SettingsRoute,
        SettingsRoute::class,
        R.string.nav_settings,
        Icons.Outlined.Tune,
        Icons.Rounded.Tune,
    ),
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
                FloatingTabBar(
                    isSelected = { tab -> currentDestination.isOn(tab.routeClass) },
                    onSelect = { tab -> navController.navigateToTab(tab.route) },
                )
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

/**
 * The bar, floating clear of the edges instead of welded to the bottom of the screen.
 *
 * The pill carries its own insets: the navigation gesture bar is padded around the outside
 * so the shape never sits under it, and the NavigationBar inside is told to add none of its
 * own, which would otherwise leave a band of empty colour inside the rounded shape.
 */
@Composable
private fun FloatingTabBar(
    isSelected: (TopLevelTab) -> Boolean,
    onSelect: (TopLevelTab) -> Unit,
) {
    Box(
        modifier = Modifier
            .fillMaxWidth()
            .navigationBarsPadding()
            .padding(horizontal = 16.dp, vertical = 10.dp),
    ) {
        Surface(
            shape = RoundedCornerShape(28.dp),
            color = MaterialTheme.colorScheme.surfaceContainer,
            tonalElevation = 3.dp,
            shadowElevation = 6.dp,
            modifier = Modifier.fillMaxWidth(),
        ) {
            NavigationBar(
                containerColor = Color.Transparent,
                windowInsets = WindowInsets(0),
                modifier = Modifier.height(72.dp),
            ) {
                TopLevelTab.entries.forEach { tab ->
                    val selected = isSelected(tab)
                    NavigationBarItem(
                        selected = selected,
                        onClick = { onSelect(tab) },
                        icon = {
                            Icon(
                                imageVector = if (selected) tab.selectedIcon else tab.icon,
                                contentDescription = null,
                            )
                        },
                        label = { Text(stringResource(tab.labelRes)) },
                    )
                }
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
