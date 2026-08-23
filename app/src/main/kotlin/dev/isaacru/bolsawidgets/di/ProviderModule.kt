package dev.isaacru.bolsawidgets.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.isaacru.bolsawidgets.data.remote.twelvedata.TwelveDataQuoteProvider
import dev.isaacru.bolsawidgets.data.remote.yahoo.YahooQuoteProvider
import dev.isaacru.bolsawidgets.domain.provider.ProviderId
import dev.isaacru.bolsawidgets.domain.provider.QuoteProvider
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object ProviderModule {

    /**
     * Every implementation, keyed by the id persisted in Ajustes. The settings screen
     * picks one out of this map; Yahoo is the fallback when the stored id is unusable.
     */
    @Provides
    @Singleton
    fun provideQuoteProviders(
        yahoo: YahooQuoteProvider,
        twelveData: TwelveDataQuoteProvider,
    ): Map<ProviderId, @JvmSuppressWildcards QuoteProvider> = mapOf(
        ProviderId.YAHOO to yahoo,
        ProviderId.TWELVE_DATA to twelveData,
    )

    /** Default source until the settings screen exists. */
    @Provides
    @Singleton
    fun provideDefaultQuoteProvider(yahoo: YahooQuoteProvider): QuoteProvider = yahoo
}
