package com.maakthi.prescriptionpharmacist.domain.usecase

import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.repository.DrugEntryRepository
import javax.inject.Inject

/** Loads one drug by id (null when missing). */
class GetDrugUseCase @Inject constructor(
    private val drugs: DrugEntryRepository
) {
    suspend operator fun invoke(id: String): Drug? = drugs.getDrug(id)
}
