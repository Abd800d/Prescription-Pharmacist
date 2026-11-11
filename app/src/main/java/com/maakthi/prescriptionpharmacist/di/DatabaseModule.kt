package com.maakthi.prescriptionpharmacist.di

import android.content.Context
import androidx.room.Room
import com.maakthi.prescriptionpharmacist.data.local.AppDatabase
import com.maakthi.prescriptionpharmacist.data.local.dao.DrugDao
import com.maakthi.prescriptionpharmacist.data.local.dao.PrescriptionDao
import com.maakthi.prescriptionpharmacist.data.local.dao.ProductImageDao
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object DatabaseModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): AppDatabase =
        Room.databaseBuilder(context, AppDatabase::class.java, AppDatabase.NAME)
            .fallbackToDestructiveMigration()
            .build()

    @Provides
    fun providePrescriptionDao(database: AppDatabase): PrescriptionDao =
        database.prescriptionDao()

    @Provides
    fun provideDrugDao(database: AppDatabase): DrugDao =
        database.drugDao()

    @Provides
    fun provideDrugAuditDao(database: AppDatabase): com.maakthi.prescriptionpharmacist.data.local.dao.DrugAuditDao =
        database.drugAuditDao()

    @Provides
    fun provideProductImageDao(database: AppDatabase): ProductImageDao =
        database.productImageDao()
}
