package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.DrugFilterOptions
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import javax.inject.Inject

/** Distinct dosage forms / manufacturers shown in the search filter row. */
class GetDrugFilterOptionsUseCase @Inject constructor(
    private val drugs: DrugEntryRepository
) {
    suspend operator fun invoke(): DrugFilterOptions = drugs.filterOptions()
}
