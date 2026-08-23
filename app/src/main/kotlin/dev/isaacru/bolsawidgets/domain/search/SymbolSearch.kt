package dev.isaacru.bolsawidgets.domain.search

/**
 * Best-effort symbol suggestions.
 *
 * This is a convenience on top of the ticker resolver, never a requirement: the Watchlist
 * and the position editor must stay usable when it fails, so callers swallow its errors.
 */
interface SymbolSearch {

    suspend fun search(query: String, limit: Int = DEFAULT_LIMIT): List<SymbolSuggestion>

    companion object {
        const val DEFAULT_LIMIT = 8
    }
}

/** One row of the suggestion list. Unverified until it is resolved through a quote call. */
data class SymbolSuggestion(
    val symbol: String,
    val name: String,
    val exchange: String,
    val type: String,
)
