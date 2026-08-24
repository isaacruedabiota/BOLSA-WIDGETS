package dev.isaacru.bolsawidgets.widget.render

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import dev.isaacru.bolsawidgets.domain.model.Candle

/**
 * Draws a sparkline into a bitmap.
 *
 * Glance renders through RemoteViews, which has no arbitrary Canvas, so the only way to
 * get a chart onto the home screen is to draw it here and hand over the result as an
 * Image. Everything is sized from the bitmap it is given, never from fixed constants.
 */
object SparklineRenderer {

    /**
     * @param baseline the previous close, drawn as a dashed reference so it is obvious
     *   whether the line is above or below where the session started. Null hides it.
     */
    fun render(
        candles: List<Candle>,
        size: PixelSize,
        lineColor: Int,
        baseline: Double? = null,
    ): Bitmap {
        val bitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(bitmap)
        val closes = candles.map { it.close }
        if (closes.size < 2) return bitmap

        val strokeWidth = (size.height * 0.045f).coerceIn(2f, 6f)
        val padding = strokeWidth
        val chartWidth = size.width - padding * 2f
        val chartHeight = size.height - padding * 2f
        if (chartWidth <= 0f || chartHeight <= 0f) return bitmap

        var low = closes.min()
        var high = closes.max()
        baseline?.let {
            low = minOf(low, it)
            high = maxOf(high, it)
        }
        // A perfectly flat series would divide by zero; give it a nominal band so the
        // line lands in the middle instead of at an edge.
        val span = (high - low).takeIf { it > 0.0 } ?: 1.0

        fun xAt(index: Int) = padding + chartWidth * index / (closes.size - 1).toFloat()
        fun yAt(value: Double) = padding + chartHeight * (1f - ((value - low) / span).toFloat())

        val linePath = Path().apply {
            moveTo(xAt(0), yAt(closes[0]))
            for (i in 1 until closes.size) lineTo(xAt(i), yAt(closes[i]))
        }

        // Soft wash under the line, so the direction reads even at thumbnail size.
        val fillPath = Path(linePath).apply {
            lineTo(xAt(closes.size - 1), size.height.toFloat())
            lineTo(xAt(0), size.height.toFloat())
            close()
        }
        canvas.drawPath(
            fillPath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = Color.argb(46, Color.red(lineColor), Color.green(lineColor), Color.blue(lineColor))
            },
        )

        baseline?.let { reference ->
            canvas.drawLine(
                padding,
                yAt(reference),
                size.width - padding,
                yAt(reference),
                Paint(Paint.ANTI_ALIAS_FLAG).apply {
                    style = Paint.Style.STROKE
                    this.strokeWidth = strokeWidth * 0.4f
                    color = Color.argb(90, 128, 128, 128)
                    pathEffect = DashPathEffect(floatArrayOf(strokeWidth * 2f, strokeWidth * 2f), 0f)
                },
            )
        }

        canvas.drawPath(
            linePath,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.STROKE
                this.strokeWidth = strokeWidth
                strokeCap = Paint.Cap.ROUND
                strokeJoin = Paint.Join.ROUND
                color = lineColor
            },
        )

        // A dot on the last point: it is the value the user actually cares about.
        canvas.drawCircle(
            xAt(closes.size - 1),
            yAt(closes.last()),
            strokeWidth * 1.1f,
            Paint(Paint.ANTI_ALIAS_FLAG).apply {
                style = Paint.Style.FILL
                color = lineColor
            },
        )
        return bitmap
    }
}
