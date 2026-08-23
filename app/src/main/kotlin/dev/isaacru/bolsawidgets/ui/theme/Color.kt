package dev.isaacru.bolsawidgets.ui.theme

import androidx.compose.ui.graphics.Color

/**
 * Gain/loss palette shared by the app and the widgets.
 *
 * The heat map interpolates between [LossStrong], [Neutral] and [GainStrong], so these
 * three have to stay perceptually balanced against both themes.
 */
val GainStrong = Color(0xFF1B8A4B)
val Gain = Color(0xFF2E9E5B)
val Neutral = Color(0xFF6B7280)
val Loss = Color(0xFFD1493F)
val LossStrong = Color(0xFFB3261E)

internal val BrandGreen = Color(0xFF0F3D2E)
internal val BrandGreenLight = Color(0xFF3E6B58)
