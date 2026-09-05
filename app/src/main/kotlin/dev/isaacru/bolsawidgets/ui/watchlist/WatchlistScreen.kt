package dev.isaacru.bolsawidgets.ui.watchlist

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
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DriveFileRenameOutline
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Savings
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.ContributionPeriod
import dev.isaacru.bolsawidgets.domain.model.WatchlistRow
import dev.isaacru.bolsawidgets.ui.common.ChangeIndicator
import dev.isaacru.bolsawidgets.ui.common.Format
import dev.isaacru.bolsawidgets.ui.common.SnackbarMessages
import dev.isaacru.bolsawidgets.ui.search.SymbolSearchSheet
import java.time.ZoneId

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun WatchlistScreen(
    onOpenSymbol: (String) -> Unit,
    modifier: Modifier = Modifier,
    viewModel: WatchlistViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    var showSearch by remember { mutableStateOf(false) }
    var editingContribution by remember { mutableStateOf<WatchlistRow?>(null) }
    var renaming by remember { mutableStateOf<WatchlistRow?>(null) }

    SnackbarMessages(viewModel.messages, snackbarHostState)

    Scaffold(
        modifier = modifier,
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.watchlist_title)) },
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
                onClick = { showSearch = true },
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.watchlist_add)) },
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        if (state.isEmpty) {
            EmptyWatchlist(Modifier.padding(innerPadding))
        } else {
            LazyColumn(
                modifier = Modifier.fillMaxSize(),
                contentPadding = PaddingValues(
                    start = 16.dp,
                    end = 16.dp,
                    top = innerPadding.calculateTopPadding() + 8.dp,
                    bottom = innerPadding.calculateBottomPadding() + 96.dp,
                ),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                items(state.rows, key = { it.symbol }) { row ->
                    WatchlistCard(
                        row = row,
                        zoneId = viewModel.zoneId,
                        onOpen = { onOpenSymbol(row.symbol) },
                        onMoveUp = { viewModel.moveUp(row.symbol) },
                        onMoveDown = { viewModel.moveDown(row.symbol) },
                        onRemove = { viewModel.remove(row.symbol) },
                        onEditContribution = { editingContribution = row },
                        onRename = { renaming = row },
                        onToggleFavorite = {
                            viewModel.toggleFavorite(row.symbol, !row.item.isFavorite)
                        },
                    )
                }
            }
        }
    }

    renaming?.let { row ->
        RenameDialog(
            row = row,
            onDismiss = { renaming = null },
            onSave = { name ->
                viewModel.rename(row.symbol, name)
                renaming = null
            },
        )
    }

    editingContribution?.let { row ->
        ContributionDialog(
            row = row,
            onDismiss = { editingContribution = null },
            onSave = { contribution ->
                viewModel.setContribution(row.symbol, contribution)
                editingContribution = null
            },
        )
    }

    if (showSearch) {
        SymbolSearchSheet(
            onDismiss = { showSearch = false },
            onSymbolChosen = { quote ->
                viewModel.add(quote)
                showSearch = false
            },
        )
    }
}

