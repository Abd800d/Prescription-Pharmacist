package com.maakthi.prescriptionpharmacist.di

import com.maakthi.prescriptionpharmacist.core.knowledge.DrugKnowledgeProvider
import com.maakthi.prescriptionpharmacist.data.knowledge.DrugKnowledgeRepositoryImpl
import com.maakthi.prescriptionpharmacist.data.knowledge.OpenFdaKnowledgeProvider
import com.maakthi.prescriptionpharmacist.data.knowledge.ServerBackedKnowledgeProvider
import com.maakthi.prescriptionpharmacist.domain.repository.DrugKnowledgeRepository
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.IntoSet
import javax.inject.Singleton

/**
 * Plugin registry of official drug-information sources.
 *
 * Adding a new source (ministry of health, local drug authority…) = write a
 * [DrugKnowledgeProvider] and register it here with @IntoSet — nothing else
 * in the app changes.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class KnowledgeModule {

    @Binds
    @Singleton
    abstract fun bindDrugKnowledgeRepository(
        impl: DrugKnowledgeRepositoryImpl
    ): DrugKnowledgeRepository

    companion object {

        /** OPTIONAL keyless connector for drug leaflets (openFDA). */
        @Provides
        @IntoSet
        @Singleton
        fun provideOpenFdaProvider(provider: OpenFdaKnowledgeProvider): DrugKnowledgeProvider =
            provider

        /** Official authority APIs proxied through the backend (keys live on the server). */
        @Provides
        @IntoSet
        @Singleton
        fun provideAuthorityProvider(provider: ServerBackedKnowledgeProvider): DrugKnowledgeProvider =
            provider

        @Provides
        @Singleton
        fun provideDrugKnowledgeService(
            providers: Set<@JvmSuppressWildcards DrugKnowledgeProvider>
        ): com.maakthi.prescriptionpharmacist.core.knowledge.DrugKnowledgeService =
            com.maakthi.prescriptionpharmacist.core.knowledge.DrugKnowledgeService(
                providers = providers.sortedBy { it.id }
            )
    }
}
