package com.maakthi.prescriptionpharmacist.core.image

/**
 * In-memory [ImagePipeline] for ViewModel unit tests: records calls and
 * returns configurable results.
 */
class FakeImagePipeline : ImagePipeline {

    var captureTarget: String = "/tmp/capture.jpg"
    var importResult: String? = "/tmp/imported.img"
    var dimensionsResult: Pair<Int, Int> = 3200 to 2400
    var qualityResult: ImageQualityReport = ImageQualityReport(
        width = 3200,
        height = 2400,
        blurScore = 120.0,
        isLikelyBlurry = false,
        meanBrightness = 140.0,
        isTooDark = false,
        isTooBright = false,
        contrastScore = 45.0,
        isLowContrast = false,
        skewDegrees = 0
    )
    var qualityThrows: Boolean = false
    var renderResult: String? = "/tmp/rendered.png"
    var uploadBytes: ByteArray? = ByteArray(3 * 1024)

    var cleanupCount = 0
    val importCalls = mutableListOf<String?>()
    val renderCalls = mutableListOf<Pair<String, RenderOptions>>()
    val uploadCalls = mutableListOf<String>()

    override fun createCaptureTarget(): String = captureTarget

    override suspend fun importImage(source: String?): String? {
        importCalls += source
        return importResult
    }

    override suspend fun imageDimensions(path: String): Pair<Int, Int> = dimensionsResult

    override suspend fun quality(path: String): ImageQualityReport {
        if (qualityThrows) throw IllegalStateException("decode failed")
        return qualityResult
    }

    override suspend fun render(path: String, options: RenderOptions): String? {
        renderCalls += path to options
        return renderResult
    }

    override suspend fun compressForUpload(path: String): ByteArray? {
        uploadCalls += path
        return uploadBytes
    }

    override suspend fun cleanupOldTemp() {
        cleanupCount++
    }
}
