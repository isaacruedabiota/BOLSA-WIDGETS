package dev.isaacru.bolsawidgets.domain.csv

import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.ContributionPeriod
import dev.isaacru.bolsawidgets.domain.model.Position
import dev.isaacru.bolsawidgets.domain.model.WatchlistItem
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import java.time.LocalDate

/**
 * This file is the only backup of data typed in by hand, so the round trip has to be
 * exact and the parser has to be hard to break.
 */
class PortfolioCsvTest {

    private val backup = CsvBackup(
        positions = listOf(
            Position(
                id = 7,
                symbol = "SAN.MC",
                name = "BANCO SANTANDER S.A.",
                exchange = "MCE",
                quantity = 100.0,
                averageBuyPrice = 10.5,
                currency = "EUR",
                purchaseDate = LocalDate.of(2026, 1, 15),
                notes = "primera compra",
            ),
            Position(
                id = 8,
                symbol = "AAPL",
                name = "Apple Inc.",
                exchange = "NasdaqGS",
                quantity = 10.0,
                averageBuyPrice = 150.0,
                currency = "USD",
                purchaseDate = LocalDate.of(2026, 3, 2),
                notes = "",
            ),
        ),
        watchlist = listOf(
            WatchlistItem("ITX.MC", "Inditex", 0),
            WatchlistItem(
                symbol = "IWDA.AS",
                name = "iShares Core MSCI World",
                sortOrder = 1,
                contribution = Contribution(200.0, ContributionPeriod.MONTHLY),
            ),
        ),
    )

    @Test
    fun `a backup survives a round trip`() {
        val restored = PortfolioCsv.parse(PortfolioCsv.export(backup))

        assertTrue(restored.skippedRows.isEmpty())
        assertEquals(backup.watchlist, restored.backup.watchlist)
        assertEquals(
            backup.positions.map { it.copy(id = 0L) },
            restored.backup.positions,
        )
    }

    @Test
    fun `ids are not exported because they are database bookkeeping`() {
        val restored = PortfolioCsv.parse(PortfolioCsv.export(backup))

        assertTrue(restored.backup.positions.all { it.id == 0L })
    }

    @Test
    fun `the file starts with a readable header`() {
        val csv = PortfolioCsv.export(backup)

        assertEquals(PortfolioCsv.COLUMNS.joinToString(","), csv.lineSequence().first())
    }

    @Test
    fun `commas inside a field do not break the row`() {
        val withCommas = backup.copy(
            positions = listOf(backup.positions[0].copy(notes = "compra en dos tramos, la buena")),
        )

        val restored = PortfolioCsv.parse(PortfolioCsv.export(withCommas))

        assertEquals("compra en dos tramos, la buena", restored.backup.positions.single().notes)
    }

    @Test
    fun `quotes inside a field survive`() {
        val withQuotes = backup.copy(
            positions = listOf(backup.positions[0].copy(notes = "la llamaban \"la joya\"")),
        )

        val restored = PortfolioCsv.parse(PortfolioCsv.export(withQuotes))

        assertEquals("la llamaban \"la joya\"", restored.backup.positions.single().notes)
    }

    @Test
    fun `a newline in a note is flattened so one record stays one line`() {
        val multiline = CsvBackup(
            positions = listOf(backup.positions[0].copy(notes = "primera\nsegunda")),
            watchlist = emptyList(),
        )

        val csv = PortfolioCsv.export(multiline)

        // Header plus exactly one record.
        assertEquals(2, csv.trim().lines().size)
        assertEquals("primera segunda", PortfolioCsv.parse(csv).backup.positions.single().notes)
    }

    @Test
    fun `decimals are always written with a dot`() {
        val csv = PortfolioCsv.export(backup)

        assertTrue(csv.contains("10.5"))
    }

    @Test
    fun `a comma decimal from a spreadsheet is still read`() {
        val csv = PortfolioCsv.COLUMNS.joinToString(",") +
            "\nposicion,SAN.MC,Santander,MCE,\"100\",\"10,5\",EUR,2026-01-15,,"

        val restored = PortfolioCsv.parse(csv)

        assertEquals(10.5, restored.backup.positions.single().averageBuyPrice, 1e-9)
    }

    @Test
    fun `a broken row is skipped and reported instead of losing the file`() {
        val csv = PortfolioCsv.COLUMNS.joinToString(",") +
            "\nposicion,SAN.MC,Santander,MCE,100,10.5,EUR,2026-01-15,," +
            "\nposicion,,sin simbolo,,1,1,EUR,2026-01-15,," +
            "\nposicion,ITX.MC,Inditex,MCE,cantidad-mala,45,EUR,2026-01-15,," +
            "\nbasura,una,linea,que,no,significa,nada,,," +
            "\nseguimiento,AAPL,Apple,,,,,,,0"

        val restored = PortfolioCsv.parse(csv)

        assertEquals(listOf("SAN.MC"), restored.backup.positions.map { it.symbol })
        assertEquals(listOf("AAPL"), restored.backup.watchlist.map { it.symbol })
        assertEquals(listOf(3, 4, 5), restored.skippedRows)
    }

