package com.maakthi.prescriptionpharmacist.di

import com.maakthi.prescriptionpharmacist.data.firebase.FirebaseAuthRepository
import com.maakthi.prescriptionpharmacist.data.firebase.FirebaseBackupRepository
import com.maakthi.prescriptionpharmacist.data.remote.RemoteAnalysisRepository
import com.maakthi.prescriptionpharmacist.data.repository.AssistantRepositoryImpl
import com.maakthi.prescriptionpharmacist.data.repository.DrugRepositoryImpl
import com.maakthi.prescriptionpharmacist.data.repository.ProductRepositoryImpl
import com.maakthi.prescriptionpharmacist.data.repository.PrescriptionRepositoryImpl
import com.maakthi.prescriptionpharmacist.data.repository.TransferRepositoryImpl
import com.maakthi.prescriptionpharmacist.data.local.SettingsDataStore
import com.maakthi.prescriptionpharmacist.domain.repository.AnalysisRepository
import com.maakthi.prescriptionpharmacist.domain.repository.AssistantRepository
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.BackupRepository
import com.maakthi.prescriptionpharmacist.domain.repository.DrugRepository
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import com.maakthi.prescriptionpharmacist.domain.repository.ProductRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
import com.maakthi.prescriptionpharmacist.domain.repository.TransferRepository
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class RepositoryModule {

    @Binds
    @Singleton
    abstract fun bindPrescriptionRepository(impl: PrescriptionRepositoryImpl): PrescriptionRepository

    @Binds
    @Singleton
    abstract fun bindAnalysisRepository(impl: RemoteAnalysisRepository): AnalysisRepository

    @Binds
    @Singleton
    abstract fun bindSettingsRepository(impl: SettingsDataStore): SettingsRepository

    @Binds
    @Singleton
    abstract fun bindAuthRepository(impl: FirebaseAuthRepository): AuthRepository

    @Binds
    @Singleton
    abstract fun bindBackupRepository(impl: FirebaseBackupRepository): BackupRepository

    @Binds
    @Singleton
    abstract fun bindDrugRepository(impl: DrugRepositoryImpl): DrugRepository

    @Binds
    @Singleton
    abstract fun bindDrugEntryRepository(impl: DrugRepositoryImpl): com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository

    @Binds
    @Singleton
    abstract fun bindProductRepository(impl: ProductRepositoryImpl): ProductRepository

    @Binds
    @Singleton
    abstract fun bindAssistantRepository(impl: AssistantRepositoryImpl): AssistantRepository

    @Binds
    @Singleton
    abstract fun bindTransferRepository(impl: TransferRepositoryImpl): TransferRepository

    @Binds
    @Singleton
    abstract fun bindMediaRepository(impl: com.maakthi.prescriptionpharmacist.data.repository.MediaRepositoryImpl): com.maakthi.prescriptionpharmacist.domain.repository.MediaRepository

    @Binds
    @Singleton
    abstract fun bindSyncRepository(impl: com.maakthi.prescriptionpharmacist.data.sync.SyncRepositoryImpl): com.maakthi.prescriptionpharmacist.domain.repository.SyncRepository
}
