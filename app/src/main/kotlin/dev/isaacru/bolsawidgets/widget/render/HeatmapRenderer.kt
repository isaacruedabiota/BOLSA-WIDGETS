package dev.isaacru.bolsawidgets.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Typeface
import androidx.compose.ui.graphics.toArgb
import dev.isaacru.bolsawidgets.ui.common.Format

/** One position as the heat map needs it. */
data class HeatmapEntry(
    val symbol: String,
    /** Share of the portfolio; only the ratios between entries matter. */
    val weight: Double,
    val changePercent: Double,
)

/**
 * Draws a Finviz-style treemap into a bitmap: area by portfolio weight, colour by the
 * day's move. Labels are only drawn when they actually fit, because a clipped ticker is
 * worse than no ticker.
 */
object HeatmapRenderer {

    private const val TILE_GAP_RATIO = 0.006f
    private const val MIN_LABEL_TEXT_PX = 9f

    fun render(entries: List<HeatmapEntry>, size: PixelSize): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        if (entries.isEmpty()) return bitmap

        val tiles = Treemap.layout(
            weights = entries.map { it.weight },
            width = size.width.toFloat(),
            height = size.height.toFloat(),
        )
        val gap = (minOf(size.width, size.height) * TILE_GAP_RATIO).coerceAtLeast(1f)

        val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
        val symbolPaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.create(Typeface.DEFAULT, Typeface.BOLD)
            color = Color.WHITE
        }
        val changePaint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            textAlign = Paint.Align.CENTER
            typeface = Typeface.DEFAULT
            color = Color.argb(220, 255, 255, 255)
        }

        tiles.forEach { tile ->
            val entry = entries[tile.index]
            fill.color = HeatScale.colorFor(entry.changePercent).toArgb()
            canvas.drawRect(
                tile.left + gap / 2f,
                tile.top + gap / 2f,
                tile.right - gap / 2f,
                tile.bottom - gap / 2f,
                fill,
            )
            drawLabels(canvas, tile, entry, symbolPaint, changePaint)
        }
        return bitmap
    }

    private fun drawLabels(
        canvas: Canvas,
        tile: TreemapTile,
        entry: HeatmapEntry,
        symbolPaint: Paint,
        changePaint: Paint,
    ) {
        val innerWidth = tile.width * 0.90f
        val innerHeight = tile.height * 0.90f

        // Shrink the ticker to fit rather than dropping it: tickers of the same length
        // are not the same width ("ITX.MC" is far narrower than "SAN.MC"), and an
        // all-or-nothing threshold silently leaves some tiles unlabelled.
        var symbolSize = minOf(innerHeight * 0.26f, innerWidth * 0.24f)
        symbolPaint.textSize = symbolSize
        val measured = symbolPaint.measureText(entry.symbol)
        if (measured > innerWidth) {
            symbolSize *= innerWidth / measured
            symbolPaint.textSize = symbolSize
        }
        // Below this the label is unreadable anyway and the tile is left a colour block.
        if (symbolSize < MIN_LABEL_TEXT_PX) return

        val changeText = Format.percent(entry.changePercent)
        changePaint.textSize = symbolSize * 0.78f
        val showChange = symbolSize * 1.9f <= innerHeight &&
            changePaint.measureText(changeText) <= innerWidth

        val centerX = (tile.left + tile.right) / 2f
        val centerY = (tile.top + tile.bottom) / 2f

        if (showChange) {
            canvas.drawText(entry.symbol, centerX, centerY - symbolSize * 0.08f, symbolPaint)
            canvas.drawText(changeText, centerX, centerY + symbolSize * 0.85f, changePaint)
        } else {
            // Only room for one line: the ticker is the part that identifies the tile.
            canvas.drawText(entry.symbol, centerX, centerY + symbolSize * 0.35f, symbolPaint)
        }
    }
}
