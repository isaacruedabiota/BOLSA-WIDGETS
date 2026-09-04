package dev.isaacru.bolsawidgets.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Gain/loss palette shared by the app and the widgets.
 *
 * These have to stay perceptually balanced against both themes, because the same green
 * and the same red are used on the app's white background and on a widget.
 */
val GainStrong = Color(0xFF1B8A4B)
val Gain = Color(0xFF2E9E5B)
val Neutral = Color(0xFF6B7280)
val Loss = Color(0xFFD1493F)
val LossStrong = Color(0xFFB3261E)

/**
 * Heat map ramp. Its own palette, and always dark: the map is a block of colour, so it
 * ignores the system theme instead of turning into a white slab in daylight.
 *
 * A tile says two things and nothing else — which way the value moved (hue) and how far
 * (intensity) — so each side runs from an almost-black tint at zero to a saturated colour
 * at the saturation point. The dim ends are deliberately close to each other: a value that
 * barely moved should read as "nothing happened" whichever way it went.
 *
 * The vivid ends stop short of neon so white labels stay legible on top of them.
 */
val HeatGainDim = Color(0xFF0E241A)
val HeatGainVivid = Color(0xFF1FA65C)
val HeatLossDim = Color(0xFF261417)
val HeatLossVivid = Color(0xFFC93B31)

/** Background of the heat map widget, and the ground its corners are cut against. */
val HeatBackground = Color(0xFF0B0E10)

/** Header text on [HeatBackground]: present, but never competing with the tiles. */
val HeatOnBackground = Color(0xFF8A9199)

internal val BrandGreen = Color(0xFF0F3D2E)
internal val BrandGreenLight = Color(0xFF3E6B58)
