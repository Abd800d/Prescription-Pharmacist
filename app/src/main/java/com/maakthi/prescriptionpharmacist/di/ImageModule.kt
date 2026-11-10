package com.maakthi.prescriptionpharmacist.di

import com.maakthi.prescriptionpharmacist.core.image.ImagePipeline
import com.maakthi.prescriptionpharmacist.data.image.ImagePipelineImpl
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
abstract class ImageModule {

    @Binds
    @Singleton
    abstract fun bindImagePipeline(impl: ImagePipelineImpl): ImagePipeline
}
