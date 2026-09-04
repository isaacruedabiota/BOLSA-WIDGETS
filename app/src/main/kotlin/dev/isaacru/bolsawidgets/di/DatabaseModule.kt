package dev.isaacru.bolsawidgets.di

import android.content.Context
import androidx.room.Room
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import dev.isaacru.bolsawidgets.data.local.BolsaDatabase
import dev.isaacru.bolsawidgets.data.local.dao.CandleCacheDao
import dev.isaacru.bolsawidgets.data.local.dao.FxRateDao
import dev.isaacru.bolsawidgets.data.local.dao.PositionDao
import dev.isaacru.bolsawidgets.data.local.dao.QuoteCacheDao
import dev.isaacru.bolsawidgets.data.local.dao.WatchlistDao
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): BolsaDatabase =
        Room.databaseBuilder(context, BolsaDatabase::class.java, BolsaDatabase.NAME)
            .addMigrations(BolsaDatabase.MIGRATION_1_2, BolsaDatabase.MIGRATION_2_3)
            .build()

    @Provides
    fun providePositionDao(database: BolsaDatabase): PositionDao = database.positionDao()

    @Provides
    fun provideWatchlistDao(database: BolsaDatabase): WatchlistDao = database.watchlistDao()

    @Provides
    fun provideQuoteCacheDao(database: BolsaDatabase): QuoteCacheDao = database.quoteCacheDao()

    @Provides
    fun provideFxRateDao(database: BolsaDatabase): FxRateDao = database.fxRateDao()

    @Provides
    fun provideCandleCacheDao(database: BolsaDatabase): CandleCacheDao = database.candleCacheDao()
}
