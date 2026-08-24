package dev.isaacru.bolsawidgets.widget.render

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import kotlin.math.abs

/**
 * Going over the RemoteViews transaction limit kills the widget update, and the crash
 * lands on the launcher rather than on the app, so the cap has to hold.
 */
class BitmapBudgetTest {

    @Test
    fun `a small bitmap is left alone`() {
        val size = BitmapBudget.fit(300, 200)

        assertEquals(300, size.width)
        assertEquals(200, size.height)
    }

    @Test
    fun `an oversized bitmap is scaled down into the budget`() {
        // A 4x4 widget on a 3x screen: 750x750 would be 2.25 MB of ARGB_8888.
        val size = BitmapBudget.fit(750, 750)

        assertTrue("still " + size.pixels + " pixels", size.pixels <= BitmapBudget.MAX_PIXELS)
        assertTrue(size.width < 750)
    }

    @Test
    fun `scaling down keeps the aspect ratio`() {
        val size = BitmapBudget.fit(2000, 1000)

        assertEquals(2.0, size.width.toDouble() / size.height, 0.02)
        assertTrue(size.pixels <= BitmapBudget.MAX_PIXELS)
    }

    @Test
    fun `converts dp to pixels at the screen density`() {
        val size = BitmapBudget.sizeFor(widthDp = 250f, heightDp = 110f, density = 2f)

        assertEquals(500, size.width)
        assertEquals(220, size.height)
    }

    @Test
    fun `never returns a zero sized bitmap`() {
        assertEquals(PixelSize(1, 1), BitmapBudget.fit(0, 0))
        assertEquals(PixelSize(1, 1), BitmapBudget.fit(-10, -10))
        assertTrue(BitmapBudget.sizeFor(0f, 0f, 3f).pixels >= 1)
    }

    @Test
    fun `a very wide strip stays inside the budget too`() {
        val size = BitmapBudget.fit(4000, 120)

        assertTrue(size.pixels <= BitmapBudget.MAX_PIXELS)
        assertTrue(abs(size.width.toDouble() / size.height - 4000.0 / 120.0) < 1.0)
    }
}
