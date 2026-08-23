package dev.isaacru.bolsawidgets.domain.usecase

import dev.isaacru.bolsawidgets.domain.calc.PortfolioCalculator
import dev.isaacru.bolsawidgets.domain.model.PortfolioSummary
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.combine
import javax.inject.Inject

/**
 * The valued portfolio, recomputed whenever the lots, the cached quotes or the FX
 * snapshot change. Everything it reads is local, so it emits offline too.
 */
class ObservePortfolioUseCase @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val quoteRepository: QuoteRepository,
) {
    operator fun invoke(): Flow<PortfolioSummary> = combine(
        portfolioRepository.observePositions(),
        quoteRepository.observeQuotes(),
        quoteRepository.observeConverter(),
    ) { positions, quotes, converter ->
        PortfolioCalculator.summarize(positions, quotes, converter)
    }
}
