package dev.isaacru.bolsawidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.isaacru.bolsawidgets.data.prefs.SettingsRepositoryImpl
import dev.isaacru.bolsawidgets.data.remote.yahoo.YahooSymbolSearch
import dev.isaacru.bolsawidgets.data.repository.PortfolioRepositoryImpl
import dev.isaacru.bolsawidgets.data.repository.QuoteRepositoryImpl
import dev.isaacru.bolsawidgets.data.repository.WatchlistRepositoryImpl
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
import dev.isaacru.bolsawidgets.domain.repository.QuoteRepository
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import dev.isaacru.bolsawidgets.domain.search.SymbolSearch
import dev.isaacru.bolsawidgets.domain.repository.WatchlistRepository
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPortfolioRepository(impl: PortfolioRepositoryImpl): PortfolioRepository

    @Binds
    @Singleton
    abstract fun bindWatchlistRepository(impl: WatchlistRepositoryImpl): WatchlistRepository

    @Binds
    @Singleton
    abstract fun bindQuoteRepository(impl: QuoteRepositoryImpl): QuoteRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsRepositoryImpl): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindSymbolSearch(impl: YahooSymbolSearch): SymbolSearch
}