    @Test
    fun `a position without a date is not guessed at`() {
        val csv = "posicion,SAN.MC,Santander,MCE,100,10.5,EUR,,,"

        val restored = PortfolioCsv.parse(csv)

        assertTrue(restored.backup.positions.isEmpty())
        assertEquals(listOf(1), restored.skippedRows)
    }

    @Test
    fun `watchlist order falls back to the file order when the column is empty`() {
        val csv = "seguimiento,SAN.MC,Santander,,,,,,,\nseguimiento,ITX.MC,Inditex,,,,,,,"

        val restored = PortfolioCsv.parse(csv)

        assertEquals(listOf(0, 1), restored.backup.watchlist.map { it.sortOrder })
    }

    @Test
    fun `symbols are normalised to upper case on the way in`() {
        val csv = "seguimiento,san.mc,Santander,,,,,,,0"

        assertEquals("SAN.MC", PortfolioCsv.parse(csv).backup.watchlist.single().symbol)
    }

    @Test
    fun `an empty file parses to an empty backup`() {
        val restored = PortfolioCsv.parse("")

        assertTrue(restored.backup.isEmpty)
        assertTrue(restored.skippedRows.isEmpty())
    }

    @Test
    fun `blank lines are ignored rather than reported`() {
        val csv = PortfolioCsv.COLUMNS.joinToString(",") +
            "\n\nseguimiento,AAPL,Apple,,,,,,,0\n\n"

        val restored = PortfolioCsv.parse(csv)

        assertEquals(1, restored.backup.watchlist.size)
        assertTrue(restored.skippedRows.isEmpty())
    }

    @Test
    fun `a contribution survives the round trip, cadence included`() {
        val weekly = CsvBackup(
            positions = emptyList(),
            watchlist = listOf(
                WatchlistItem(
                    symbol = "SAN.MC",
                    name = "Santander",
                    sortOrder = 0,
                    contribution = Contribution(50.0, ContributionPeriod.WEEKLY),
                ),
            ),
        )

        val restored = PortfolioCsv.parse(PortfolioCsv.export(weekly))

        assertEquals(weekly.watchlist, restored.backup.watchlist)
    }

    @Test
    fun `a file written before contributions existed still restores`() {
        // Ten columns, no contribution ones. Losing a whole backup because it predates a
        // feature would be the worst possible way to fail.
        val old = """
            tipo,simbolo,nombre,mercado,cantidad,precio_medio,divisa,fecha_compra,notas,orden
            seguimiento,ITX.MC,Inditex,,,,,,,0
        """.trimIndent()

        val restored = PortfolioCsv.parse(old)

        assertTrue(restored.skippedRows.isEmpty())
        assertEquals(listOf(WatchlistItem("ITX.MC", "Inditex", 0)), restored.backup.watchlist)
        assertNull(restored.backup.watchlist.single().contribution)
    }

    @Test
    fun `an unreadable cadence is dropped rather than guessed`() {
        val broken = """
            tipo,simbolo,nombre,mercado,cantidad,precio_medio,divisa,fecha_compra,notas,orden,aportacion,periodo_aportacion
            seguimiento,ITX.MC,Inditex,,,,,,,0,200,trimestral
        """.trimIndent()

        val item = PortfolioCsv.parse(broken).backup.watchlist.single()

        assertNull(item.contribution)
    }

    @Test
    fun `a favourite survives the round trip`() {
        val starred = CsvBackup(
            positions = emptyList(),
            watchlist = listOf(
                WatchlistItem("ITX.MC", "Inditex", 0, isFavorite = true),
                WatchlistItem("TEF.MC", "Telefonica", 1),
            ),
        )

        val restored = PortfolioCsv.parse(PortfolioCsv.export(starred)).backup.watchlist

        assertEquals(starred.watchlist, restored)
        assertTrue(restored.first().isFavorite)
        assertFalse(restored.last().isFavorite)
    }

    @Test
    fun `a spreadsheet's idea of yes is still a yes`() {
        val file = """
            tipo,simbolo,nombre,mercado,cantidad,precio_medio,divisa,fecha_compra,notas,orden,aportacion,periodo_aportacion,favorito
            seguimiento,ITX.MC,Inditex,,,,,,,0,,,TRUE
            seguimiento,TEF.MC,Telefonica,,,,,,,1,,,
        """.trimIndent()

        val restored = PortfolioCsv.parse(file).backup.watchlist

        assertTrue(restored.first().isFavorite)
        assertFalse(restored.last().isFavorite)
    }
}
