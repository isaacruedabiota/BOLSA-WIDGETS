package dev.isaacru.bolsawidgets.ui.common

import androidx.compose.runtime.Composable
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
    is UiMessage.RefreshPartial -> stringResource(R.string.refresh_partial, failedCount)
    UiMessage.RefreshFailed -> stringResource(R.string.refresh_failed)
    UiMessage.RefreshNothing -> stringResource(R.string.refresh_nothing)
    is UiMessage.SymbolRemoved -> stringResource(R.string.watchlist_removed, symbol)
    is UiMessage.SymbolAlreadyPresent -> stringResource(R.string.watchlist_already_present, symbol)
}
