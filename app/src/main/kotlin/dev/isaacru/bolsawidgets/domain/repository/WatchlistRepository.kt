package dev.isaacru.bolsawidgets.domain.repository

import dev.isaacru.bolsawidgets.domain.model.Contribution
import dev.isaacru.bolsawidgets.domain.model.WatchlistItem
import kotlinx.coroutines.flow.Flow

/** Followed symbols, ordered by the user. */
interface WatchlistRepository {

    fun observeItems(): Flow<List<WatchlistItem>>

    suspend fun getItems(): List<WatchlistItem>

    /** Appends at the end of the list; a symbol already present is left untouched. */
    suspend fun add(symbol: String, name: String)

    suspend fun remove(symbol: String)

    /**
     * Sets what the user puts into [symbol] every week or month, or clears it with null.
     * A symbol that is not on the list is left alone.
     */
    suspend fun setContribution(symbol: String, contribution: Contribution?)

    /** Persists [symbolsInOrder] as the new ordering. */
    suspend fun reorder(symbolsInOrder: List<String>)

    /** Swaps the whole list for [items] in one transaction, for a backup restore. */
    suspend fun replaceAll(items: List<WatchlistItem>)
}
