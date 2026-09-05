package dev.isaacru.bolsawidgets.ui.settings

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.RadioButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.lifecycle.viewmodel.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.isaacru.bolsawidgets.BuildConfig
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.model.ThemeMode
import dev.isaacru.bolsawidgets.domain.model.UserPreferences
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import dev.isaacru.bolsawidgets.domain.share.WatchlistShare
import dev.isaacru.bolsawidgets.ui.common.SnackbarMessages
import dev.isaacru.bolsawidgets.ui.common.positionsLabel
import dev.isaacru.bolsawidgets.ui.common.watchlistLabel
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.time.LocalDate

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    modifier: Modifier = Modifier,
    viewModel: SettingsViewModel = hiltViewModel(),
) {
    val state by viewModel.uiState.collectAsStateWithLifecycle()
    val preferences = state.preferences
    val context = LocalContext.current
    val snackbarHostState = remember { SnackbarHostState() }

    SnackbarMessages(viewModel.messages, snackbarHostState)

    var pasting by remember { mutableStateOf(false) }

    // The sharing lives here, not in the ViewModel: handing text to another app is an
    // Android thing, the same reason the file pickers are on this screen.
    LaunchedEffect(Unit) {
        viewModel.listToShare.collect { text ->
            val intent = Intent(Intent.ACTION_SEND).apply {
                type = "text/plain"
                putExtra(Intent.EXTRA_SUBJECT, WatchlistShare.HEADER)
                putExtra(Intent.EXTRA_TEXT, text)
            }
            context.startActivity(
                Intent.createChooser(intent, context.getString(R.string.share_list)),
            )
        }
    }

    // The document lives outside the app, so the read and the write happen here and the
    // ViewModel only ever sees text. That is what keeps it free of a Context.
    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("text/csv"),
    ) { uri ->
        if (uri != null) {
            viewModel.export { csv ->
                withContext(Dispatchers.IO) {
                    context.contentResolver.openOutputStream(uri)?.use { output ->
                        output.write(csv.toByteArray())
                        true
                    } ?: false
                }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument(),
    ) { uri ->
        if (uri != null) {
            viewModel.stageImport {
                withContext(Dispatchers.IO) {
                    context.contentResolver.openInputStream(uri)
                        ?.use { it.readBytes().decodeToString() }
                }
            }
        }
    }

    Scaffold(
        modifier = modifier,
        topBar = { TopAppBar(title = { Text(stringResource(R.string.settings_title)) }) },
        snackbarHost = { SnackbarHost(snackbarHostState) },
    ) { innerPadding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp),
        ) {
            SectionHeader(stringResource(R.string.settings_provider_header))
            ProviderId.entries.forEach { provider ->
                ChoiceRow(
                    title = provider.displayName,
                    subtitle = stringResource(
                        when (provider) {
                            ProviderId.YAHOO -> R.string.settings_provider_yahoo_note
                            ProviderId.TWELVE_DATA -> R.string.settings_provider_twelvedata_note
                        },
                    ),
                    selected = preferences.providerId == provider,
                    onClick = { viewModel.setProvider(provider) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(stringResource(R.string.settings_theme_header))
            Text(
                text = stringResource(R.string.settings_theme_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
            )
            Row(
                modifier = Modifier.fillMaxWidth().padding(top = 8.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                ThemeMode.entries.forEach { mode ->
                    FilterChip(
                        selected = preferences.themeMode == mode,
                        onClick = { viewModel.setThemeMode(mode) },
                        label = { Text(mode.label()) },
                    )
                }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(stringResource(R.string.settings_privacy_header))
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { viewModel.setPrivacyMode(!preferences.privacyMode) }
                    .padding(vertical = 10.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.settings_privacy_title),
                        style = MaterialTheme.typography.bodyLarge,
                    )
                    Text(
                        text = stringResource(R.string.settings_privacy_body),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                    )
                }
                Switch(
                    checked = preferences.privacyMode,
                    onCheckedChange = viewModel::setPrivacyMode,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(stringResource(R.string.settings_refresh_header))
            Text(
                text = stringResource(R.string.settings_refresh_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            UserPreferences.REFRESH_OPTIONS.forEach { minutes ->
                ChoiceRow(
                    title = if (minutes < 60) {
                        stringResource(R.string.settings_refresh_minutes, minutes)
                    } else {
                        stringResource(R.string.settings_refresh_hours, minutes / 60)
                    },
                    subtitle = null,
                    selected = preferences.refreshIntervalMinutes == minutes,
                    onClick = { viewModel.setRefreshInterval(minutes) },
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(stringResource(R.string.settings_share_header))
            Text(
                text = stringResource(R.string.settings_share_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = viewModel::shareList,
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.share_list)) }
                OutlinedButton(
                    onClick = { pasting = true },
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.share_import)) }
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(stringResource(R.string.settings_backup_header))
            Text(
                text = stringResource(R.string.settings_backup_body),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 8.dp),
            )
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Button(
                    onClick = { exportLauncher.launch("bolsa-widgets-" + LocalDate.now() + ".csv") },
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.settings_export)) }
                OutlinedButton(
                    // Anything: a CSV arrives labelled text/csv, text/plain or a
                    // spreadsheet type depending on where it came from, and a strict
                    // filter would hide real backups.
                    onClick = { importLauncher.launch(arrayOf("*/*")) },
                    enabled = !state.isWorking,
                    modifier = Modifier.weight(1f),
                ) { Text(stringResource(R.string.settings_import)) }
            }
            if (state.isWorking) {
                CircularProgressIndicator(
                    modifier = Modifier.padding(top = 8.dp).size(20.dp),
                    strokeWidth = 2.dp,
                )
            }

            HorizontalDivider(modifier = Modifier.padding(vertical = 12.dp))

            SectionHeader(stringResource(R.string.settings_about_header))
            Text(
                text = stringResource(R.string.settings_about_body, BuildConfig.VERSION_NAME),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(bottom = 24.dp),
            )
        }
    }

    if (pasting) {
        PasteListDialog(
            onDismiss = { pasting = false },
            onImport = { text ->
                viewModel.importSharedList(text)
                pasting = false
            },
        )
    }

    val staged = state.pendingImport
    if (staged != null) {
        AlertDialog(
            onDismissRequest = viewModel::cancelImport,
            title = { Text(stringResource(R.string.backup_import_title)) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        stringResource(
                            R.string.backup_import_body,
                            positionsLabel(staged.backup.positions.size),
                            watchlistLabel(staged.backup.watchlist.size),
                        ),
                    )
                    if (staged.skippedRows.isNotEmpty()) {
                        Text(
                            text = pluralStringResource(
                                R.plurals.backup_import_skipped,
                                staged.skippedRows.size,
                                staged.skippedRows.size,
                            ),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.error,
                        )
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmImport) {
                    Text(stringResource(R.string.action_replace))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::cancelImport) {
                    Text(stringResource(R.string.action_cancel))
                }
            },
        )
    }
}

