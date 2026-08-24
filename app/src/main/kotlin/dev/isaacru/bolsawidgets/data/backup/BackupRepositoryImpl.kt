package dev.isaacru.bolsawidgets.data.backup

import dev.isaacru.bolsawidgets.domain.csv.CsvBackup
import dev.isaacru.bolsawidgets.domain.csv.PortfolioCsv
import dev.isaacru.bolsawidgets.domain.repository.BackupRepository
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class BackupRepositoryImpl @Inject constructor(
    private val portfolioRepository: PortfolioRepository,
    private val watchlistRepository: WatchlistRepository,
) : BackupRepository {

    override suspend fun exportCsv(): String = PortfolioCsv.export(
        CsvBackup(
            positions = portfolioRepository.getPositions(),
            watchlist = watchlistRepository.getItems(),
        ),
    )

    override suspend fun restore(backup: CsvBackup) {
        portfolioRepository.replaceAll(backup.positions)
        watchlistRepository.replaceAll(backup.watchlist)
    }
}
