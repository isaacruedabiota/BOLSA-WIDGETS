package dev.isaacru.bolsawidgets.widget.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The heat map is only honest if the tiles really are proportional and really do tile the
 * rectangle, so those two properties are what these tests check.
 */
class TreemapTest {

    private val width = 400f
    private val height = 300f

    @Test
    fun `a single position fills the whole rectangle`() {
        val tiles = Treemap.layout(listOf(1.0), width, height)

        val tile = tiles.single()
        assertEquals(0f, tile.left, EPSILON)
        assertEquals(0f, tile.top, EPSILON)
        assertEquals(width, tile.right, EPSILON)
        assertEquals(height, tile.bottom, EPSILON)
    }

    @Test
    fun `tiles cover the rectangle exactly`() {
        val tiles = Treemap.layout(listOf(6.0, 6.0, 4.0, 3.0, 2.0, 2.0, 1.0), width, height)

        val covered = tiles.sumOf { it.area.toDouble() }
        assertEquals(width.toDouble() * height, covered, 0.5)
        tiles.forEach { tile ->
            assertTrue("tile outside the rectangle: " + tile, tile.left >= -EPSILON)
            assertTrue("tile outside the rectangle: " + tile, tile.top >= -EPSILON)
            assertTrue("tile outside the rectangle: " + tile, tile.right <= width + EPSILON)
            assertTrue("tile outside the rectangle: " + tile, tile.bottom <= height + EPSILON)
        }
    }

    @Test
    fun `tiles never overlap`() {
        val tiles = Treemap.layout(listOf(5.0, 4.0, 3.0, 3.0, 2.0, 1.0, 1.0, 1.0), width, height)

        for (i in tiles.indices) {
            for (j in i + 1 until tiles.size) {
                assertTrue("overlap between " + tiles[i] + " and " + tiles[j], !tiles[i].overlaps(tiles[j]))
            }
        }
    }

    @Test
    fun `area is proportional to weight`() {
        val weights = listOf(50.0, 30.0, 20.0)

        val tiles = Treemap.layout(weights, width, height).associateBy { it.index }

        val total = (width * height).toDouble()
        weights.forEachIndexed { index, weight ->
            val expected = total * weight / weights.sum()
            assertEquals(expected, tiles.getValue(index).area.toDouble(), 1.0)
        }
    }

    @Test
    fun `the biggest position gets the biggest tile`() {
        val tiles = Treemap.layout(listOf(1.0, 9.0, 3.0), width, height).associateBy { it.index }

        assertTrue(tiles.getValue(1).area > tiles.getValue(2).area)
        assertTrue(tiles.getValue(2).area > tiles.getValue(0).area)
    }

    @Test
    fun `keeps tiles reasonably square rather than producing slivers`() {
        val tiles = Treemap.layout(List(12) { 1.0 }, 400f, 400f)

        // Twelve equal tiles in a square: a naive slice-and-dice would give 33x400
        // ribbons with a ratio of 12. Squarified should stay well under 3.
        val worst = tiles.maxOf { maxOf(it.width / it.height, it.height / it.width) }
        assertTrue("worst aspect ratio was " + worst, worst < 3.0f)
    }

    @Test
    fun `ignores positions with no value`() {
        val tiles = Treemap.layout(listOf(1.0, 0.0, -5.0, 3.0), width, height)

        assertEquals(setOf(0, 3), tiles.map { it.index }.toSet())
    }

    @Test
    fun `degenerate inputs produce nothing instead of crashing`() {
        assertTrue(Treemap.layout(emptyList(), width, height).isEmpty())
        assertTrue(Treemap.layout(listOf(1.0), 0f, height).isEmpty())
        assertTrue(Treemap.layout(listOf(1.0), width, -1f).isEmpty())
        assertTrue(Treemap.layout(listOf(0.0, 0.0), width, height).isEmpty())
    }

    private fun TreemapTile.overlaps(other: TreemapTile): Boolean {
        val horizontal = left < other.right - EPSILON && other.left < right - EPSILON
        val vertical = top < other.bottom - EPSILON && other.top < bottom - EPSILON
        return horizontal && vertical
    }

    private companion object {
        const val EPSILON = 0.01f
    }
}
