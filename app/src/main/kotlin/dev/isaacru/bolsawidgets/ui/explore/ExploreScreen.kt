package dev.isaacru.bolsawidgets.ui.explore

import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
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
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(
                    top = innerPadding.calculateTopPadding(),
                    bottom = innerPadding.calculateBottomPadding() + 24.dp,
                ),
        ) {
            SectionHeader(
                title = stringResource(R.string.explore_favorites),
                subtitle = stringResource(R.string.explore_favorites_note),
            )
            if (state.favorites.isEmpty()) {
                SectionMessage(stringResource(R.string.explore_favorites_empty))
            } else {
                Carousel(items = state.favorites, key = { "fav-" + it.symbol }) { row ->
                    InstrumentCard(
                        symbol = row.symbol,
                        name = row.displayName,
                        price = row.quote?.let { Format.price(it.price, it.currency) },
                        changePercent = row.quote?.changePercent,
                        onClick = { onOpenSymbol(row.symbol) },
                        onStar = { viewModel.toggleFavorite(row.symbol, false) },
                    )
                }
            }

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
            if (state.movers.gainers.isEmpty() && !state.isLoading) {
                SectionMessage(
                    stringResource(
                        if (state.failed) {
                            R.string.explore_movers_failed
                        } else {
                            R.string.explore_movers_note_empty
                        },
                    ),
                )
            } else {
                Carousel(items = state.movers.gainers, key = { "up-" + it.symbol }) { mover ->
                    MoverCard(mover = mover, onClick = { onOpenSymbol(mover.symbol) })
                }
            }

            if (state.movers.losers.isNotEmpty()) {
                SectionHeader(title = stringResource(R.string.explore_losers))
                Carousel(items = state.movers.losers, key = { "down-" + it.symbol }) { mover ->
                    MoverCard(mover = mover, onClick = { onOpenSymbol(mover.symbol) })
                }
            }
        }
    }
}

/**
 * A row of cards that scrolls sideways.
 *
 * Lazy on purpose: fifteen cards of which three are on screen should cost three, and the
 * ranking is drawn again every time the tab comes back.
 */
@Composable
private fun <T> Carousel(
    items: List<T>,
    key: (T) -> Any,
    card: @Composable (T) -> Unit,
) {
    LazyRow(
        contentPadding = PaddingValues(horizontal = 16.dp),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
    ) {
        items(items, key = key) { item -> card(item) }
    }
}

@Composable
private fun SectionMessage(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.bodyMedium,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp),
    )
}

@Composable
private fun MoverCard(mover: MarketMover, onClick: () -> Unit) {
    InstrumentCard(
        symbol = mover.symbol,
        name = mover.name,
        price = Format.price(mover.price, mover.currency),
        changePercent = mover.changePercent,
        onClick = onClick,
        onStar = null,
    )
}

/**
 * One card of a carousel.
 *
 * Fixed width so the cards line up and the next one peeks in from the right, which is what
 * says "this scrolls" without a scrollbar. Two lines for the name, because "iShares Core
 * MSCI World UCITS ETF" does not fit in one and cutting it at "iShares" identifies nothing.
 */
@Composable
private fun InstrumentCard(
    symbol: String,
    name: String,
    price: String?,
    changePercent: Double?,
    onClick: () -> Unit,
    onStar: (() -> Unit)?,
) {
    ElevatedCard(
        onClick = onClick,
        modifier = Modifier.width(CARD_WIDTH.dp),
    ) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                SymbolMonogram(symbol = symbol, size = 36)
                Spacer(modifier = Modifier.weight(1f))
                if (onStar != null) {
                    IconButton(onClick = onStar, modifier = Modifier.size(28.dp)) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(R.string.explore_unstar),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(18.dp),
                        )
                    }
                }
            }

            Column {
                Text(
                    text = symbol,
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = name,
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium,
                    maxLines = 2,
                    minLines = 2,
                    overflow = TextOverflow.Ellipsis,
                )
            }

            Column {
                Text(
                    text = price ?: stringResource(R.string.watchlist_no_price),
                    style = MaterialTheme.typography.titleSmall,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                if (changePercent != null) {
                    ChangeIndicator(
                        percent = changePercent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
            }
        }
    }
}

private const val CARD_WIDTH = 168

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

