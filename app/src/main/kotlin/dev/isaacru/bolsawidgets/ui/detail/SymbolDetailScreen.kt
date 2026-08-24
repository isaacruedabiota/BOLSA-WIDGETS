package dev.isaacru.bolsawidgets.ui.detail

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
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
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.PositionValuation
import dev.isaacru.bolsawidgets.ui.common.ChangeIndicator
import dev.isaacru.bolsawidgets.ui.common.Format
import dev.isaacru.bolsawidgets.ui.common.changeColor

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SymbolDetailScreen(
    onBack: () -> Unit,
    modifier: Modifier = Modifier,
    viewModel: SymbolDetailViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val quote = state.quote

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = {
                    Column {
                        Text(state.symbol, maxLines = 1)
                        Text(
                            text = state.displayName,
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                        )
                    }
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            stringResource(R.string.action_back),
                        )
                    }
                },
                actions = {
                    IconButton(onClick = viewModel::refresh) {
                        Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                    }
                },
            )
        },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            if (quote == null) {
                Text(
                    text = stringResource(R.string.detail_no_quote),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    modifier = Modifier.padding(top = 16.dp),
                )
            } else {
                Column(modifier = Modifier.padding(top = 8.dp)) {
                    Text(
                        text = Format.price(quote.price, quote.currency),
                        style = MaterialTheme.typography.headlineMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        verticalAlignment = Alignment.CenterVertically,
                    ) {
                        Text(
                            text = Format.signedMoney(quote.change, quote.currency),
                            style = MaterialTheme.typography.bodyMedium,
                            color = changeColor(quote.change),
                        )
                        ChangeIndicator(
                            percent = quote.changePercent,
                            style = MaterialTheme.typography.bodyMedium,
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.portfolio_last_update,
                            Format.dateTime(quote.timestamp, viewModel.zoneId),
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { option ->
                    FilterChip(
                        selected = state.range == option,
                        onClick = { viewModel.selectRange(option) },
                        label = { Text(option.label) },
                    )
                }
            }

            ChartCard(state = state)

            if (state.isHeld) {
                PositionCard(state = state)
            } else {
                Text(
                    text = stringResource(R.string.detail_not_held),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            Text(text = "", modifier = Modifier.height(8.dp))
        }
    }
}

@Composable
private fun ChartCard(state: SymbolDetailUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            when {
                state.isLoadingChart -> Row(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(24.dp), strokeWidth = 2.dp)
                }

                // Checked against the data itself, not the flag: a composition that
                // reaches the drawing branch with an empty list crashes the app.
                state.chartUnavailable || state.candles.size < 2 -> Row(
                    modifier = Modifier.fillMaxWidth().height(180.dp),
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
                    val closes = state.candles.map { it.close }
                    val periodChange = closes.last() - closes.first()
                    PriceChart(
                        candles = state.candles,
                        lineColor = changeColor(periodChange),
                        baseline = state.quote?.previousClose
                            ?.takeIf { state.range == ChartRange.DAY && it > 0.0 },
                        modifier = Modifier.fillMaxWidth().height(180.dp),
                    )
                    HorizontalDivider(modifier = Modifier.padding(vertical = 10.dp))
                    val currency = state.quote?.currency ?: ""
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                    ) {
                        Text(
                            text = stringResource(
                                R.string.detail_range_low,
                                Format.price(closes.min(), currency),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = stringResource(
                                R.string.detail_range_high,
                                Format.price(closes.max(), currency),
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                        Text(
                            text = Format.percent(
                                if (closes.first() == 0.0) 0.0 else periodChange / closes.first() * 100.0,
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            color = changeColor(periodChange),
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun PositionCard(state: SymbolDetailUiState) {
    val valuation = state.valuation ?: return
    val position = valuation.position

    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.detail_your_position),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            DetailRow(
                stringResource(R.string.editor_quantity),
                Format.quantity(position.quantity),
            )
            DetailRow(
                stringResource(R.string.editor_buy_price),
                Format.price(position.averageBuyPrice, position.currency),
            )
            if (!state.privacyMode) {
                DetailRow(
                    stringResource(R.string.portfolio_total_value),
                    Format.money(valuation.marketValueEur),
                )
                DetailRow(
                    stringResource(R.string.portfolio_cost_basis),
                    Format.money(valuation.costBasisEur),
                )
            }
            HorizontalDivider()
            PnlRow(
                label = stringResource(R.string.portfolio_day_pnl),
                amountEur = valuation.dayPnlEur,
                percent = valuation.dayPnlPercent,
                privacyMode = state.privacyMode,
            )
            PnlRow(
                label = stringResource(R.string.portfolio_total_pnl),
                amountEur = valuation.totalPnlEur,
                percent = valuation.totalPnlPercent,
                privacyMode = state.privacyMode,
            )
            Text(
                text = pluralStringResource(R.plurals.count_lots, state.lots.size, state.lots.size),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun DetailRow(label: String, value: String) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Text(text = value, style = MaterialTheme.typography.bodyMedium)
    }
}

@Composable
private fun PnlRow(label: String, amountEur: Double, percent: Double, privacyMode: Boolean) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Text(
            text = label,
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
        Row(horizontalArrangement = Arrangement.spacedBy(10.dp), verticalAlignment = Alignment.CenterVertically) {
            if (!privacyMode) {
                Text(
                    text = Format.signedMoney(amountEur),
                    style = MaterialTheme.typography.bodyMedium,
                    color = changeColor(amountEur),
                    fontWeight = FontWeight.Medium,
                )
            }
            ChangeIndicator(percent = percent, style = MaterialTheme.typography.bodyMedium)
        }
    }
}
