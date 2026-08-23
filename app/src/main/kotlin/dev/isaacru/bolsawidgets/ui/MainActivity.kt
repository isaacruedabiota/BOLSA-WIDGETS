package dev.isaacru.bolsawidgets.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import dagger.hilt.android.AndroidEntryPoint
import dev.isaacru.bolsawidgets.ui.navigation.BolsaApp
import dev.isaacru.bolsawidgets.ui.theme.BolsaWidgetsTheme

/** Single activity of the app. Every screen is a Compose destination inside [BolsaApp]. */
@AndroidEntryPoint
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        enableEdgeToEdge()
        super.onCreate(savedInstanceState)
        setContent {
            BolsaWidgetsTheme {
                BolsaApp()
            }
        }
    }
}
