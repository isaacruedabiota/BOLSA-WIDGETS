package dev.isaacru.bolsawidgets.widget.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.isaacru.bolsawidgets.ui.theme.GainStrong
import dev.isaacru.bolsawidgets.ui.theme.LossStrong

/**
 * Finviz-style colour scale for the heat map: red through grey to green.
 *
 * The scale saturates at [SATURATION_PERCENT] in each direction. Without a cap a single
 * violent mover would wash out every other tile, and the point of the map is comparing
 * the whole portfolio at a glance.
 */
object HeatScale {

    /** Daily change at which the colour stops getting stronger. */
    const val SATURATION_PERCENT = 3.0

    /** Neutral midpoint. Darker than the app grey so white labels stay readable on it. */
    val Flat = Color(0xFF4B5563)

    fun colorFor(changePercent: Double): Color {
        if (changePercent.isNaN()) return Flat
        val t = (changePercent / SATURATION_PERCENT).coerceIn(-1.0, 1.0)
        return when {
            t > 0.0 -> lerp(Flat, GainStrong, t.toFloat())
            t < 0.0 -> lerp(Flat, LossStrong, (-t).toFloat())
            else -> Flat
        }
    }
}
