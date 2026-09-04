package dev.isaacru.bolsawidgets.widget.render

import dev.isaacru.bolsawidgets.ui.theme.HeatGainDim
import dev.isaacru.bolsawidgets.ui.theme.HeatGainVivid
import dev.isaacru.bolsawidgets.ui.theme.HeatLossDim
import dev.isaacru.bolsawidgets.ui.theme.HeatLossVivid
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatScaleTest {

    @Test
    fun `a flat day is the dimmest tile on the map`() {
        assertEquals(HeatGainDim, HeatScale.colorFor(0.0))
    }

    @Test
    fun `the scale saturates at three percent`() {
        assertEquals(HeatGainVivid, HeatScale.colorFor(3.0))
        assertEquals(HeatLossVivid, HeatScale.colorFor(-3.0))
    }

    @Test
    fun `a violent mover cannot push the scale further`() {
        // Without the cap one outlier would wash out every other tile in the map.
        assertEquals(HeatGainVivid, HeatScale.colorFor(25.0))
        assertEquals(HeatLossVivid, HeatScale.colorFor(-40.0))
    }

    @Test
    fun `a bigger move is a more intense colour`() {
        val small = HeatScale.colorFor(0.5)
        val big = HeatScale.colorFor(2.5)

        assertTrue(big.green > small.green)
        assertTrue(small.green > HeatGainDim.green)
        assertTrue(big.green < HeatGainVivid.green)
    }

    @Test
    fun `the same move up and down only differs in hue`() {
        val gain = HeatScale.colorFor(2.0)
        val loss = HeatScale.colorFor(-2.0)

        assertTrue(gain.green > gain.red)
        assertTrue(loss.red > loss.green)
    }

    @Test
    fun `a barely moved value is nearly the same tile whichever way it went`() {
        // The two dim ends sit next to each other on purpose: "nothing happened" should
        // not shout in red.
        val up = HeatScale.colorFor(0.02)
        val down = HeatScale.colorFor(-0.02)

        assertTrue(Math.abs(up.red - down.red) < 0.12f)
        assertTrue(Math.abs(up.green - down.green) < 0.12f)
        assertTrue(Math.abs(up.blue - down.blue) < 0.12f)
    }

    @Test
    fun `no change at all is drawn apart from the ramp rather than as a flat day`() {
        assertEquals(HeatScale.Flat, HeatScale.colorFor(Double.NaN))
        assertTrue(HeatScale.Flat != HeatGainDim)
        assertTrue(HeatScale.Flat != HeatLossDim)
    }
}
