package dev.isaacru.bolsawidgets.domain.repository

import dev.isaacru.bolsawidgets.domain.model.Position
import kotlinx.coroutines.flow.Flow

/** Manual buy lots. The only writer is the user, through the Cartera screen. */
interface PortfolioRepository {

    fun observePositions(): Flow<List<Position>>

    /** Distinct symbols currently held, upper-cased. */
    fun observeHeldSymbols(): Flow<List<String>>

    suspend fun getPositions(): List<Position>

    suspend fun getPosition(id: Long): Position?

    /** Inserts when [position] has id 0, updates otherwise. Returns the row id. */
    suspend fun upsert(position: Position): Long

    suspend fun delete(id: Long)

    /**
     * Swaps the whole portfolio for [positions] in one transaction. Used only when
     * restoring a backup, where a half-applied import would be worse than none.
     */
    suspend fun replaceAll(positions: List<Position>)
}
