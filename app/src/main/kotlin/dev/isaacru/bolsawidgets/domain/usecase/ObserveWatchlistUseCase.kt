package dev.isaacru.bolsawidgets.domain.usecase

import dev.isaacru.bolsawidgets.domain.model.WatchlistRow
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/** The watchlist in user order, each row carrying its last known price. */
class ObserveWatchlistUseCase @Inject constructor(
    private val watchlistRepository: WatchlistRepository,
    private val quoteRepository: QuoteRepository,
) {
    operator fun invoke(): Flow<List<WatchlistRow>> = combine(
        watchlistRepository.observeItems(),
        quoteRepository.observeQuotes(),
    ) { items, quotes ->
        items.map { item -> WatchlistRow(item, quotes[item.symbol.uppercase()]) }
    }
}
