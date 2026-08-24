package dev.isaacru.bolsawidgets.ui.portfolio

import androidx.compose.animation.AnimatedVisibility
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
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.automirrored.filled.ShowChart
import androidx.compose.material.icons.filled.WarningAmber
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ElevatedCard
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
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
import dev.isaacru.bolsawidgets.domain.model.PortfolioSummary
import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.model.PositionValuation
import dev.isaacru.bolsawidgets.ui.common.ChangeIndicator
import dev.isaacru.bolsawidgets.ui.common.Format
import dev.isaacru.bolsawidgets.ui.common.SnackbarMessages
import dev.isaacru.bolsawidgets.ui.common.changeColor
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun PortfolioScreen(
    onAddPosition: () -> Unit,
    onEditPosition: (Long) -> Unit,
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: PortfolioViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var expandedSymbol by remember { mutableStateOf<String?>(null) }

    SnackbarMessages(viewModel.messages, snackbarHostState)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.portfolio_title)) },
                actions = {
                    IconButton(onClick = viewModel::refresh, enabled = !state.isRefreshing) {
                        if (state.isRefreshing) {
                            CircularProgressIndicator(modifier = Modifier.size(20.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Refresh, stringResource(R.string.action_refresh))
                        }
                    }
                },
            )
        },
        floatingActionButton = {
            ExtendedFloatingActionButton(
                onClick = onAddPosition,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.portfolio_add_position)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyPortfolio(Modifier.padding(innerPadding))
            return@Scaffold
        }

        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                start = 16.dp,
                end = 16.dp,
                top = innerPadding.calculateTopPadding() + 8.dp,
                bottom = innerPadding.calculateBottomPadding() + 96.dp,
            ),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            item {
                SummaryCard(
                    summary = state.summary,
                    privacyMode = state.privacyMode,
                    zoneId = viewModel.zoneId,
                )
            }

            if (state.summary.unpricedSymbols.isNotEmpty()) {
                item { UnpricedWarning(state.summary.unpricedSymbols) }
            }

            items(state.summary.positions, key = { it.position.symbol }) { valuation ->
                PositionCard(
                    valuation = valuation,
                    lots = state.lotsBySymbol[valuation.position.symbol].orEmpty(),
                    totalValueEur = state.summary.totalValueEur,
                    privacyMode = state.privacyMode,
                    expanded = expandedSymbol == valuation.position.symbol,
                    onToggle = {
                        expandedSymbol = valuation.position.symbol.takeIf { it != expandedSymbol }
                    },
                    onOpen = { onOpenSymbol(valuation.position.symbol) },
                    onEditLot = onEditPosition,
                    onDeleteLot = viewModel::deleteLot,
                )
            }
        }
    }
}

@Composable
private fun SummaryCard(
    summary: PortfolioSummary,
    privacyMode: Boolean,
    zoneId: ZoneId,
) {
    ElevatedCard(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp),
        ) {
            Text(
                text = stringResource(R.string.portfolio_total_value),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Text(
                text = moneyOrHidden(summary.totalValueEur, privacyMode),
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.SemiBold,
            )

            HorizontalDivider()

            PnlRow(
                label = stringResource(R.string.portfolio_day_pnl),
                amountEur = summary.dayPnlEur,
                percent = summary.dayPnlPercent,
                privacyMode = privacyMode,
            )
            PnlRow(
                label = stringResource(R.string.portfolio_total_pnl),
                amountEur = summary.totalPnlEur,
                percent = summary.totalPnlPercent,
                privacyMode = privacyMode,
            )

            if (!privacyMode) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.portfolio_cost_basis),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(text = Format.money(summary.costBasisEur), style = MaterialTheme.typography.bodyMedium)
                }
            }

            Text(
                text = summary.lastQuoteAt
                    ?.let { stringResource(R.string.portfolio_last_update, Format.dateTime(it, zoneId)) }
                    ?: stringResource(R.string.portfolio_never_updated),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
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

@Composable
private fun UnpricedWarning(symbols: List<String>) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.errorContainer,
            contentColor = MaterialTheme.colorScheme.onErrorContainer,
        ),
        modifier = Modifier.fillMaxWidth(),
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            horizontalArrangement = Arrangement.spacedBy(10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Icon(Icons.Filled.WarningAmber, contentDescription = null, modifier = Modifier.size(20.dp))
            Text(
                text = stringResource(R.string.portfolio_unpriced_warning, symbols.joinToString(", ")),
                style = MaterialTheme.typography.bodySmall,
            )
        }
    }
}

