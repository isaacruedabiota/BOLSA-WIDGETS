package dev.isaacru.bolsawidgets.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.focus.FocusRequester
import androidx.compose.ui.focus.focusRequester
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.market.Market
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.search.SymbolKind
import dev.isaacru.bolsawidgets.domain.search.SymbolSuggestion
import dev.isaacru.bolsawidgets.ui.common.ChangeIndicator
import dev.isaacru.bolsawidgets.ui.common.Format
import dev.isaacru.bolsawidgets.ui.common.PriceChart
import dev.isaacru.bolsawidgets.ui.common.SymbolMonogram
import dev.isaacru.bolsawidgets.ui.common.changeColor
import dev.isaacru.bolsawidgets.ui.theme.Gain

/**
 * Symbol picker shared by Seguimiento and the sparkline widget's setup.
 *
 * It suggests as you type and lets the list be narrowed by the only two things known
 * about a symbol before it is resolved: what it is and where it trades. Price, currency
 * and day change arrive with the quote, one call per symbol, which is not something a
 * list can afford — so those are shown for the verified match alone.
 *
 * The verified block comes from the quote endpoint the whole app already depends on, so
 * it is always offered. The suggestion list comes from a separate Yahoo endpoint and is
 * allowed to be missing: when it is, the sheet says so and keeps accepting exact tickers.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolSearchSheet(
    onDismiss: () -> Unit,
    onSymbolChosen: (Quote) -> Unit,
    viewModel: SymbolSearchViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    val focusRequester = remember { FocusRequester() }

    LaunchedEffect(Unit) { viewModel.chosen.collect(onSymbolChosen) }
    LaunchedEffect(Unit) {
        // The sheet is composed only while open, so this runs once per opening.
        viewModel.reset()
        focusRequester.requestFocus()
    }

    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        // One thing at a time: looking at a value replaces the list rather than pushing it
        // off the bottom of a sheet that is already sharing the screen with a keyboard.
        state.preview?.let { preview ->
            SymbolPreviewPanel(
                preview = preview,
                onBack = viewModel::closePreview,
                onConfirm = viewModel::confirm,
            )
            return@ModalBottomSheet
        }

        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                placeholder = { Text(stringResource(R.string.search_placeholder)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                trailingIcon = {
                    if (state.query.isNotEmpty()) {
                        IconButton(onClick = { viewModel.onQueryChange("") }) {
                            Icon(Icons.Filled.Close, stringResource(R.string.search_clear))
                        }
                    }
                },
                singleLine = true,
                // Not forced to upper case any more: this field takes company names as
                // readily as tickers, and "SANTANDER" shouting back is not a search box.
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Search),
                keyboardActions = KeyboardActions(onSearch = { viewModel.open(state.query) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            if (state.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }

            if (state.showFilters) {
                FilterRow(
                    kinds = state.availableKinds,
                    markets = state.availableMarkets,
                    selectedKind = state.filter.kind,
                    selectedMarket = state.filter.market,
                    onKind = viewModel::toggleKind,
                    onMarket = viewModel::toggleMarket,
                )
            }

            state.failedSymbol?.let { symbol ->
                Text(
                    text = stringResource(R.string.search_resolve_failed, symbol),
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.suggestionsUnavailable) {
                Text(
                    text = stringResource(R.string.search_suggestions_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            when {
                state.query.isBlank() -> Text(
                    text = stringResource(R.string.search_hint),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )

                state.showFilteredOutState -> Text(
                    text = stringResource(R.string.search_no_results_filtered),
                    style = MaterialTheme.typography.bodyMedium,
                )

                state.showEmptyState -> Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                state.exactMatch?.let { quote ->
                    item {
                        VerifiedResultRow(quote = quote, onClick = { viewModel.openVerified(quote) })
                        HorizontalDivider()
                    }
                }
                items(state.suggestions, key = { it.symbol }) { suggestion ->
                    SuggestionRow(
                        suggestion = suggestion,
                        changePercent = state.dayChanges[suggestion.symbol.uppercase()],
                        isResolving = state.resolvingSymbol == suggestion.symbol,
                        onClick = { viewModel.open(suggestion.symbol) },
                    )
                }
            }
        }
    }
}

/**
 * The two filter rows, scrolled sideways.
 *
 * Only the options present in the current results are drawn, so every chip on screen has
 * something behind it.
 */
@Composable
private fun FilterRow(
    kinds: List<SymbolKind>,
    markets: List<Market>,
    selectedKind: SymbolKind?,
    selectedMarket: Market?,
    onKind: (SymbolKind) -> Unit,
    onMarket: (Market) -> Unit,
) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        if (kinds.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                kinds.forEach { kind ->
                    FilterChip(
                        selected = kind == selectedKind,
                        onClick = { onKind(kind) },
                        label = { Text(stringResource(kind.labelRes())) },
                    )
                }
            }
        }
        if (markets.size > 1) {
            Row(
                modifier = Modifier.fillMaxWidth().horizontalScroll(rememberScrollState()),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                markets.forEach { market ->
                    FilterChip(
                        selected = market == selectedMarket,
                        onClick = { onMarket(market) },
                        label = { Text(stringResource(market.labelRes())) },
                    )
                }
            }
        }
    }
}

/**
 * One suggestion, name first.
 *
 * The name is what the user is looking for — nobody remembers that Inditex is ITX.MC —
 * and the ticker, the venue and the type sit underneath as the line that tells two
 * listings of the same company apart.
 */
