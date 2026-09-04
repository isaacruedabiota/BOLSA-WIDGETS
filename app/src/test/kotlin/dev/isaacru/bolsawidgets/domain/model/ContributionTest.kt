package dev.isaacru.bolsawidgets.domain.model

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * The heat map compares contributions by area, so the only thing that has to be right here
 * is that two cadences end up on the same scale.
 */
class ContributionTest {

    @Test
    fun `a monthly contribution is already monthly`() {
        val contribution = Contribution(200.0, ContributionPeriod.MONTHLY)

        assertEquals(200.0, contribution.monthlyEur, EPSILON)
    }

    @Test
    fun `a weekly contribution is measured over a real month, not four weeks`() {
        val contribution = Contribution(50.0, ContributionPeriod.WEEKLY)

        // 52 weeks over 12 months, so 50 a week is 216.67 a month. Calling it four weeks
        // would say 200, understating it by 8 percent and reordering the map.
        assertEquals(216.666, contribution.monthlyEur, 0.001)
    }

    @Test
    fun `a weekly and a monthly plan can be compared once both are monthly`() {
        val weekly = Contribution(50.0, ContributionPeriod.WEEKLY)
        val monthly = Contribution(200.0, ContributionPeriod.MONTHLY)

        assertTrue(weekly.monthlyEur > monthly.monthlyEur)
    }

    @Test
    fun `anything that is not a real amount is no plan at all`() {
        assertNull(Contribution.of(null, ContributionPeriod.MONTHLY))
        assertNull(Contribution.of(100.0, null))
        assertNull(Contribution.of(0.0, ContributionPeriod.MONTHLY))
        assertNull(Contribution.of(-50.0, ContributionPeriod.WEEKLY))
        assertNull(Contribution.of(Double.NaN, ContributionPeriod.MONTHLY))
        assertNull(Contribution.of(Double.POSITIVE_INFINITY, ContributionPeriod.MONTHLY))
    }

    @Test
    fun `a real amount survives`() {
        assertEquals(
            Contribution(120.0, ContributionPeriod.MONTHLY),
            Contribution.of(120.0, ContributionPeriod.MONTHLY),
        )
    }

    private companion object {
        const val EPSILON = 1e-9
    }
}
