package dev.isaacru.bolsawidgets.ui.search

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
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
import androidx.compose.ui.text.input.KeyboardCapitalization
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.ui.common.ChangeIndicator
import dev.isaacru.bolsawidgets.ui.common.Format
import dev.isaacru.bolsawidgets.ui.theme.Gain

/**
 * Symbol picker shared by the Cartera editor and the Seguimiento screen.
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
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.search_title),
                style = MaterialTheme.typography.titleLarge,
            )

            OutlinedTextField(
                value = state.query,
                onValueChange = viewModel::onQueryChange,
                label = { Text(stringResource(R.string.search_field_label)) },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                singleLine = true,
                keyboardOptions = KeyboardOptions(
                    capitalization = KeyboardCapitalization.Characters,
                    imeAction = ImeAction.Search,
                ),
                keyboardActions = KeyboardActions(onSearch = { viewModel.choose(state.query) }),
                modifier = Modifier
                    .fillMaxWidth()
                    .focusRequester(focusRequester),
            )

            if (state.isSearching) {
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
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

                state.showEmptyState -> Text(
                    text = stringResource(R.string.search_no_results),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }

            LazyColumn(modifier = Modifier.heightIn(max = 420.dp)) {
                state.exactMatch?.let { quote ->
                    item {
                        SectionHeader(stringResource(R.string.search_verified_header))
                        VerifiedResultRow(quote = quote, onClick = { viewModel.chooseVerified(quote) })
                        HorizontalDivider()
                    }
                }
                if (state.suggestions.isNotEmpty()) {
                    item { SectionHeader(stringResource(R.string.search_suggestions_header)) }
                    items(state.suggestions, key = { it.symbol }) { suggestion ->
                        ListItem(
                            headlineContent = { Text(suggestion.symbol) },
                            supportingContent = {
                                Text(
                                    text = listOf(suggestion.name, suggestion.exchange, suggestion.type)
                                        .filter { it.isNotBlank() }
                                        .joinToString(" · "),
                                    maxLines = 2,
                                )
                            },
                            trailingContent = {
                                if (state.resolvingSymbol == suggestion.symbol) {
                                    CircularProgressIndicator(modifier = Modifier.size(20.dp))
                                }
                            },
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { viewModel.choose(suggestion.symbol) },
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(vertical = 8.dp),
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
                Text(text = quote.symbol, fontWeight = FontWeight.SemiBold)
            }
        },
        supportingContent = { Text(quote.shortName ?: quote.exchange.orEmpty()) },
        trailingContent = {
            Column(horizontalAlignment = Alignment.End) {
                Text(Format.price(quote.price, quote.currency))
                ChangeIndicator(
                    percent = quote.changePercent,
                    style = MaterialTheme.typography.labelSmall,
                )
            }
        },
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
    )
}
