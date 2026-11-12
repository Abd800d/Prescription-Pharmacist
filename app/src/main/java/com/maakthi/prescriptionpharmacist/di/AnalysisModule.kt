package com.maakthi.prescriptionpharmacist.di

import com.maakthi.prescriptionpharmacist.core.analysis.AiExtractionEngine
import com.maakthi.prescriptionpharmacist.core.analysis.OcrEngine
import com.maakthi.prescriptionpharmacist.data.analysis.RemoteAiExtractionEngine
import com.maakthi.prescriptionpharmacist.data.analysis.RemoteOcrEngine
import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

/**
 * The OCR layer and the AI extraction layer are independent interfaces —
 * swap the bindings here to change providers without touching the pipeline.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class AnalysisModule {

    @Binds
    @Singleton
    abstract fun bindOcrEngine(impl: RemoteOcrEngine): OcrEngine

    @Binds
    @Singleton
    abstract fun bindAiExtractionEngine(impl: RemoteAiExtractionEngine): AiExtractionEngine
}
