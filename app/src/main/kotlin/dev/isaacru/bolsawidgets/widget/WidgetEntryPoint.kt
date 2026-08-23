package dev.isaacru.bolsawidgets.widget

import android.content.Context
import dagger.hilt.EntryPoint
import dagger.hilt.InstallIn
import dagger.hilt.android.EntryPointAccessors
import dagger.hilt.components.SingletonComponent
import dev.isaacru.bolsawidgets.domain.repository.SettingsRepository
import dev.isaacru.bolsawidgets.domain.usecase.ObservePortfolioUseCase
import dev.isaacru.bolsawidgets.domain.usecase.ObserveWatchlistUseCase
import dev.isaacru.bolsawidgets.domain.usecase.RefreshMarketDataUseCase
import java.time.ZoneId

/**
 * Hilt bridge for the widgets.
 *
 * A [androidx.glance.appwidget.GlanceAppWidget] is instantiated by the framework, not by
 * Hilt, so it cannot take constructor injection. Reaching into the singleton graph through
 * an entry point is the supported way round that.
 */
@EntryPoint
@InstallIn(SingletonComponent::class)
interface WidgetEntryPoint {

    fun observeWatchlist(): ObserveWatchlistUseCase

    fun observePortfolio(): ObservePortfolioUseCase

    fun settingsRepository(): SettingsRepository

    fun refreshMarketData(): RefreshMarketDataUseCase

    fun widgetUpdater(): WidgetUpdater

    fun zoneId(): ZoneId

    companion object {
        fun from(context: Context): WidgetEntryPoint = EntryPointAccessors.fromApplication(
            context.applicationContext,
            WidgetEntryPoint::class.java,
        )
    }
}