@Composable
private fun WatchlistCard(
    row: WatchlistRow,
    zoneId: ZoneId,
    onOpen: () -> Unit,
    onMoveUp: () -> Unit,
    onMoveDown: () -> Unit,
    onRemove: () -> Unit,
    onEditContribution: () -> Unit,
    onToggleFavorite: () -> Unit,
    onRename: () -> Unit,
) {
    var menuOpen by remember { mutableStateOf(false) }
    val quote = row.quote

    Card(modifier = Modifier.fillMaxWidth()) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .clickable(onClick = onOpen)
                .padding(start = 14.dp, top = 10.dp, bottom = 10.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                ) {
                    Text(
                        text = row.symbol,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                    )
                    if (row.item.isFavorite) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = stringResource(R.string.watchlist_favorite),
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp),
                        )
                    }
                }
                Text(
                    text = row.displayName,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                )
                row.item.contribution?.let { contribution ->
                    Text(
                        text = contributionLabel(contribution),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.primary,
                    )
                }
            }

            Column(horizontalAlignment = Alignment.End) {
                if (quote == null) {
                    Text(
                        text = stringResource(R.string.watchlist_no_price),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                } else {
                    Text(
                        text = Format.price(quote.price, quote.currency),
                        style = MaterialTheme.typography.titleMedium,
                    )
                    ChangeIndicator(
                        percent = quote.changePercent,
                        style = MaterialTheme.typography.labelMedium,
                    )
                    Text(
                        text = Format.dateTime(quote.timestamp, zoneId),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }

            Box {
                IconButton(onClick = { menuOpen = true }) {
                    Icon(Icons.Filled.MoreVert, stringResource(R.string.action_more))
                }
                DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                    DropdownMenuItem(
                        text = {
                            Text(
                                stringResource(
                                    if (row.item.isFavorite) {
                                        R.string.action_unfavorite
                                    } else {
                                        R.string.action_favorite
                                    },
                                ),
                            )
                        },
                        leadingIcon = {
                            Icon(
                                imageVector = if (row.item.isFavorite) {
                                    Icons.Filled.Star
                                } else {
                                    Icons.Outlined.StarOutline
                                },
                                contentDescription = null,
                            )
                        },
                        onClick = {
                            menuOpen = false
                            onToggleFavorite()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_rename)) },
                        leadingIcon = { Icon(Icons.Filled.DriveFileRenameOutline, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRename()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_contribution)) },
                        leadingIcon = { Icon(Icons.Filled.Savings, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onEditContribution()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_up)) },
                        leadingIcon = { Icon(Icons.Filled.ArrowUpward, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveUp()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_move_down)) },
                        leadingIcon = { Icon(Icons.Filled.ArrowDownward, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onMoveDown()
                        },
                    )
                    DropdownMenuItem(
                        text = { Text(stringResource(R.string.action_remove)) },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = {
                            menuOpen = false
                            onRemove()
                        },
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyWatchlist(modifier: Modifier = Modifier) {
    Box(modifier = modifier.fillMaxSize().padding(32.dp), contentAlignment = Alignment.Center) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            Text(
                text = stringResource(R.string.watchlist_empty_title),
                style = MaterialTheme.typography.titleMedium,
            )
            Text(
                text = stringResource(R.string.watchlist_empty_body),
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}

/**
 * Where the user says how much money goes into a value, and how often.
 *
 * The cadence is stored as typed instead of being normalised away, because "50 a la
 * semana" is how the user thinks about it; turning it into 216,67 al mes would be right
 * and unrecognisable. The map does that conversion later, where it needs one scale.
 */
@Composable
private fun ContributionDialog(
    row: WatchlistRow,
    onDismiss: () -> Unit,
    onSave: (Contribution?) -> Unit,
) {
    val current = row.item.contribution
    var amountText by remember(row.symbol) {
        mutableStateOf(current?.let { Format.editable(it.amountEur, 2) }.orEmpty())
    }
    var period by remember(row.symbol) {
        mutableStateOf(current?.period ?: ContributionPeriod.MONTHLY)
    }
    val contribution = Contribution.of(amountText.toDecimalOrNull(), period)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.contribution_title, row.symbol)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.contribution_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = amountText,
                    onValueChange = { amountText = it },
                    label = { Text(stringResource(R.string.contribution_amount)) },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                    modifier = Modifier.fillMaxWidth(),
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    ContributionPeriod.entries.forEach { option ->
                        FilterChip(
                            selected = period == option,
                            onClick = { period = option },
                            label = { Text(stringResource(periodLabel(option))) },
                        )
                    }
                }
                if (contribution != null && contribution.period == ContributionPeriod.WEEKLY) {
                    // Weekly amounts are compared as monthly ones, so the figure the map
                    // will actually use is shown here rather than left as a surprise.
                    Text(
                        text = stringResource(
                            R.string.contribution_monthly_equivalent,
                            Format.money(contribution.monthlyEur),
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
            }
        },
        confirmButton = {
            TextButton(
                onClick = { onSave(contribution) },
                enabled = contribution != null,
            ) { Text(stringResource(R.string.action_save)) }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (current != null) {
                    TextButton(onClick = { onSave(null) }) {
                        Text(stringResource(R.string.contribution_clear))
                    }
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}

@Composable
private fun contributionLabel(contribution: Contribution): String = stringResource(
    when (contribution.period) {
        ContributionPeriod.WEEKLY -> R.string.contribution_weekly_value
        ContributionPeriod.MONTHLY -> R.string.contribution_monthly_value
    },
    Format.money(contribution.amountEur),
)

private fun periodLabel(period: ContributionPeriod): Int = when (period) {
    ContributionPeriod.WEEKLY -> R.string.contribution_period_weekly
    ContributionPeriod.MONTHLY -> R.string.contribution_period_monthly
}

/** Accepts either decimal separator, like every other amount field in the app. */
private fun String.toDecimalOrNull(): Double? = trim().replace(',', '.').toDoubleOrNull()

/**
 * Renaming a value.
 *
 * The field starts on whatever is being shown right now, market name included, so the
 * dialog is also a way to see what you are replacing. Leaving it empty is how you undo a
 * rename: the row goes back to the name the market gives it rather than to nothing.
 */
@Composable
private fun RenameDialog(
    row: WatchlistRow,
    onDismiss: () -> Unit,
    onSave: (String) -> Unit,
) {
    var name by remember(row.symbol) { mutableStateOf(row.displayName) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.rename_title, row.symbol)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.rename_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.rename_label)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth(),
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onSave(name) }) {
                Text(stringResource(R.string.action_save))
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                TextButton(onClick = { onSave("") }) {
                    Text(stringResource(R.string.rename_restore))
                }
                TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
            }
        },
    )
}
