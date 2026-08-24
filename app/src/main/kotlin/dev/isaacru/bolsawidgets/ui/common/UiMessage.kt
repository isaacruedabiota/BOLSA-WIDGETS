package dev.isaacru.bolsawidgets.ui.common

import androidx.compose.runtime.Composable
import androidx.compose.ui.res.pluralStringResource
import androidx.compose.ui.res.stringResource
import dev.isaacru.bolsawidgets.R
import dev.isaacru.bolsawidgets.domain.repository.RefreshOutcome

/**
 * One-shot messages a ViewModel sends to the snackbar.
 *
 * Kept as a type rather than a ready-made string so ViewModels stay free of Android
 * resources and the Spanish wording lives only in strings.xml.
 */
sealed interface UiMessage {

    data object RefreshDone : UiMessage

    data class RefreshPartial(val failedCount: Int) : UiMessage

    data object RefreshFailed : UiMessage

    data object RefreshNothing : UiMessage

    data class SymbolRemoved(val symbol: String) : UiMessage

    data class SymbolAlreadyPresent(val symbol: String) : UiMessage

    data class BackupExported(val positions: Int, val watchlist: Int) : UiMessage

    data object BackupExportFailed : UiMessage

    data class BackupImported(val positions: Int, val watchlist: Int) : UiMessage

    data object BackupImportFailed : UiMessage

    data object BackupImportEmpty : UiMessage

    data class PurchaseAdded(val symbol: String) : UiMessage

    companion object {
        /** Turns a refresh result into the message that describes it honestly. */
        fun of(outcome: RefreshOutcome): UiMessage = when {
            outcome.requested.isEmpty() -> RefreshNothing
            outcome.isCompleteFailure -> RefreshFailed
            outcome.failed.isNotEmpty() -> RefreshPartial(outcome.failed.size)
            else -> RefreshDone
        }
    }
}

@Composable
fun UiMessage.text(): String = when (this) {
    UiMessage.RefreshDone -> stringResource(R.string.refresh_done)
    is UiMessage.RefreshPartial ->
        pluralStringResource(R.plurals.refresh_partial, failedCount, failedCount)
    UiMessage.RefreshFailed -> stringResource(R.string.refresh_failed)
    UiMessage.RefreshNothing -> stringResource(R.string.refresh_nothing)
    is UiMessage.SymbolRemoved -> stringResource(R.string.watchlist_removed, symbol)
    is UiMessage.SymbolAlreadyPresent -> stringResource(R.string.watchlist_already_present, symbol)
    is UiMessage.BackupExported -> stringResource(
        R.string.backup_export_done,
        positionsLabel(positions),
        watchlistLabel(watchlist),
    )
    UiMessage.BackupExportFailed -> stringResource(R.string.backup_export_failed)
    is UiMessage.BackupImported -> stringResource(
        R.string.backup_import_done,
        positionsLabel(positions),
        watchlistLabel(watchlist),
    )
    UiMessage.BackupImportFailed -> stringResource(R.string.backup_import_failed)
    UiMessage.BackupImportEmpty -> stringResource(R.string.backup_import_empty)
    is UiMessage.PurchaseAdded -> stringResource(R.string.purchase_added, symbol)
}

/** "1 posición" / "3 posiciones", for sentences that mention both counts. */
@Composable
fun positionsLabel(count: Int): String =
    pluralStringResource(R.plurals.count_positions, count, count)

@Composable
fun watchlistLabel(count: Int): String =
    pluralStringResource(R.plurals.count_watchlist, count, count)
