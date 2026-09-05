package dev.isaacru.bolsawidgets.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.PorterDuff
import android.graphics.PorterDuffXfermode
import android.graphics.RectF
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import dev.isaacru.bolsawidgets.ui.common.Format
import kotlin.math.ceil
import kotlin.math.floor

/** One position as the heat map needs it. */
data class HeatmapEntry(
    val symbol: String,
    /** Share of the portfolio; only the ratios between entries matter. */
    val weight: Double,
    val changePercent: Double,
    /**
     * What the tile prints, which is the ticker unless the user renamed the value. Kept
     * apart from [symbol] so the ticker survives as the fallback for a tile too small to
     * hold a name.
     */
    val label: String = symbol,
)

/**
 * Draws the treemap into a bitmap: area by portfolio weight, colour by the day's move.
 *
 * The tiles are flush against each other, with no gap and no outline. The colour blocks
 * are the drawing; a grid of separators between them is one more thing on screen saying
 * nothing that the change of colour does not already say.
 */
object HeatmapRenderer {

    private const val MIN_LABEL_TEXT_PX = 9f

    /**
     * Renders [entries] at [size]. [cornerRadiusPx] rounds the corners so the map can run
     * to the edges of a rounded widget without poking out of it.
     */
    fun render(
        entries: List<HeatmapEntry>,
        size: PixelSize,
        cornerRadiusPx: Float = 0f,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (entries.isEmpty()) return bitmap

        val tiles = Treemap.layout(
            weights = entries.map { it.weight },
            width = size.width.toFloat(),
            height = size.height.toFloat(),
        )

        // No anti-aliasing on the fills, and every edge snapped outwards: neighbouring
        // tiles then overlap by at most a pixel instead of leaving a hairline of
        // background between them, which is exactly the grid the design is without.
        val fill = Paint().apply {
            style = Paint.Style.FILL
            isAntiAlias = false
        }
        val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
        }
        val changePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = Color.argb(170, 255, 255, 255)
        }

        tiles.forEach { tile ->
            val entry = entries[tile.index]
            fill.color = HeatScale.colorFor(entry.changePercent).toArgb()
            canvas.drawRect(
                floor(tile.left),
                floor(tile.top),
                ceil(tile.right),
                ceil(tile.bottom),
                fill,
            )
            drawLabels(canvas, tile, entry, symbolPaint, changePaint)
        }

        if (cornerRadiusPx > 0f) roundCorners(canvas, size, cornerRadiusPx)
        return bitmap
    }

    /**
     * Cuts the corners out of what has already been drawn.
     *
     * Clipping first would leave them jagged; masking afterwards with DST_IN keeps the
     * destination only where the rounded path is opaque, so the curve is as smooth as the
     * path that drew it.
     */
    private fun roundCorners(canvas: Canvas, size: PixelSize, radius: Float) {
        val r = radius.coerceAtMost(minOf(size.width, size.height) / 2f)
        val path = Path().apply {
            addRoundRect(
                RectF(0f, 0f, size.width.toFloat(), size.height.toFloat()),
                r,
                r,
                Path.Direction.CW,
            )
        }
        val mask = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            color = Color.BLACK
            xfermode = PorterDuffXfermode(PorterDuff.Mode.DST_IN)
        }
        canvas.drawPath(path, mask)
    }

    private fun drawLabels(
        canvas: Canvas,
        tile: TreemapTile,
        entry: HeatmapEntry,
        symbolPaint: Paint,
        changePaint: Paint,
    ) {
        val innerWidth = tile.width * 0.86f
        val innerHeight = tile.height * 0.86f
        val ideal = minOf(innerHeight * 0.24f, innerWidth * 0.22f)

        // The user's name first, the ticker behind it. A name is longer than a ticker, so
        // on a small tile it shrinks past reading; falling back then keeps the tile
        // identified instead of leaving it a mute block of colour.
        var text = entry.label
        var symbolSize = fittedSize(symbolPaint, text, ideal, innerWidth)
        if (symbolSize < MIN_LABEL_TEXT_PX && entry.label != entry.symbol) {
            text = entry.symbol
            symbolSize = fittedSize(symbolPaint, text, ideal, innerWidth)
        }
        // Below this the label is unreadable anyway and the tile is left a colour block.
        if (symbolSize < MIN_LABEL_TEXT_PX) return
        symbolPaint.textSize = symbolSize

        val changeText = Format.percent(entry.changePercent)
        changePaint.textSize = symbolSize * 0.76f
        val showChange = symbolSize * 1.9f <= innerHeight &&
            changePaint.measureText(changeText) <= innerWidth

        val centerX = (tile.left + tile.right) / 2f
        val centerY = (tile.top + tile.bottom) / 2f

        if (showChange) {
            canvas.drawText(text, centerX, centerY - symbolSize * 0.10f, symbolPaint)
            canvas.drawText(changeText, centerX, centerY + symbolSize * 0.86f, changePaint)
        } else {
            // Only room for one line: the name is the part that identifies the tile.
            canvas.drawText(text, centerX, centerY + symbolSize * 0.35f, symbolPaint)
        }
    }

    /**
     * The largest size no bigger than [ideal] at which [text] fits into [maxWidth].
     *
     * Shrinking beats an all-or-nothing threshold because labels of the same length are
     * not the same width: "ITX.MC" is far narrower than "SAN.MC".
     */
    private fun fittedSize(paint: Paint, text: String, ideal: Float, maxWidth: Float): Float {
        paint.textSize = ideal
        val measured = paint.measureText(text)
        return if (measured > maxWidth) ideal * maxWidth / measured else ideal
    }
}
