package dev.isaacru.bolsawidgets.widget

import android.content.Context
import android.content.Intent
import android.net.Uri
import androidx.compose.ui.unit.DpSize
import androidx.compose.ui.unit.dp
import androidx.glance.unit.ColorProvider
import dev.isaacru.bolsawidgets.ui.MainActivity
import dev.isaacru.bolsawidgets.ui.theme.Gain
import dev.isaacru.bolsawidgets.ui.theme.Loss
import dev.isaacru.bolsawidgets.ui.theme.Neutral

/**
 * Sizes the widgets are laid out against.
 *
 * A home-screen cell is 70dp wide minus 30dp of margin, so four cells is 250dp. Glance
 * picks the largest declared size that fits, and [androidx.glance.LocalSize] reports it.
 */
object WidgetSizes {
    /** 2x2 */
    val Small = DpSize(140.dp, 110.dp)

    /** 4x2 */
    val Wide = DpSize(250.dp, 110.dp)

    /** 4x4 */
    val Tall = DpSize(250.dp, 250.dp)
}

/** Same green/red/grey rule the app uses, wrapped for Glance. */
fun changeColorProvider(value: Double): ColorProvider = when {
    value > 0.0 -> ColorProvider(Gain)
    value < 0.0 -> ColorProvider(Loss)
    else -> ColorProvider(Neutral)
}

/**
 * Direction marker. Drawn as text rather than an icon so it inherits the colour and the
 * font size of the figure next to it, and costs nothing in the RemoteViews bundle.
 */
fun changeArrow(value: Double): String = when {
    value > 0.0 -> "▲"
    value < 0.0 -> "▼"
    else -> "–"
}

private const val DEEP_LINK_SCHEME = "bolsawidgets"

/**
 * Opens the app on a given symbol.
 *
 * The symbol travels in the URI rather than in an extra on purpose: PendingIntents are
 * deduplicated with [Intent.filterEquals], which ignores extras. Two rows carrying only
 * different extras would share one PendingIntent and every row would open the same value.
 */
fun symbolIntent(context: Context, symbol: String): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(DEEP_LINK_SCHEME + "://symbol/" + Uri.encode(symbol))
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

fun openPortfolioIntent(context: Context): Intent =
    Intent(context, MainActivity::class.java).apply {
        action = Intent.ACTION_VIEW
        data = Uri.parse(DEEP_LINK_SCHEME + "://portfolio")
        flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
    }

/** Reads the symbol out of a widget deep link, or null when the intent is not one. */
fun symbolFromDeepLink(uri: Uri?): String? {
    if (uri == null || uri.scheme != DEEP_LINK_SCHEME || uri.host != "symbol") return null
    return uri.lastPathSegment?.takeIf { it.isNotBlank() }
}
