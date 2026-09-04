package dev.isaacru.bolsawidgets.ui.explore

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.MarketMover
import dev.isaacru.bolsawidgets.domain.model.WatchlistRow
import dev.isaacru.bolsawidgets.ui.common.ChangeIndicator
import dev.isaacru.bolsawidgets.ui.common.Format
import dev.isaacru.bolsawidgets.ui.common.SymbolMonogram
import java.time.ZoneId

/**
 * Explorar: what the user starred, and the two ends of today's ranking.
 *
 * The ranking is the US market, because that is the only one the provider will hand over
 * in a single request; the screen says so rather than letting the user assume otherwise.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ExploreScreen(
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: ExploreViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.explore_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isLoading) {
                        if (state.isLoading) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                        }
                    }
                },
            )
        },
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 4.dp,
                bottom = innerPadding.calculateBottomPadding() + 24.dp,
            ),
        ) {
            item {
                SectionHeader(
                    title = stringResource(R.string.explore_favorites),
                    subtitle = stringResource(R.string.explore_favorites_note),
                )
            }
            if (state.favorites.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.explore_favorites_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            } else {
                items(state.favorites, key = { "fav-" + it.symbol }) { row ->
                    FavoriteRow(
                        row = row,
                        onClick = { onOpenSymbol(row.symbol) },
                        onUnstar = { viewModel.toggleFavorite(row.symbol, false) },
                    )
                }
            }

            item {
                SectionHeader(
                    title = stringResource(R.string.explore_gainers),
                    subtitle = state.movers.fetchedAt
                        ?.let {
                            stringResource(
                                R.string.explore_movers_note,
                                Format.dateTime(it, viewModel.zoneId),
                            )
                        }
                        ?: stringResource(R.string.explore_movers_note_empty),
                )
            }
            items(state.movers.gainers, key = { "up-" + it.symbol }) { mover ->
                MoverRow(mover = mover, onClick = { onOpenSymbol(mover.symbol) })
            }

            if (state.movers.losers.isNotEmpty()) {
                item { SectionHeader(title = stringResource(R.string.explore_losers)) }
                items(state.movers.losers, key = { "down-" + it.symbol }) { mover ->
                    MoverRow(mover = mover, onClick = { onOpenSymbol(mover.symbol) })
                }
            }

            if (state.movers.isEmpty && !state.isLoading) {
                item {
                    Text(
                        text = stringResource(
                            if (state.failed) {
                                R.string.explore_movers_failed
                            } else {
                                R.string.explore_movers_note_empty
                            },
                        ),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(title: String, subtitle: String? = null) {
    Column(modifier = Modifier.padding(top = 16.dp, bottom = 4.dp)) {
        Text(
            text = title,
            style = MaterialTheme.typography.titleMedium,
            fontWeight = FontWeight.SemiBold,
        )
        if (subtitle != null) {
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun FavoriteRow(row: WatchlistRow, onClick: () -> Unit, onUnstar: () -> Unit) {
    val quote = row.quote
    InstrumentRow(
        symbol = row.symbol,
        name = row.displayName,
        subtitle = row.symbol,
        price = quote?.let { Format.price(it.price, it.currency) },
        changePercent = quote?.changePercent,
        onClick = onClick,
        trailing = {
            IconButton(onClick = onUnstar) {
                Icon(
                    imageVector = Icons.Filled.Star,
                    contentDescription = stringResource(R.string.explore_unstar),
                    tint = MaterialTheme.colorScheme.primary,
                )
            }
        },
    )
}

@Composable
private fun MoverRow(mover: MarketMover, onClick: () -> Unit) {
    InstrumentRow(
        symbol = mover.symbol,
        name = mover.name,
        subtitle = listOf(mover.symbol, mover.exchange).filter { it.isNotBlank() }.joinToString(" · "),
        price = Format.price(mover.price, mover.currency),
        changePercent = mover.changePercent,
        onClick = onClick,
        trailing = null,
    )
}

/** One line of the tab, whichever section it belongs to. */
@Composable
private fun InstrumentRow(
    symbol: String,
    name: String,
    subtitle: String,
    price: String?,
    changePercent: Double?,
    onClick: () -> Unit,
    trailing: (@Composable () -> Unit)?,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 10.dp),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        SymbolMonogram(symbol = symbol)

        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = name,
                style = MaterialTheme.typography.bodyLarge,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }

        Column(horizontalAlignment = Alignment.End) {
            if (price != null) {
                Text(text = price, style = MaterialTheme.typography.bodyMedium)
            }
            if (changePercent != null) {
                ChangeIndicator(
                    percent = changePercent,
                    style = MaterialTheme.typography.labelMedium,
                )
            } else {
                Text(
                    text = stringResource(R.string.watchlist_no_price),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }

        if (trailing != null) {
            Box { trailing() }
        }
    }
}
