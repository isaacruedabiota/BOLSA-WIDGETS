package dev.isaacru.bolsawidgets.ui

import android.content.Intent
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import dagger.hilt.android.AndroidEntryPoint
import dev.isaacru.bolsawidgets.ui.navigation.BolsaApp
import dev.isaacru.bolsawidgets.ui.theme.BolsaWidgetsTheme
import dev.isaacru.bolsawidgets.widget.symbolFromDeepLink

/** Single activity of the app. Every screen is a Compose destination inside [BolsaApp]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    /** Symbol carried by a widget tap, cleared once the navigation has happened. */
    private var pendingSymbol by mutableStateOf<String?>(null)

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        pendingSymbol = symbolFromDeepLink(intent?.data)
        setContent {
            BolsaWidgetsTheme {
                BolsaApp(
                    deepLinkSymbol = pendingSymbol,
                    onDeepLinkHandled = { pendingSymbol = null },
                )
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        // The activity is single-task from the widget PendingIntent, so a second tap
        // arrives here rather than through onCreate.
        setIntent(intent)
        pendingSymbol = symbolFromDeepLink(intent.data)
    }
}
