package dev.isaacru.bolsawidgets.domain.csv

import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.ContributionPeriod
import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.model.WatchlistItem
import java.time.LocalDate
import java.util.Locale

/** Everything the app stores that the user typed in by hand. */
data class CsvBackup(
    val positions: List<Position>,
    val watchlist: List<WatchlistItem>,
) {
    val isEmpty: Boolean get() = positions.isEmpty() && watchlist.isEmpty()
}

/**
 * What a parse produced, plus the rows it could not read.
 *
 * A single malformed line never aborts the import: the rest is still worth having, and
 * the count of skipped rows is surfaced so nothing disappears silently.
 */
data class CsvParseResult(
    val backup: CsvBackup,
    val skippedRows: List<Int>,
)

/**
 * Backup format for the portfolio and the watchlist.
 *
 * One file holds both, told apart by the first column, so a backup is a single thing to
 * save and a single thing to restore. Numbers are always written with a dot decimal
 * separator and dates in ISO form: this file is meant to survive being opened in a
 * spreadsheet in any locale and handed back unchanged.
 *
 * Pure text in, pure text out — no Android, no storage — so the round trip is unit tested.
 */
object PortfolioCsv {

    const val TYPE_POSITION = "posicion"
    const val TYPE_WATCHLIST = "seguimiento"

    val COLUMNS = listOf(
        "tipo",
        "simbolo",
        "nombre",
        "mercado",
        "cantidad",
        "precio_medio",
        "divisa",
        "fecha_compra",
        "notas",
        "orden",
        "aportacion",
        "periodo_aportacion",
    )

    fun export(backup: CsvBackup): String = buildString {
        appendLine(COLUMNS.joinToString(","))
        backup.positions.forEach { position ->
            appendLine(
                row(
                    TYPE_POSITION,
                    position.symbol,
                    position.name,
                    position.exchange,
                    decimal(position.quantity),
                    decimal(position.averageBuyPrice),
                    position.currency,
                    position.purchaseDate.toString(),
                    position.notes,
                    "",
                    "",
                    "",
                ),
            )
        }
        backup.watchlist.forEach { item ->
            appendLine(
                row(
                    TYPE_WATCHLIST,
                    item.symbol,
                    item.name,
                    "",
                    "",
                    "",
                    "",
                    "",
                    "",
                    item.sortOrder.toString(),
                    item.contribution?.let { decimal(it.amountEur) }.orEmpty(),
                    item.contribution?.period?.name?.lowercase().orEmpty(),
                ),
            )
        }
    }

    fun parse(text: String): CsvParseResult {
        val positions = mutableListOf<Position>()
        val watchlist = mutableListOf<WatchlistItem>()
        val skipped = mutableListOf<Int>()

        text.lineSequence().forEachIndexed { index, rawLine ->
            val line = rawLine.trim()
            if (line.isEmpty()) return@forEachIndexed
            val fields = splitRow(line)
            val type = fields.getOrNull(0)?.trim()?.lowercase().orEmpty()

            // The header line, whichever spelling it has, is not a row.
            if (type == COLUMNS[0]) return@forEachIndexed

            when (type) {
                TYPE_POSITION -> parsePosition(fields)?.let(positions::add)
                    ?: skipped.add(index + 1)

                TYPE_WATCHLIST -> parseWatchlistItem(fields, watchlist.size)?.let(watchlist::add)
                    ?: skipped.add(index + 1)

                else -> skipped.add(index + 1)
            }
        }

        return CsvParseResult(CsvBackup(positions, watchlist), skipped)
    }

    private fun parsePosition(fields: List<String>): Position? {
        val symbol = fields.getOrNull(1)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        val quantity = fields.getOrNull(4).toDecimalOrNull() ?: return null
        val price = fields.getOrNull(5).toDecimalOrNull() ?: return null
        if (quantity <= 0.0 || price < 0.0) return null
        val date = fields.getOrNull(7)?.trim()?.takeIf { it.isNotEmpty() }
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return null

        return Position(
            id = 0L,
            symbol = symbol,
            name = fields.getOrNull(2)?.trim().orEmpty().ifEmpty { symbol },
            exchange = fields.getOrNull(3)?.trim().orEmpty(),
            quantity = quantity,
            averageBuyPrice = price,
            currency = fields.getOrNull(6)?.trim()?.takeIf { it.isNotEmpty() } ?: return null,
            purchaseDate = date,
            notes = fields.getOrNull(8)?.trim().orEmpty(),
        )
    }

    private fun parseWatchlistItem(fields: List<String>, fallbackOrder: Int): WatchlistItem? {
        val symbol = fields.getOrNull(1)?.trim()?.uppercase()?.takeIf { it.isNotEmpty() } ?: return null
        return WatchlistItem(
            symbol = symbol,
            name = fields.getOrNull(2)?.trim().orEmpty().ifEmpty { symbol },
            sortOrder = fields.getOrNull(9)?.trim()?.toIntOrNull() ?: fallbackOrder,
            // Absent in files written before contributions existed, and a restore has to
            // work with those: missing columns simply mean no plan.
            contribution = Contribution.of(
                amountEur = fields.getOrNull(10).toDecimalOrNull(),
                period = parsePeriod(fields.getOrNull(11)),
            ),
        )
    }

    private fun parsePeriod(raw: String?): ContributionPeriod? {
        val text = raw?.trim().orEmpty()
        if (text.isEmpty()) return null
        return ContributionPeriod.entries.firstOrNull { it.name.equals(text, ignoreCase = true) }
    }

    private fun row(vararg values: String): String = values.joinToString(",") { escape(it) }

    /**
     * Quotes a field when it contains anything that would break the row apart. Newlines
     * are folded into spaces rather than quoted, so one record is always one line and the
     * parser can stay line based.
     */
    private fun escape(value: String): String {
        val flattened = value.replace("\r\n", " ").replace('\n', ' ').replace('\r', ' ')
        val needsQuotes = flattened.contains(',') || flattened.contains('"')
        if (!needsQuotes) return flattened
        return "\"" + flattened.replace("\"", "\"\"") + "\""
    }

    /** Splits one CSV line, honouring quoted fields and doubled quotes inside them. */
    private fun splitRow(line: String): List<String> {
        val fields = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var index = 0

        while (index < line.length) {
            val char = line[index]
            when {
                inQuotes && char == '"' && line.getOrNull(index + 1) == '"' -> {
                    current.append('"')
                    index++
                }

                char == '"' -> inQuotes = !inQuotes
                char == ',' && !inQuotes -> {
                    fields.add(current.toString())
                    current.clear()
                }

                else -> current.append(char)
            }
            index++
        }
        fields.add(current.toString())
        return fields
    }

    /** Always a dot, never the Spanish comma: the comma is the field separator. */
    private fun decimal(value: Double): String = String.format(Locale.ROOT, "%s", value)

    /** Accepts either separator on the way in, because spreadsheets like to help. */
    private fun String?.toDecimalOrNull(): Double? =
        this?.trim()?.replace(',', '.')?.toDoubleOrNull()
}