@Composable
private fun SectionHeader(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.titleSmall,
        color = MaterialTheme.colorScheme.primary,
        modifier = Modifier.padding(top = 8.dp, bottom = 4.dp),
    )
}

@Composable
private fun ChoiceRow(
    title: String,
    subtitle: String?,
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
            if (subtitle != null) {
                Text(
                    text = subtitle,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
            }
        }
    }
}

@Composable
private fun ThemeMode.label(): String = stringResource(
    when (this) {
        ThemeMode.SYSTEM -> R.string.settings_theme_system
        ThemeMode.LIGHT -> R.string.settings_theme_light
        ThemeMode.DARK -> R.string.settings_theme_dark
    },
)

/**
 * Taking in a list somebody sent.
 *
 * A paste box rather than a file picker, because a shared list arrives in a chat: the
 * whole message can go in and the parser keeps only the lines that carry a ticker.
 */
@Composable
private fun PasteListDialog(
    onDismiss: () -> Unit,
    onImport: (String) -> Unit,
) {
    var text by remember { mutableStateOf("") }
    val found = remember(text) { WatchlistShare.decode(text).size }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(stringResource(R.string.share_import_title)) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                Text(
                    text = stringResource(R.string.share_import_body),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                )
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text(stringResource(R.string.share_import_label)) },
                    minLines = 4,
                    maxLines = 8,
                    modifier = Modifier.fillMaxWidth(),
                )
                Text(
                    // Counted as you paste, so nobody presses the button hoping.
                    text = pluralStringResource(R.plurals.share_import_found, found, found),
                    style = MaterialTheme.typography.bodyMedium,
                )
            }
        },
        confirmButton = {
            TextButton(onClick = { onImport(text) }, enabled = found > 0) {
                Text(stringResource(R.string.share_import_add))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text(stringResource(R.string.action_cancel)) }
        },
    )
}
