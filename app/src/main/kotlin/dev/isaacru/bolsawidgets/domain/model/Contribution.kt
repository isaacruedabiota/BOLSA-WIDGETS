package dev.isaacru.bolsawidgets.domain.model

/** How often money goes into a value. */
enum class ContributionPeriod {
    WEEKLY,
    MONTHLY,
}

/**
 * What the user puts into one value on a recurring basis, in euros.
 *
 * A savings plan is a flow, not a holding, so this is deliberately not a [Position]: there
 * is no quantity, no purchase price and no P&L here, only "this is how much of my money
 * goes here".
 *
 * Everything comparable is expressed as [monthlyEur], because two values on different
 * cadences can only be put on the same scale once they are measured over the same stretch
 * of time. A month is 52/12 weeks, not four: four would understate a weekly contribution
 * by about 8 %, which is enough to reorder a map.
 */
data class Contribution(
    val amountEur: Double,
    val period: ContributionPeriod,
) {
    val monthlyEur: Double
        get() = when (period) {
            ContributionPeriod.WEEKLY -> amountEur * WEEKS_PER_MONTH
            ContributionPeriod.MONTHLY -> amountEur
        }

    companion object {
        const val WEEKS_PER_MONTH = 52.0 / 12.0

        /** Null for anything that is not a real amount, so "no plan" has one shape only. */
        fun of(amountEur: Double?, period: ContributionPeriod?): Contribution? {
            if (amountEur == null || period == null) return null
            if (!amountEur.isFinite() || amountEur <= 0.0) return null
            return Contribution(amountEur, period)
        }
    }
}
