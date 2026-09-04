package dev.isaacru.bolsawidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
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
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.ChartRange
import dev.isaacru.bolsawidgets.domain.model.Quote
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.ui.common.Format
import dev.isaacru.bolsawidgets.ui.search.SymbolSearchSheet
import dev.isaacru.bolsawidgets.ui.theme.BolsaWidgetsTheme
import kotlinx.coroutines.launch
import javax.inject.Inject

/**
 * Placement-time configuration for [SparklineWidget]: which ticker, which range.
 *
 * The launcher starts this before the widget exists on screen, and only adds it if we
 * answer RESULT_OK, which is why the result is set to cancelled up front.
 */
@AndroidEntryPoint
class SparklineConfigActivity : ComponentActivity() {

    @Inject
    lateinit var quoteRepository: QuoteRepository

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var stored by mutableStateOf<StoredConfig?>(null)

    /** What the widget is showing right now, or the defaults when it is a new one. */
    private data class StoredConfig(
        val symbol: String?,
        val quote: Quote?,
        val range: ChartRange,
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setResult(RESULT_CANCELED)

        appWidgetId = intent?.extras?.getInt(
            AppWidgetManager.EXTRA_APPWIDGET_ID,
            AppWidgetManager.INVALID_APPWIDGET_ID,
        ) ?: AppWidgetManager.INVALID_APPWIDGET_ID

        if (appWidgetId == AppWidgetManager.INVALID_APPWIDGET_ID) {
            finish()
            return
        }

        lifecycleScope.launch { stored = readStoredConfig() }

        setContent {
            BolsaWidgetsTheme {
                // Reopened by the launcher to reconfigure a widget already on screen, so
                // the screen waits for its current ticker and range instead of coming up
                // empty and asking for them again.
                stored?.let { current ->
                    SparklineConfigScreen(
                        initialSymbol = current.symbol,
                        initialQuote = current.quote,
                        initialRange = current.range,
                        onConfirm = ::confirm,
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private suspend fun readStoredConfig(): StoredConfig {
        val empty = StoredConfig(symbol = null, quote = null, range = ChartRange.DAY)
        val glanceId = runCatching {
            GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        }.getOrNull() ?: return empty
        val preferences = runCatching {
            getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)
        }.getOrNull() ?: return empty

        val symbol = preferences[SparklineWidget.KEY_SYMBOL] ?: return empty
        val range = ChartRange.entries
            .firstOrNull { it.name == preferences[SparklineWidget.KEY_RANGE] }
            ?: ChartRange.DAY
        // From the cache only: reconfiguring must not depend on the network.
        val quote = runCatching {
            quoteRepository.getCachedQuotes(listOf(symbol))[symbol.uppercase()]
        }.getOrNull()
        return StoredConfig(symbol = symbol, quote = quote, range = range)
    }

    private fun confirm(symbol: String, range: ChartRange) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@SparklineConfigActivity)
                .getGlanceIdBy(appWidgetId)
            updateAppWidgetState(
                context = this@SparklineConfigActivity,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId,
            ) { preferences ->
                preferences.toMutablePreferences().apply {
                    this[SparklineWidget.KEY_SYMBOL] = symbol
                    this[SparklineWidget.KEY_RANGE] = range.name
                }
            }
            SparklineWidget().update(this@SparklineConfigActivity, glanceId)

            setResult(
                RESULT_OK,
                Intent().putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID, appWidgetId),
            )
            finish()
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SparklineConfigScreen(
    initialSymbol: String?,
    initialQuote: Quote?,
    initialRange: ChartRange,
    onConfirm: (String, ChartRange) -> Unit,
    onCancel: () -> Unit,
) {
    var chosen by remember { mutableStateOf(initialQuote) }
    var range by remember { mutableStateOf(initialRange) }
    var showSearch by remember { mutableStateOf(false) }
    // The ticker survives an empty quote cache: reconfiguring the range of a widget must
    // not make it forget which value it was showing.
    val symbol = chosen?.symbol ?: initialSymbol

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = stringResource(R.string.widget_config_symbol),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(14.dp)) {
                    if (symbol == null) {
                        Text(
                            text = stringResource(R.string.widget_config_no_symbol),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                        )
                    } else {
                        Text(
                            text = symbol.orEmpty(),
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold,
                        )
                        val detail = listOfNotNull(
                            chosen?.shortName,
                            chosen?.let { Format.price(it.price, it.currency) },
                        ).joinToString(" · ")
                        if (detail.isNotEmpty()) {
                            Text(
                                text = detail,
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                            )
                        }
                    }
                    TextButton(onClick = { showSearch = true }) {
                        Text(stringResource(R.string.widget_config_pick))
                    }
                }
            }

            Text(
                text = stringResource(R.string.widget_config_range),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                ChartRange.entries.forEach { option ->
                    FilterChip(
                        selected = range == option,
                        onClick = { range = option },
                        label = { Text(option.label) },
                    )
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(
                    onClick = { symbol?.let { onConfirm(it, range) } },
                    enabled = symbol != null,
                    modifier = Modifier.weight(1f),
                ) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }

    if (showSearch) {
        SymbolSearchSheet(
            onDismiss = { showSearch = false },
            onSymbolChosen = { quote ->
                chosen = quote
                showSearch = false
            },
        )
    }
}
