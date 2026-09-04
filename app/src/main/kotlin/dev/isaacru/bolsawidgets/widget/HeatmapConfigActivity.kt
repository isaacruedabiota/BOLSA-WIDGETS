package dev.isaacru.bolsawidgets.widget

import android.appwidget.AppWidgetManager
import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.glance.appwidget.GlanceAppWidgetManager
import androidx.glance.appwidget.state.getAppWidgetState
import androidx.glance.appwidget.state.updateAppWidgetState
import androidx.glance.state.PreferencesGlanceStateDefinition
import androidx.lifecycle.lifecycleScope
import dagger.hilt.android.AndroidEntryPoint
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.ui.theme.BolsaWidgetsTheme
import kotlinx.coroutines.launch

/**
 * Placement-time configuration for [HeatmapWidget]: which list the map is drawn from.
 *
 * A choice rather than a merge, because the two modes give the tile area different
 * meanings and a map that mixes them cannot be read.
 */
@AndroidEntryPoint
class HeatmapConfigActivity : ComponentActivity() {

    private var appWidgetId = AppWidgetManager.INVALID_APPWIDGET_ID

    private var storedSource by mutableStateOf<HeatmapSource?>(null)

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

        lifecycleScope.launch { storedSource = readStoredSource() }

        setContent {
            BolsaWidgetsTheme {
                // The launcher reopens this screen to reconfigure a widget that is already
                // on the home screen, so nothing is drawn until the current choice is
                // known: starting on the default would silently propose changing it.
                storedSource?.let { stored ->
                    HeatmapConfigScreen(
                        initial = stored,
                        onConfirm = ::confirm,
                        onCancel = { finish() },
                    )
                }
            }
        }
    }

    private suspend fun readStoredSource(): HeatmapSource {
        val glanceId = runCatching {
            GlanceAppWidgetManager(this).getGlanceIdBy(appWidgetId)
        }.getOrNull() ?: return HeatmapSource.WATCHLIST
        val stored = runCatching {
            val preferences = getAppWidgetState(this, PreferencesGlanceStateDefinition, glanceId)
            preferences[HeatmapWidget.KEY_SOURCE]
        }.getOrNull()
        return HeatmapSource.entries.firstOrNull { it.name == stored } ?: HeatmapSource.WATCHLIST
    }

    private fun confirm(source: HeatmapSource) {
        lifecycleScope.launch {
            val glanceId = GlanceAppWidgetManager(this@HeatmapConfigActivity)
                .getGlanceIdBy(appWidgetId)
            updateAppWidgetState(
                context = this@HeatmapConfigActivity,
                definition = PreferencesGlanceStateDefinition,
                glanceId = glanceId,
            ) { preferences ->
                preferences.toMutablePreferences().apply {
                    this[HeatmapWidget.KEY_SOURCE] = source.name
                }
            }
            HeatmapWidget().update(this@HeatmapConfigActivity, glanceId)

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
private fun HeatmapConfigScreen(
    initial: HeatmapSource,
    onConfirm: (HeatmapSource) -> Unit,
    onCancel: () -> Unit,
) {
    var source by remember { mutableStateOf(initial) }

    Scaffold(
        topBar = { TopAppBar(title = { Text(stringResource(R.string.widget_config_title)) }) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            Text(
                text = stringResource(R.string.heatmap_config_header),
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.primary,
            )

            SourceRow(
                title = stringResource(R.string.heatmap_source_watchlist),
                subtitle = stringResource(R.string.heatmap_source_watchlist_note),
                selected = source == HeatmapSource.WATCHLIST,
                onClick = { source = HeatmapSource.WATCHLIST },
            )
            SourceRow(
                title = stringResource(R.string.heatmap_source_plan),
                subtitle = stringResource(R.string.heatmap_source_plan_note),
                selected = source == HeatmapSource.PLAN,
                onClick = { source = HeatmapSource.PLAN },
            )

            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                OutlinedButton(onClick = onCancel, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_cancel))
                }
                Button(onClick = { onConfirm(source) }, modifier = Modifier.weight(1f)) {
                    Text(stringResource(R.string.action_save))
                }
            }
        }
    }
}

@Composable
private fun SourceRow(
    title: String,
    subtitle: String,
    selected: Boolean,
    onClick: () -> Unit,
) {
    Row(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
            .padding(vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        RadioButton(selected = selected, onClick = onClick)
        Column(modifier = Modifier.padding(start = 4.dp)) {
            Text(text = title, style = MaterialTheme.typography.bodyLarge)
            Text(
                text = subtitle,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
        }
    }
}
