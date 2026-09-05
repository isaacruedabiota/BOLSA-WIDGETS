package dev.isaacru.bolsawidgets.di

import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dev.isaacru.bolsawidgets.BuildConfig
import dev.isaacru.bolsawidgets.data.remote.yahoo.YahooChartApi
import dev.isaacru.bolsawidgets.data.remote.yahoo.YahooScreenerApi
import dev.isaacru.bolsawidgets.data.remote.yahoo.YahooSearchApi
import dev.isaacru.bolsawidgets.data.remote.yahoo.YahooSparkApi
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import retrofit2.converter.kotlinx.serialization.asConverterFactory
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        // The Yahoo payload carries far more than this app models, and its shape drifts.
        ignoreUnknownKeys = true
        coerceInputValues = true
        explicitNulls = false
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val builder = OkHttpClient.Builder()
            // Short timeouts: a widget refresh that stalls is worse than one that
            // silently keeps the cached value.
            .connectTimeout(CONNECT_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .readTimeout(READ_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .writeTimeout(WRITE_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .callTimeout(CALL_TIMEOUT_SECONDS, TimeUnit.SECONDS)
            .retryOnConnectionFailure(true)
            .addInterceptor { chain ->
                // Yahoo answers 429 to clients that do not look like a browser.
                val request = chain.request().newBuilder()
                    .header("User-Agent", YahooChartApi.USER_AGENT)
                    .header("Accept", "application/json")
                    .build()
                chain.proceed(request)
            }

        if (BuildConfig.DEBUG) {
            builder.addInterceptor(
                HttpLoggingInterceptor().apply { level = HttpLoggingInterceptor.Level.BASIC },
            )
        }
        return builder.build()
    }

    @Provides
    @Singleton
    fun provideYahooRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl(YahooChartApi.BASE_URL)
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideYahooChartApi(retrofit: Retrofit): YahooChartApi =
        retrofit.create(YahooChartApi::class.java)

    @Provides
    @Singleton
    fun provideYahooSearchApi(retrofit: Retrofit): YahooSearchApi =
        retrofit.create(YahooSearchApi::class.java)

    @Provides
    @Singleton
    fun provideYahooSparkApi(retrofit: Retrofit): YahooSparkApi =
        retrofit.create(YahooSparkApi::class.java)

    @Provides
    @Singleton
    fun provideYahooScreenerApi(retrofit: Retrofit): YahooScreenerApi =
        retrofit.create(YahooScreenerApi::class.java)

    private const val CONNECT_TIMEOUT_SECONDS = 5L
    private const val READ_TIMEOUT_SECONDS = 8L
    private const val WRITE_TIMEOUT_SECONDS = 5L
    private const val CALL_TIMEOUT_SECONDS = 12L
}