@Composable
private fun SuggestionRow(
    suggestion: SymbolSuggestion,
    changePercent: Double?,
    isResolving: Boolean,
    onClick: () -> Unit,
) {
    ListItem(
        headlineContent = {
            Text(
                text = suggestion.name,
                fontWeight = FontWeight.Medium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        supportingContent = {
            Text(
                text = listOf(
                    suggestion.symbol,
                    suggestion.exchange,
                    stringResource(suggestion.kind.labelRes()),
                ).filter { it.isNotBlank() }.joinToString(" · "),
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        },
        trailingContent = {
            when {
                isResolving -> CircularProgressIndicator(modifier = Modifier.size(20.dp))
                // Absent rather than zero when the day's move is unknown: a flat 0,00 %
                // would be a claim about the market, not a missing value.
                changePercent != null -> ChangeIndicator(
                    percent = changePercent,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

@Composable
private fun VerifiedResultRow(quote: Quote, onClick: () -> Unit) {
    ListItem(
        headlineContent = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp),
            ) {
                Icon(
                    imageVector = Icons.Filled.CheckCircle,
                    contentDescription = null,
                    tint = Gain,
                    modifier = Modifier.size(16.dp),
                )
                Text(
                    text = quote.shortName ?: quote.symbol,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
            }
        },
        supportingContent = {
            Text(
                text = listOf(quote.symbol, quote.exchange.orEmpty())
                    .filter { it.isNotBlank() }
                    .joinToString(" · "),
                maxLines = 1,
            )
        },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.price(quote.price, quote.currency))
                ChangeIndicator(
                    percent = quote.changePercent,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        modifier = Modifier.fillMaxWidth().clickable(onClick = onClick),
    )
}

private fun SymbolKind.labelRes(): Int = when (this) {
    SymbolKind.EQUITY -> R.string.symbol_kind_equity
    SymbolKind.ETF -> R.string.symbol_kind_etf
    SymbolKind.FUND -> R.string.symbol_kind_fund
    SymbolKind.INDEX -> R.string.symbol_kind_index
    SymbolKind.CRYPTO -> R.string.symbol_kind_crypto
    SymbolKind.CURRENCY -> R.string.symbol_kind_currency
    SymbolKind.OTHER -> R.string.symbol_kind_other
}

private fun Market.labelRes(): Int = when (this) {
    Market.BME -> R.string.market_bme
    Market.US -> R.string.market_us
    Market.EURONEXT -> R.string.market_euronext
    Market.XETRA -> R.string.market_xetra
    Market.BORSA_ITALIANA -> R.string.market_milan
    Market.SIX -> R.string.market_six
    Market.LONDON -> R.string.market_london
    Market.UNKNOWN -> R.string.market_other
}


/**
 * The value before it is taken: what it costs, what it has done today, and its session.
 *
 * Looking first is the whole point of this panel, so the day's move is the biggest thing
 * on it and the chart underneath is the session the market is having right now, not a year
 * of history nobody is asking about at this moment.
 */
@Composable
private fun SymbolPreviewPanel(
    preview: SymbolPreview,
    onBack: () -> Unit,
    onConfirm: () -> Unit,
) {
    val quote = preview.quote
    val closes = preview.candles.map { it.close }

    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(horizontal = 16.dp)
            .padding(bottom = 24.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp),
    ) {
        Row(verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onBack) {
                Icon(
                    imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                    contentDescription = stringResource(R.string.action_back),
                )
            }
            SymbolMonogram(symbol = quote.symbol, size = 36)
            Column(modifier = Modifier.padding(start = 12.dp)) {
                Text(
                    text = quote.shortName ?: quote.symbol,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                Text(
                    text = listOf(quote.symbol, quote.exchange.orEmpty())
                        .filter { it.isNotBlank() }
                        .joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                )
            }
        }

        Column {
            Text(
                text = Format.price(quote.price, quote.currency),
                style = MaterialTheme.typography.headlineMedium,
            )
            Row(
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = Format.signedMoney(quote.change, quote.currency),
                    style = MaterialTheme.typography.bodyMedium,
                    color = changeColor(quote.change),
                    fontWeight = FontWeight.Medium,
                )
                ChangeIndicator(
                    percent = quote.changePercent,
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
            Text(
                text = stringResource(R.string.search_preview_today),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }

        when {
            preview.isLoadingChart -> Row(
                modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
            }

            // Checked against the data, not a flag: two points are the least a line needs.
            closes.size < 2 -> Row(
                modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
                horizontalArrangement = Arrangement.Center,
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = stringResource(R.string.detail_chart_unavailable),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            else -> {
                PriceChart(
                    candles = preview.candles,
                    lineColor = changeColor(quote.change),
                    // The line the day is measured against, which is what makes the shape
                    // mean something instead of just wiggling.
                    baseline = quote.previousClose.takeIf { it > 0.0 },
                    modifier = Modifier.fillMaxWidth().height(CHART_HEIGHT.dp),
                )
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(
                            R.string.detail_range_low,
                            Format.price(closes.min(), quote.currency),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.detail_range_high,
                            Format.price(closes.max(), quote.currency),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = stringResource(
                            R.string.search_preview_previous_close,
                            Format.price(quote.previousClose, quote.currency),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            OutlinedButton(onClick = onBack, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.search_preview_back))
            }
            Button(onClick = onConfirm, modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.search_preview_add))
            }
        }
    }
}

private const val CHART_HEIGHT = 170
