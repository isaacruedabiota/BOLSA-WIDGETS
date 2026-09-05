package dev.isaacru.bolsawidgets.domain.share

import dev.isaacru.bolsawidgets.domain.model.WatchlistItem

/** One line of a shared list: a ticker and, when it was written down, a name. */
data class SharedSymbol(
    val symbol: String,
    val name: String,
)

/**
 * A watchlist as something you can paste into a chat.
 *
 * Not the CSV: that is a backup meant for a spreadsheet, with your contributions and your
 * favourites in it. This is a list of values to look at, which is the part that makes
 * sense to hand to somebody else. **What you put in every month never leaves the phone
 * this way** — it is nobody's business but yours, and a friend has no use for it.
 *
 * The format is deliberately dull so it survives being pasted through WhatsApp, an email
 * client and back: one value per line, ticker first, name after a separator. Anything the
 * parser does not recognise is skipped rather than rejected, because a shared message
 * arrives with greetings, signatures and quoted replies around it.
 */
object WatchlistShare {

    const val HEADER = "Bolsa Widgets · lista de seguimiento"

    private const val SEPARATOR = " | "

    /** Tickers are short, made of letters, digits and the separators Yahoo uses. */
    private const val TICKER_PUNCTUATION = ".-^=&"
    private const val MAX_TICKER_LENGTH = 16

    fun encode(items: List<WatchlistItem>): String = buildString {
        appendLine(HEADER)
        items.forEach { item ->
            val symbol = item.symbol.trim().uppercase()
            if (symbol.isEmpty()) return@forEach
            append(symbol)
            // The name is a courtesy for whoever reads the message; the ticker is what
            // actually gets resolved on the other side.
            item.name.trim().takeIf { it.isNotEmpty() && !it.equals(symbol, ignoreCase = true) }
                ?.let { append(SEPARATOR).append(it.replace("\n", " ")) }
            appendLine()
        }
    }

    /**
     * Reads back whatever survived the trip.
     *
     * Duplicates collapse, order is kept, and a line that carries no plausible ticker is
     * dropped: pasting a whole conversation should yield the values in it, not an error.
     */
    fun decode(text: String): List<SharedSymbol> {
        val seen = LinkedHashMap<String, SharedSymbol>()
        text.lineSequence().forEach { rawLine ->
            val line = rawLine.trim().removePrefix(">").trim()
            if (line.isEmpty() || line.equals(HEADER, ignoreCase = true)) return@forEach

            // Both the separator this writes and the comma a person would type by hand.
            val parts = line.split('|', ',', '\t', limit = 2)
            // Judged as written, before being upper-cased: how someone typed a word is
            // half of what says whether it is a ticker at all.
            val candidate = parts.firstOrNull()?.trim().orEmpty()
            if (!candidate.looksLikeTicker()) return@forEach
            val symbol = candidate.uppercase()

            val name = parts.getOrNull(1)?.trim().orEmpty()
            seen.putIfAbsent(symbol, SharedSymbol(symbol = symbol, name = name))
        }
        return seen.values.toList()
    }

    /**
     * Whether a word is plausibly a ticker, judged as it was written.
     *
     * Shape alone is not enough: "Hola, mira mi lista" splits on its comma and leaves
     * "Hola", which is letters and no spaces like any ticker. What tells them apart is how
     * people write them — a ticker is shouted (AAPL, SAN.MC) or carries its market suffix
     * (ibe.mc) — so one of those two has to hold. A letter is required as well, which is
     * what keeps a year or a price out of somebody's list.
     */
    private fun String.looksLikeTicker(): Boolean {
        if (isEmpty() || length > MAX_TICKER_LENGTH) return false
        if (none { it.isLetter() }) return false
        if (any { !it.isLetterOrDigit() && it !in TICKER_PUNCTUATION }) return false
        val isShouted = this == uppercase()
        val hasSuffix = any { it in TICKER_PUNCTUATION }
        return isShouted || hasSuffix
    }
}
