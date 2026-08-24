package dev.isaacru.bolsawidgets.widget.render

import dev.isaacru.bolsawidgets.ui.theme.GainStrong
import dev.isaacru.bolsawidgets.ui.theme.LossStrong
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class HeatScaleTest {

    @Test
    fun `a flat day is the neutral colour`() {
        assertEquals(HeatScale.Flat, HeatScale.colorFor(0.0))
    }

    @Test
    fun `the scale saturates at three percent`() {
        assertEquals(GainStrong, HeatScale.colorFor(3.0))
        assertEquals(LossStrong, HeatScale.colorFor(-3.0))
    }

    @Test
    fun `a violent mover cannot push the scale further`() {
        // Without the cap one outlier would wash out every other tile in the map.
        assertEquals(GainStrong, HeatScale.colorFor(25.0))
        assertEquals(LossStrong, HeatScale.colorFor(-40.0))
    }

    @Test
    fun `intermediate moves land between the neutral and the saturated colour`() {
        val half = HeatScale.colorFor(1.5)

        assertTrue(half.green > HeatScale.Flat.green)
        assertTrue(half.green < GainStrong.green || half.red < HeatScale.Flat.red)
        assertTrue(half != HeatScale.Flat)
        assertTrue(half != GainStrong)
    }

    @Test
    fun `losses and gains go in opposite directions`() {
        val gain = HeatScale.colorFor(2.0)
        val loss = HeatScale.colorFor(-2.0)

        assertTrue(gain.green > loss.green)
        assertTrue(loss.red > gain.red)
    }

    @Test
    fun `a missing change is drawn neutral rather than crashing`() {
        assertEquals(HeatScale.Flat, HeatScale.colorFor(Double.NaN))
    }
}
