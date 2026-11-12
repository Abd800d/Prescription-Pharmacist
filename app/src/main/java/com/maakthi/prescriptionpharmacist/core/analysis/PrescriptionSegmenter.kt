package com.maakthi.prescriptionpharmacist.core.analysis

/**
 * Stage 4: splits raw OCR text into medication candidate lines.
 *
 * - One OCR line = one candidate; lines containing separators ("،", ",", "+")
 *   are split when every part still holds letters.
 * - Noise lines (signatures, dates, phones, headers) are dropped.
 * - Confidence comes from the OCR provider when available.
 */
object PrescriptionSegmenter {

    // NOTE: \b does not work with Arabic letters (they are not \w), so the
    // boundaries use (?![\p{L}\d]) instead.
    private val noisePatterns = listOf(
        Regex("^(date|التاريخ|تاريخ)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE),
        Regex("^(signature|التوقيع|توقيع)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE),
        Regex("^(tel|phone|fax|هاتف|فاكس)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE),
        Regex("^(dr\\.?|doctor|د\\.?|طبيب)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE),
        Regex("^(clinic|hospital|عيادة|مستشفى|صيدلية|pharmacy)(?![\\p{L}\\d])", RegexOption.IGNORE_CASE),
        Regex("^\\d{2,4}[-/.]\\d{1,2}[-/.]\\d{1,4}$"), // pure dates
        Regex("^[+()\\d\\s-]{7,}$") // pure phone-like
    )

    fun segment(ocr: OcrResult): List<PrescriptionLine> {
        val rawLines = if (ocr.lines.isNotEmpty()) {
            ocr.lines.map { it.text to it.confidence }
        } else {
            ocr.fullText.split('\n').map { it to 1f }
        }
        val out = mutableListOf<PrescriptionLine>()
        var index = 0
        for ((raw, confidence) in rawLines) {
            for (part in splitMultiMedication(raw)) {
                val cleaned = clean(part)
                if (isNoise(cleaned)) continue
                out += PrescriptionLine(
                    text = cleaned,
                    ocrConfidence = confidence.coerceIn(0f, 1f),
                    index = index++
                )
            }
        }
        return out
    }

    fun segment(text: String): List<PrescriptionLine> =
        segment(OcrResult(fullText = text))

    /** Splits "A + B" / "A، B" only when every part contains letters. */
    internal fun splitMultiMedication(line: String): List<String> {
        val parts = line.split(Regex("\\s*[،,;+]\\s*")).map { it.trim() }.filter { it.isNotEmpty() }
        if (parts.size <= 1) return listOf(line)
        val allHaveLetters = parts.all { part -> part.any { it.isLetter() } }
        return if (allHaveLetters) parts else listOf(line)
    }

    internal fun clean(line: String): String =
        TextNormalizer.normalizeDigits(line)
            .replace(Regex("^[\\s\\-•*.]+"), "")
            .trim()

    internal fun isNoise(line: String): Boolean {
        if (line.isEmpty()) return true
        if (line.none { it.isLetter() }) return true
        return noisePatterns.any { it.containsMatchIn(line) }
    }
}
