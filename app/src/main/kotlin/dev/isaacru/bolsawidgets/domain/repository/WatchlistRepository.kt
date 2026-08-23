package dev.isaacru.bolsawidgets.domain.repository

import dev.isaacru.bolsawidgets.domain.model.WatchlistItem
import kotlinx.coroutines.flow.Flow

/** Followed symbols, ordered by the user. */
interface WatchlistRepository {

    fun observeItems(): Flow<List<WatchlistItem>>

    suspend fun getItems(): List<WatchlistItem>

    /** Appends at the end of the list; a symbol already present is left untouched. */
    suspend fun add(symbol: String, name: String)

    suspend fun remove(symbol: String)

    /** Persists [symbolsInOrder] as the new ordering. */
    suspend fun reorder(symbolsInOrder: List<String>)
}
