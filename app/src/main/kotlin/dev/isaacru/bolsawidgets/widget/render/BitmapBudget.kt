package dev.isaacru.bolsawidgets.widget.render

import kotlin.math.max
import kotlin.math.roundToInt
import kotlin.math.sqrt

/** A bitmap size in pixels. */
data class PixelSize(val width: Int, val height: Int) {
    val pixels: Int get() = width * height
}

/**
 * Keeps rendered bitmaps inside the RemoteViews transaction budget.
 *
 * A widget bundle that goes past roughly 1.5 MB kills the update with a
 * TransactionTooLargeException, and the failure lands on the launcher rather than on us.
 * So the drawing size is derived from the real widget size but capped: on a 3x screen a
 * 4x4 widget would otherwise be 750x750 px, which is 2.25 MB of ARGB_8888 on its own.
 */
object BitmapBudget {

    /** Well under the ~1.5 MB limit, leaving room for the rest of the RemoteViews tree. */
    const val MAX_BYTES = 900_000

    private const val BYTES_PER_PIXEL = 4

    val MAX_PIXELS = MAX_BYTES / BYTES_PER_PIXEL

    /**
     * The size to draw at for a widget measured [widthDp] by [heightDp] on a screen of
     * [density], scaled down proportionally when it would not fit the budget.
     */
    fun sizeFor(widthDp: Float, heightDp: Float, density: Float): PixelSize {
        val width = (widthDp * density).roundToInt()
        val height = (heightDp * density).roundToInt()
        return fit(width, height)
    }

    /** Scales [width] by [height] down to the budget, preserving the aspect ratio. */
    fun fit(width: Int, height: Int): PixelSize {
        val safeWidth = max(1, width)
        val safeHeight = max(1, height)
        val pixels = safeWidth.toLong() * safeHeight.toLong()
        if (pixels <= MAX_PIXELS) return PixelSize(safeWidth, safeHeight)

        val scale = sqrt(MAX_PIXELS.toDouble() / pixels.toDouble())
        return PixelSize(
            width = max(1, (safeWidth * scale).roundToInt()),
            height = max(1, (safeHeight * scale).roundToInt()),
        )
    }
}
