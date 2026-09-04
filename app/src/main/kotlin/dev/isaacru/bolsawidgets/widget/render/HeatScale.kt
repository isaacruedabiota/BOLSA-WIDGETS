package dev.isaacru.bolsawidgets.widget.render

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.lerp
import dev.isaacru.bolsawidgets.ui.theme.HeatGainDim
import dev.isaacru.bolsawidgets.ui.theme.HeatGainVivid
import dev.isaacru.bolsawidgets.ui.theme.HeatLossDim
import dev.isaacru.bolsawidgets.ui.theme.HeatLossVivid
import kotlin.math.abs

/**
 * Colour scale for the heat map: green up, red down, dim to vivid with the size of the
 * day's move.
 *
 * Two colours and nothing in between. A grey midpoint would be a third thing to read on a
 * map whose whole job is to be understood without reading, so a value that hardly moved
 * gets the darkest shade of its own side rather than its own colour.
 *
 * The scale saturates at [SATURATION_PERCENT] in each direction. Without a cap a single
 * violent mover would wash out every other tile, and the point of the map is comparing the
 * whole portfolio at a glance.
 */
object HeatScale {

    /** Daily change at which the colour stops getting stronger. */
    const val SATURATION_PERCENT = 3.0

    /**
     * Drawn only when there is no change to show at all. Not part of the ramp: a tile with
     * no data must not pass for a flat day.
     */
    val Flat = Color(0xFF1B2126)

    fun colorFor(changePercent: Double): Color {
        if (changePercent.isNaN()) return Flat
        val t = (abs(changePercent) / SATURATION_PERCENT).coerceIn(0.0, 1.0).toFloat()
        return when {
            changePercent < 0.0 -> lerp(HeatLossDim, HeatLossVivid, t)
            // Zero lands on the green side, where it is the darkest tile on the map and
            // reads as "flat" either way.
            else -> lerp(HeatGainDim, HeatGainVivid, t)
        }
    }
}
