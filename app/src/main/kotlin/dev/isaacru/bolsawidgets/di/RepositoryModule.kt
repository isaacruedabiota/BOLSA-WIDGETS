package dev.isaacru.bolsawidgets.di

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.isaacru.bolsawidgets.data.repository.PortfolioRepositoryImpl
import dev.isaacru.bolsawidgets.data.repository.WatchlistRepositoryImpl
import dev.isaacru.bolsawidgets.domain.repository.PortfolioRepository
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
}
