package dev.isaacru.bolsawidgets.ui.common

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.unit.dp
import dev.isaacru.bolsawidgets.domain.model.Candle

/**
 * The detail chart, drawn with Compose Canvas.
 *
 * Deliberately the same visual language as the widget sparkline — line, wash underneath,
 * dashed reference, dot on the last point — so the chart on the home screen and the one
 * inside the app read as the same thing. Drawn by hand rather than with a chart library:
 * it is one screen, and the geometry was already solved for the widget.
 */
@Composable
fun PriceChart(
    candles: List<Candle>,
    lineColor: Color,
    modifier: Modifier = Modifier,
    baseline: Double? = null,
) {
    val closes = candles.map { it.close }
    if (closes.size < 2) return

    Canvas(modifier = modifier) {
        val strokeWidth = 2.5.dp.toPx()
        val padding = strokeWidth * 2f
        val chartWidth = size.width - padding * 2f
        val chartHeight = size.height - padding * 2f
        if (chartWidth <= 0f || chartHeight <= 0f) return@Canvas

        var low = closes.min()
        var high = closes.max()
        baseline?.let {
            low = minOf(low, it)
            high = maxOf(high, it)
        }
        // A flat series would divide by zero; a nominal band puts the line in the middle
        // instead of pinning it to an edge.
        val span = (high - low).takeIf { it > 0.0 } ?: 1.0

        fun xAt(index: Int) = padding + chartWidth * index / (closes.size - 1).toFloat()
        fun yAt(value: Double) = padding + chartHeight * (1f - ((value - low) / span).toFloat())

        val line = Path().apply {
            moveTo(xAt(0), yAt(closes[0]))
            for (i in 1 until closes.size) lineTo(xAt(i), yAt(closes[i]))
        }

        val wash = Path().apply {
            addPath(line)
            lineTo(xAt(closes.size - 1), size.height)
            lineTo(xAt(0), size.height)
            close()
        }
        drawPath(
            path = wash,
            brush = Brush.verticalGradient(
                listOf(lineColor.copy(alpha = 0.28f), lineColor.copy(alpha = 0.02f)),
            ),
        )

        baseline?.let { reference ->
            val y = yAt(reference)
            drawLine(
                color = Color.Gray.copy(alpha = 0.55f),
                start = Offset(padding, y),
                end = Offset(size.width - padding, y),
                strokeWidth = strokeWidth * 0.45f,
                pathEffect = PathEffect.dashPathEffect(
                    floatArrayOf(strokeWidth * 3f, strokeWidth * 3f),
                ),
            )
        }

        drawPath(
            path = line,
            color = lineColor,
            style = Stroke(width = strokeWidth, cap = androidx.compose.ui.graphics.StrokeCap.Round),
        )

        drawCircle(
            color = lineColor,
            radius = strokeWidth * 1.4f,
            center = Offset(xAt(closes.size - 1), yAt(closes.last())),
        )
    }
}
