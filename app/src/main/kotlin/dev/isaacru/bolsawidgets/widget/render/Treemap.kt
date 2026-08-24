package dev.isaacru.bolsawidgets.widget.render

import kotlin.math.max
import kotlin.math.min

/** One tile of the treemap, in pixels, referring back to the input by [index]. */
data class TreemapTile(
    val index: Int,
    val left: Float,
    val top: Float,
    val right: Float,
    val bottom: Float,
) {
    val width: Float get() = right - left
    val height: Float get() = bottom - top
    val area: Float get() = width * height
}

/**
 * Squarified treemap layout.
 *
 * Tiles fill the rectangle exactly and never overlap, with areas proportional to the
 * weights. "Squarified" means it lays tiles out in rows chosen to keep aspect ratios as
 * close to square as possible, which is what makes a Finviz-style map readable instead of
 * a set of unusable slivers.
 *
 * Pure geometry: no Android types, so the layout is unit tested directly.
 */
object Treemap {

    /**
     * Lays [weights] out inside a [width] by [height] rectangle at the origin.
     * Non-positive weights are dropped; the result is ordered largest tile first.
     */
    fun layout(weights: List<Double>, width: Float, height: Float): List<TreemapTile> {
        if (width <= 0f || height <= 0f) return emptyList()

        val positive = weights
            .mapIndexed { index, weight -> index to weight }
            .filter { it.second > 0.0 }
        if (positive.isEmpty()) return emptyList()

        val total = positive.sumOf { it.second }
        // Scale the weights so their areas add up to the rectangle exactly.
        val scale = width.toDouble() * height.toDouble() / total
        val remaining = ArrayDeque(
            positive
                .map { (index, weight) -> Item(index, weight * scale) }
                .sortedByDescending { it.area },
        )

        val tiles = mutableListOf<TreemapTile>()
        var left = 0.0
        var top = 0.0
        var freeWidth = width.toDouble()
        var freeHeight = height.toDouble()

        while (remaining.isNotEmpty()) {
            val shorter = min(freeWidth, freeHeight)
            if (shorter <= 0.0) break

            val row = mutableListOf(remaining.removeFirst())
            while (remaining.isNotEmpty()) {
                val candidate = row + remaining.first()
                if (worstAspectRatio(candidate, shorter) <= worstAspectRatio(row, shorter)) {
                    row.add(remaining.removeFirst())
                } else {
                    break
                }
            }

            val rowArea = row.sumOf { it.area }
            if (freeWidth >= freeHeight) {
                // The free space is wider than tall: the row becomes a column on the left.
                val columnWidth = rowArea / freeHeight
                var y = top
                row.forEach { item ->
                    val tileHeight = item.area / columnWidth
                    tiles += TreemapTile(
                        index = item.index,
                        left = left.toFloat(),
                        top = y.toFloat(),
                        right = (left + columnWidth).toFloat(),
                        bottom = (y + tileHeight).toFloat(),
                    )
                    y += tileHeight
                }
                left += columnWidth
                freeWidth -= columnWidth
            } else {
                val rowHeight = rowArea / freeWidth
                var x = left
                row.forEach { item ->
                    val tileWidth = item.area / rowHeight
                    tiles += TreemapTile(
                        index = item.index,
                        left = x.toFloat(),
                        top = top.toFloat(),
                        right = (x + tileWidth).toFloat(),
                        bottom = (top + rowHeight).toFloat(),
                    )
                    x += tileWidth
                }
                top += rowHeight
                freeHeight -= rowHeight
            }
        }
        return tiles
    }

    private data class Item(val index: Int, val area: Double)

    /**
     * The worst (largest) aspect ratio a row would have if it were laid out now. The
     * squarified algorithm keeps adding tiles to a row while this keeps getting better.
     */
    private fun worstAspectRatio(row: List<Item>, shorterSide: Double): Double {
        if (row.isEmpty()) return Double.MAX_VALUE
        val sum = row.sumOf { it.area }
        if (sum <= 0.0) return Double.MAX_VALUE
        val largest = row.maxOf { it.area }
        val smallest = row.minOf { it.area }
        if (smallest <= 0.0) return Double.MAX_VALUE
        val side2 = shorterSide * shorterSide
        val sum2 = sum * sum
        return max(side2 * largest / sum2, sum2 / (side2 * smallest))
    }
}
