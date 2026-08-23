package dev.isaacru.bolsawidgets.ui.common

import androidx.compose.material3.SnackbarHostState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import kotlinx.coroutines.flow.Flow

/** Bridges a ViewModel one-shot message flow to a snackbar, resolving its Spanish text. */
@Composable
fun SnackbarMessages(messages: Flow<UiMessage>, hostState: SnackbarHostState) {
    var pending by remember { mutableStateOf<UiMessage?>(null) }
    LaunchedEffect(messages) { messages.collect { pending = it } }

    val current = pending
    if (current != null) {
        val label = current.text()
        LaunchedEffect(current) {
            hostState.showSnackbar(label)
            pending = null
        }
    }
}
