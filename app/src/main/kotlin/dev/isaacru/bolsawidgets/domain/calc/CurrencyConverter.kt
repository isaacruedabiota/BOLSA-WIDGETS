package dev.isaacru.bolsawidgets.domain.calc

import dev.isaacru.bolsawidgets.domain.model.FxRate

/**
 * Converts amounts between currencies using a fixed snapshot of rates.
 *
 * The snapshot comes from the Room FX cache, so conversion is a pure function and stays
 * testable and offline-safe. Rates are keyed by concatenated ISO codes ("USDEUR" means
 * "1 USD = rate EUR"). A missing pair is resolved from its inverse, or triangulated
 * through EUR, before giving up and returning null.
 */
class CurrencyConverter(private val rates: Map<String, Double>) {

    constructor(rates: List<FxRate>) : this(rates.associate { it.pair.uppercase() to it.rate })

    /**
     * Rate to turn one major unit of [from] into major units of [to],
     * or null when the snapshot cannot express it.
     */
    fun rate(from: String, to: String): Double? {
        val source = normalize(from)
        val target = normalize(to)
        return majorRate(source.code, target.code)
    }

    /**
     * [amount] expressed in [to], or null when the rate is unknown.
     *
     * Minor-unit quotes are handled here: Yahoo prices London listings in "GBp"
     * (pence), so an unadjusted rate would be off by a factor of 100.
     */
    fun convert(amount: Double, from: String, to: String): Double? {
        val source = normalize(from)
        val target = normalize(to)
        val rate = majorRate(source.code, target.code) ?: return null
        return amount * source.minorUnitFactor * rate / target.minorUnitFactor
    }

    /** Convenience for the app's base currency. */
    fun toEur(amount: Double, from: String): Double? = convert(amount, from, EUR)

    private fun majorRate(from: String, to: String): Double? {
        if (from == to) return 1.0
        direct(from, to)?.let { return it }
        if (from == EUR || to == EUR) return null
        val fromEur = direct(from, EUR) ?: return null
        val eurTo = direct(EUR, to) ?: return null
        return fromEur * eurTo
    }

    private fun direct(from: String, to: String): Double? {
        rates[from + to]?.takeIf { it > 0.0 }?.let { return it }
        rates[to + from]?.takeIf { it > 0.0 }?.let { return 1.0 / it }
        return null
    }

    private fun normalize(currency: String): Normalized = normalizeCurrency(currency)

    /** A currency code reduced to its ISO major unit plus the factor to get there. */
    data class Normalized(val code: String, val minorUnitFactor: Double) {
        val isMinorUnit: Boolean get() = minorUnitFactor != 1.0
    }

    companion object {
        const val EUR = "EUR"

        val Empty = CurrencyConverter(emptyMap<String, Double>())

        /**
         * Yahoo quotes some listings in minor units: pence for London ("GBp", note the
         * lowercase p, which is a different code from "GBP"), cents elsewhere. Anything
         * that formats or converts an amount has to go through here, otherwise the value
         * is wrong by a factor of 100.
         */
        fun normalizeCurrency(currency: String): Normalized = when (val raw = currency.trim()) {
            "GBp", "GBX" -> Normalized("GBP", 0.01)
            "ZAc" -> Normalized("ZAR", 0.01)
            "ILA" -> Normalized("ILS", 0.01)
            else -> Normalized(raw.uppercase(), 1.0)
        }

        /** True when [currency] is a minor-unit code that must not be shown as its ISO parent. */
        fun isMinorUnit(currency: String): Boolean = normalizeCurrency(currency).isMinorUnit
    }
}
