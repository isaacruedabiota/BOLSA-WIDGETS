package dev.isaacru.bolsawidgets.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.Dispatchers
import java.time.Clock
import java.time.ZoneId
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    /** The app is single-user and lives in Spain; every local time is Madrid time. */
    @Provides
    @Singleton
    fun provideZoneId(): ZoneId = ZoneId.of(MADRID_ZONE)

    /**
     * Injected instead of calling Instant.now() directly so market-hours logic and
     * cache freshness can be tested with a fixed clock.
     */
    @Provides
    @Singleton
    fun provideClock(zoneId: ZoneId): Clock = Clock.system(zoneId)

    @Provides
    @IoDispatcher
    fun provideIoDispatcher(): CoroutineDispatcher = Dispatchers.IO

    @Provides
    @DefaultDispatcher
    fun provideDefaultDispatcher(): CoroutineDispatcher = Dispatchers.Default

    const val MADRID_ZONE = "Europe/Madrid"
}
