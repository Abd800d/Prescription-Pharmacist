package com.maakthi.prescriptionpharmacist.di

import com.maakthi.prescriptionpharmacist.data.firebase.FirebaseAuthRepository
import com.maakthi.prescriptionpharmacist.data.firebase.FirebaseBackupRepository
import com.maakthi.prescriptionpharmacist.data.remote.RemoteAnalysisRepository
import com.maakthi.prescriptionpharmacist.data.repository.PrescriptionRepositoryImpl
import com.maakthi.prescriptionpharmacist.data.local.SettingsDataStore
import com.maakthi.prescriptionpharmacist.domain.repository.AnalysisRepository
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.repository.BackupRepository
import com.maakthi.prescriptionpharmacist.domain.repository.PrescriptionRepository
import com.maakthi.prescriptionpharmacist.domain.repository.SettingsRepository
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
}
