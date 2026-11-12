package com.maakthi.prescriptionpharmacist.core.analysis

import com.maakthi.prescriptionpharmacist.domain.model.AnalysisResult
import com.maakthi.prescriptionpharmacist.domain.model.AnalysisWarning
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.MedicationAlternative
import com.maakthi.prescriptionpharmacist.domain.model.MedicationCandidate
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.model.medicationTypeOf

/**
 * The multi-stage prescription analysis pipeline (stages 4–11).
 *
 * Pure Kotlin: runs on the JVM without Android, so every stage is unit
 * tested. Input: OCR output + optional AI hints + the drug catalog.
 * Output: a fully grounded [AnalysisResult] that always requires pharmacist
 * confirmation.
 *
 * Hard rules:
 * - A medication name is never invented: it is either the canonical catalog
 *   record or the literal (uncertain) reading of the text.
 * - Strength/dose/route/duration are only kept when grounded in the OCR text.
 * - Low-confidence or unmatched entries are flagged "النتيجة غير مؤكدة".
 * - Ambiguous entries carry ranked catalog alternatives.
 */
class AnalysisPipeline(
    private val matcher: DrugCatalogMatcher = DrugCatalogMatcher,
    private val scorer: ConfidenceScorer = ConfidenceScorer
) {

    fun analyze(
        ocr: OcrResult,
        hints: List<AiMedicationHint> = emptyList(),
        catalog: List<Drug> = emptyList()
    ): AnalysisResult {
        val lines = PrescriptionSegmenter.segment(ocr)
        val ruleParsed = lines.map { line -> line to (MedicationLineParser.parse(line) ?: emptyParsed(line)) }
        val merged = mergeHints(ruleParsed, hints, ocr.fullText)

        val medications = merged.map { candidate ->
            buildCandidate(candidate, ocr.fullText, catalog)
        }

        val warnings = buildList {
            add(AnalysisWarning.HANDWRITING_MAY_BE_INACCURATE)
            add(AnalysisWarning.NO_AUTOMATIC_DISPENSING)
            if (medications.any { it.isUncertain }) add(AnalysisWarning.UNCERTAIN_RESULTS_PRESENT)
        }

        return AnalysisResult(
            medications = medications,
            language = ocr.language,
            ocrText = ocr.fullText,
            warnings = warnings,
            requiresPharmacistConfirmation = true
        )
    }

    // ---------- stages 5–8: merge rule output with AI hints ----------

    internal data class Candidate(
        val name: String,
        val scientificName: String?,
        val typeRaw: String?,
        val strength: String?,
        val form: String?,
        val dose: String?,
        val route: String?,
        val duration: String?,
        val rawText: String,
        val ocrConfidence: Float,
        val hintUnclear: Boolean,
        val hintAlternatives: List<String>
    )

    private fun emptyParsed(line: PrescriptionLine) = ParsedMedication(
        name = null, rawText = line.text
    )

    private fun mergeHints(
        ruleParsed: List<Pair<PrescriptionLine, ParsedMedication>>,
        hints: List<AiMedicationHint>,
        ocrText: String
    ): List<Candidate> {
        val candidates = mutableListOf<Candidate>()

        // Rule-based candidates first (always grounded in their own line).
        for ((line, parsed) in ruleParsed) {
            val name = parsed.name ?: line.text
            val hint = findHintFor(name, parsed.rawText, hints)
            // AI hint fields survive only when grounded; rule fields are
            // grounded by construction (literal spans of the line).
            val hintFields = hint?.let {
                GroundingFilter.keepGrounded(
                    strength = it.strength,
                    form = it.form,
                    dose = it.dose,
                    route = it.route,
                    duration = it.duration,
                    ocrText = ocrText
                )
            }
            candidates += Candidate(
                name = name,
                scientificName = hint?.scientificName?.takeIf { GroundingFilter.isGrounded(it, ocrText) },
                typeRaw = hint?.type,
                strength = hintFields?.strength ?: parsed.strength,
                form = hintFields?.form ?: parsed.form,
                dose = hintFields?.dose ?: parsed.dose,
                route = hintFields?.route ?: parsed.route,
                duration = hintFields?.duration ?: parsed.duration,
                rawText = parsed.rawText,
                ocrConfidence = line.ocrConfidence,
                hintUnclear = hint?.unclear == true || MedicationLineParser.isUnclearName(name),
                hintAlternatives = hint?.alternatives ?: emptyList()
            )
        }

        // AI hints that no rule line matched (still grounded against text).
        for (hint in hints) {
            val already = candidates.any { similarNames(it.name, hint.name) || similarNames(it.rawText, hint.rawText ?: "") }
            if (already) continue
            if (!GroundingFilter.isGrounded(hint.name, ocrText)) continue
            val fields = GroundingFilter.keepGrounded(
                strength = hint.strength,
                form = hint.form,
                dose = hint.dose,
                route = hint.route,
                duration = hint.duration,
                ocrText = ocrText
            )
            candidates += Candidate(
                name = hint.name,
                scientificName = hint.scientificName?.takeIf { GroundingFilter.isGrounded(it, ocrText) },
                typeRaw = hint.type,
                strength = fields.strength,
                form = fields.form,
                dose = fields.dose,
                route = fields.route,
                duration = fields.duration,
                rawText = hint.rawText ?: hint.name,
                ocrConfidence = 1f,
                hintUnclear = hint.unclear,
                hintAlternatives = hint.alternatives
            )
        }

        // Deduplicate by normalized name.
        return candidates.distinctBy { TextNormalizer.normalize(it.name) }
    }

    private fun findHintFor(name: String, rawText: String, hints: List<AiMedicationHint>): AiMedicationHint? =
        hints.firstOrNull { similarNames(it.name, name) || similarNames(it.rawText ?: "", rawText) }

    private fun similarNames(a: String, b: String): Boolean {
        if (a.isBlank() || b.isBlank()) return false
        return TextNormalizer.similarity(a, b) >= 0.7f
    }

    // ---------- stages 9–11: match, score, alternatives ----------

    private fun buildCandidate(
        candidate: Candidate,
        ocrText: String,
        catalog: List<Drug>
    ): MedicationCandidate {
        val match = matcher.match(candidate.name, catalog)
        val nameFound = candidate.name.isNotBlank()
        val fieldsFound = listOf(
            candidate.strength, candidate.form, candidate.dose,
            candidate.route, candidate.duration
        ).count { !it.isNullOrBlank() }

        val confidence = scorer.score(
            ocrConfidence = candidate.ocrConfidence,
            nameFound = nameFound,
            fieldsFound = fieldsFound,
            matchScore = match.matched?.let { match.score },
            hintUnclear = candidate.hintUnclear
        )

        val alternatives = buildList {
            match.alternatives.forEach {
                add(MedicationAlternative(name = it.name, scientificName = it.scientificName, score = it.score))
            }
            candidate.hintAlternatives.forEach { alt ->
                if (this.none { TextNormalizer.similarity(it.name, alt) >= 0.8f }) {
                    val altMatch = matcher.match(alt, catalog)
                    add(
                        MedicationAlternative(
                            name = altMatch.matched?.name ?: alt,
                            scientificName = altMatch.matched?.scientificName,
                            score = if (altMatch.matched != null) altMatch.score else 0.5f
                        )
                    )
                }
            }
        }.sortedByDescending { it.score }.take(4)

        val isUncertain = scorer.isUncertain(
            confidence = confidence,
            matched = match.matched != null,
            hintUnclear = candidate.hintUnclear
        )

        // Never invent: keep the canonical catalog name when matched, else
        // the literal reading. scientificName/type come only from the catalog
        // or a grounded hint.
        return MedicationCandidate(
            name = match.matched?.name ?: candidate.name,
            scientificName = match.matched?.scientificName ?: candidate.scientificName,
            type = match.matched?.type ?: medicationTypeOf(candidate.typeRaw),
            strength = candidate.strength,
            form = candidate.form,
            dose = candidate.dose,
            route = candidate.route,
            duration = candidate.duration,
            confidence = confidence,
            rawText = candidate.rawText,
            isUncertain = isUncertain,
            alternatives = alternatives,
            confirmedByPharmacist = false
        )
    }
}