@Composable
private fun PositionCard(
    valuation: PositionValuation,
    lots: List<Position>,
    totalValueEur: Double,
    privacyMode: Boolean,
    expanded: Boolean,
    onToggle: () -> Unit,
    onOpen: () -> Unit,
    onEditLot: (Long) -> Unit,
    onDeleteLot: (Long) -> Unit,
) {
    val position = valuation.position
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.clickable(onClick = onToggle).padding(14.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = position.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    Text(
                        text = position.name,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                    )
                }
                Column(horizontalAlignment = Alignment.End) {
                    Text(
                        text = moneyOrHidden(valuation.marketValueEur, privacyMode),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ChangeIndicator(
                        percent = valuation.dayPnlPercent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                }
                IconButton(onClick = onOpen) {
                    Icon(
                        Icons.AutoMirrored.Filled.ShowChart,
                        stringResource(R.string.action_detail),
                        modifier = Modifier.size(20.dp),
                    )
                }
                Icon(
                    imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                    contentDescription = null,
                )
            }

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Text(
                    text = stringResource(R.string.portfolio_quantity, Format.quantity(position.quantity)),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.portfolio_average_price,
                        Format.price(position.averageBuyPrice, position.currency),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                Text(
                    text = stringResource(
                        R.string.portfolio_weight,
                        Format.percent(valuation.weightIn(totalValueEur) * 100.0, withSign = false),
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }

            if (!privacyMode) {
                Row(
                    modifier = Modifier.fillMaxWidth().padding(top = 4.dp),
                    horizontalArrangement = Arrangement.SpaceBetween,
                ) {
                    Text(
                        text = stringResource(R.string.portfolio_total_pnl),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                    Text(
                        text = Format.signedMoney(valuation.totalPnlEur) +
                            "  (" + Format.percent(valuation.totalPnlPercent) + ")",
                        style = MaterialTheme.typography.labelSmall,
                        color = changeColor(valuation.totalPnlEur),
                    )
                }
            }

            AnimatedVisibility(visible = expanded) {
                Column(modifier = Modifier.padding(top = 10.dp)) {
                    HorizontalDivider()
                    Text(
                        text = pluralStringResource(R.plurals.count_lots, lots.size, lots.size),
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(vertical = 8.dp),
                    )
                    lots.forEach { lot ->
                        LotRow(lot = lot, onEdit = { onEditLot(lot.id) }, onDelete = { onDeleteLot(lot.id) })
                    }
                }
            }
        }
    }
}

@Composable
private fun LotRow(lot: Position, onEdit: () -> Unit, onDelete: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().padding(vertical = 2.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(
                text = Format.quantity(lot.quantity) + " × " + Format.price(lot.averageBuyPrice, lot.currency),
                style = MaterialTheme.typography.bodyMedium,
            )
            Text(
                text = Format.date(lot.purchaseDate) +
                    if (lot.notes.isBlank()) "" else " · " + lot.notes,
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis,
            )
        }
        IconButton(onClick = onEdit) {
            Icon(Icons.Filled.Edit, stringResource(R.string.action_edit), modifier = Modifier.size(18.dp))
        }
        IconButton(onClick = onDelete) {
            Icon(Icons.Filled.Delete, stringResource(R.string.action_delete), modifier = Modifier.size(18.dp))
        }
    }
}

@Composable
private fun EmptyPortfolio(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.portfolio_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.portfolio_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

@Composable
private fun moneyOrHidden(amount: Double, privacyMode: Boolean): String =
    if (privacyMode) stringResource(R.string.value_hidden) else Format.money(amount)
