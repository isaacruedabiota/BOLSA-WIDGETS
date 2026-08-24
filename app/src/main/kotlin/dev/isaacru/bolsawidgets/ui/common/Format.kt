package dev.isaacru.bolsawidgets.ui.common

import dev.isaacru.bolsawidgets.domain.calc.CurrencyConverter
import java.text.NumberFormat
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.Currency
import java.util.Locale
import kotlin.math.abs

/**
 * Number and date formatting for the Spanish locale, shared by the app and the widgets.
 *
 * Kept as plain functions on purpose: the widgets render outside a Compose context with a
 * Configuration, so nothing here may depend on one.
 */
object Format {

    val LOCALE: Locale = Locale.forLanguageTag("es-ES")

    private val DATE_TIME = DateTimeFormatter.ofPattern("d MMM, HH:mm", LOCALE)
    private val DATE = DateTimeFormatter.ofPattern("dd/MM/yyyy", LOCALE)

    /** An amount with its currency symbol, e.g. "1.234,56 €". */
    fun money(amount: Double, currency: String = "EUR"): String {
        val format = NumberFormat.getCurrencyInstance(LOCALE).apply {
            maximumFractionDigits = 2
            minimumFractionDigits = 2
        }
        val iso = isoCurrency(currency)
        return if (iso == null) {
            plain(amount, 2) + " " + currency.trim()
        } else {
            format.currency = iso
            format.format(amount)
        }
    }

    /** Same as [money] but always carrying an explicit sign, for P&L figures. */
    fun signedMoney(amount: Double, currency: String = "EUR"): String {
        val body = money(abs(amount), currency)
        return if (amount < 0) "-" + body else "+" + body
    }

    /** A signed percentage with two decimals, e.g. "+2,65 %". */
    fun percent(value: Double, withSign: Boolean = true): String {
        val body = plain(abs(value), 2) + " %"
        return when {
            !withSign -> plain(value, 2) + " %"
            value < 0 -> "-" + body
            else -> "+" + body
        }
    }

    /**
     * A quoted price. Sub-euro instruments get four decimals so an FX pair or a penny
     * stock does not collapse to two identical-looking values.
     */
    fun price(value: Double, currency: String): String {
        val decimals = if (abs(value) < 1.0) 4 else 2
        val iso = isoCurrency(currency)
        return if (iso == null) {
            plain(value, decimals) + " " + currency.trim()
        } else {
            NumberFormat.getCurrencyInstance(LOCALE).apply {
                this.currency = iso
                maximumFractionDigits = decimals
                minimumFractionDigits = decimals
            }.format(value)
        }
    }

    /**
     * Share counts, which may be fractional: a savings plan buys 0,5241 shares, not 1.
     * Four decimals is what a broker shows; trailing zeros are dropped so a whole number
     * of shares does not read as "10,0000".
     */
    fun quantity(value: Double, maxDecimals: Int = 4): String =
        NumberFormat.getNumberInstance(LOCALE).apply {
            maximumFractionDigits = maxDecimals
            minimumFractionDigits = 0
        }.format(value)

    /**
     * The ISO currency to format with, or null when the code has no ISO equivalent that
     * can be shown safely. A minor-unit code such as "GBp" upper-cases into the perfectly
     * valid "GBP", so it has to be rejected before the lookup: printing 500 pence as
     * 500 pounds is a hundredfold error.
     */
    private fun isoCurrency(currency: String): Currency? {
        if (CurrencyConverter.isMinorUnit(currency)) return null
        return runCatching { Currency.getInstance(currency.trim().uppercase()) }.getOrNull()
    }

    /**
     * A number destined for a text field the user will edit and the app will parse back.
     *
     * Grouping separators are dropped on purpose: in Spanish the thousands separator is a
     * dot, so "2.450,00" stops being parseable the moment the comma is normalised to a
     * decimal point. Display formatting and editable formatting are not the same job.
     */
    fun editable(value: Double, decimals: Int): String =
        NumberFormat.getNumberInstance(LOCALE).apply {
            isGroupingUsed = false
            maximumFractionDigits = decimals
            minimumFractionDigits = 0
        }.format(value)

    fun plain(value: Double, decimals: Int): String =
        NumberFormat.getNumberInstance(LOCALE).apply {
            maximumFractionDigits = decimals
            minimumFractionDigits = decimals
        }.format(value)

    fun dateTime(instant: Instant, zone: ZoneId): String =
        DATE_TIME.format(instant.atZone(zone))

    fun date(value: LocalDate): String = DATE.format(value)
}
