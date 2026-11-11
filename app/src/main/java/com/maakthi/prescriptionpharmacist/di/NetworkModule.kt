package com.maakthi.prescriptionpharmacist.di

import com.jakewharton.retrofit2.converter.kotlinx.serialization.asConverterFactory
import com.maakthi.prescriptionpharmacist.data.remote.AnalysisApi
import com.maakthi.prescriptionpharmacist.data.remote.AssistantApi
import com.maakthi.prescriptionpharmacist.data.remote.DrugApi
import com.maakthi.prescriptionpharmacist.data.remote.OpenFdaApi
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import kotlinx.serialization.json.Json
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.OkHttpClient
import okhttp3.logging.HttpLoggingInterceptor
import retrofit2.Retrofit
import java.util.concurrent.TimeUnit
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object NetworkModule {

    @Provides
    @Singleton
    fun provideJson(): Json = Json {
        ignoreUnknownKeys = true
        isLenient = true
        coerceInputValues = true
    }

    @Provides
    @Singleton
    fun provideOkHttpClient(): OkHttpClient {
        val logging = HttpLoggingInterceptor().apply {
            level = HttpLoggingInterceptor.Level.BASIC
        }
        return OkHttpClient.Builder()
            .connectTimeout(30, TimeUnit.SECONDS)
            .readTimeout(120, TimeUnit.SECONDS)
            .writeTimeout(60, TimeUnit.SECONDS)
            .addInterceptor(logging)
            .build()
    }

    /**
     * The analysis backend URL is provided per-request via `@Url`, so a single
     * Retrofit instance serves any backend configured in Settings.
     */
    @Provides
    @Singleton
    fun provideRetrofit(client: OkHttpClient, json: Json): Retrofit =
        Retrofit.Builder()
            .baseUrl("https://localhost/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()

    @Provides
    @Singleton
    fun provideAnalysisApi(retrofit: Retrofit): AnalysisApi =
        retrofit.create(AnalysisApi::class.java)

    @Provides
    @Singleton
    fun provideAssistantApi(retrofit: Retrofit): AssistantApi =
        retrofit.create(AssistantApi::class.java)

    /** CRUD API of the shared drug database backend (same per-call `@Url` style). */
    @Provides
    @Singleton
    fun provideDrugApi(retrofit: Retrofit): DrugApi =
        retrofit.create(DrugApi::class.java)

    /** Backend drug-information endpoint (official sources, keys on the server). */
    @Provides
    @Singleton
    fun provideDrugKnowledgeApi(retrofit: Retrofit): com.maakthi.prescriptionpharmacist.data.remote.DrugKnowledgeApi =
        retrofit.create(com.maakthi.prescriptionpharmacist.data.remote.DrugKnowledgeApi::class.java)

    /** Public openFDA drug label API (no API key). */
    @Provides
    @Singleton
    fun provideOpenFdaApi(client: OkHttpClient, json: Json): OpenFdaApi =
        Retrofit.Builder()
            .baseUrl("https://api.fda.gov/")
            .client(client)
            .addConverterFactory(json.asConverterFactory("application/json".toMediaType()))
            .build()
            .create(OpenFdaApi::class.java)
}
