package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugFilters
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import javax.inject.Inject

/**
 * Searches the drug database by trade name (AR/EN), generic name (AR/EN),
 * active ingredient or barcode, then applies the result filters.
 */
class SearchDrugsUseCase @Inject constructor(
    private val drugs: DrugEntryRepository
) {
    suspend operator fun invoke(query: String, filters: DrugFilters = DrugFilters()): List<Drug> =
        drugs.searchDrugs(query.trim(), filters)
}
