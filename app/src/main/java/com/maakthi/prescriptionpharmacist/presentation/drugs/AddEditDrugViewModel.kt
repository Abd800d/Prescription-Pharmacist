package com.maakthi.prescriptionpharmacist.presentation.drugs

import android.content.Context
import android.net.Uri
import androidx.lifecycle.SavedStateHandle
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.core.util.ImageUtils
import com.maakthi.prescriptionpharmacist.domain.model.AppResult
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.repository.AuthRepository
import com.maakthi.prescriptionpharmacist.domain.usecase.AddDrugUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.DrugEntryValidator
import com.maakthi.prescriptionpharmacist.domain.usecase.GetDrugUseCase
import com.maakthi.prescriptionpharmacist.domain.usecase.UpdateDrugUseCase
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import java.util.UUID
import javax.inject.Inject

/** All drug-database columns, editable from the form. */
data class DrugFormState(
    val id: String? = null,
    val tradeNameArabic: String = "",
    val tradeNameEnglish: String = "",
    val genericNameArabic: String = "",
    val genericNameEnglish: String = "",
    val activeIngredient: String = "",
    val strength: String = "",
    val dosageForm: String = "",
    val manufacturer: String = "",
    val barcode: String = "",
    val indications: String = "",
    val contraindications: String = "",
    val warnings: String = "",
    val sideEffects: String = "",
    val interactions: String = "",
    val pregnancyInformation: String = "",
    val lactationInformation: String = "",
    val productImageUrl: String = "",
    val sourceName: String = "",
    val sourceUrl: String = "",
    val sourceDate: String = "",
    val type: MedicationType = MedicationType.UNKNOWN,
    val isVerified: Boolean = false
)

sealed interface DrugFormEvent {
    data object Saved : DrugFormEvent
    data class Error(val message: String) : DrugFormEvent
}

@HiltViewModel
class AddEditDrugViewModel @Inject constructor(
    savedStateHandle: SavedStateHandle,
    @ApplicationContext private val context: Context,
    private val getDrug: GetDrugUseCase,
    private val addDrug: AddDrugUseCase,
    private val updateDrug: UpdateDrugUseCase,
    private val authRepository: AuthRepository
) : ViewModel() {

    private val drugId: String? = savedStateHandle.get<String>("drugId")

    private val _form = MutableStateFlow(DrugFormState())
    val form: StateFlow<DrugFormState> = _form.asStateFlow()

    private val _loading = MutableStateFlow(drugId != null)
    val loading: StateFlow<Boolean> = _loading.asStateFlow()

    private val _events = Channel<DrugFormEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    init {
        if (drugId != null) {
            viewModelScope.launch {
                val drug = getDrug(drugId)
                if (drug != null) {
                    _form.value = DrugFormState(
                        id = drug.id,
                        tradeNameArabic = drug.tradeNameArabic.orEmpty(),
                        tradeNameEnglish = drug.tradeNameEnglish.orEmpty(),
                        genericNameArabic = drug.genericNameArabic.orEmpty(),
                        genericNameEnglish = drug.genericNameEnglish.orEmpty(),
                        activeIngredient = drug.activeIngredient.orEmpty(),
                        strength = drug.strength.orEmpty(),
                        dosageForm = drug.dosageForm.orEmpty(),
                        manufacturer = drug.manufacturer.orEmpty(),
                        barcode = drug.barcode.orEmpty(),
                        indications = drug.indications.orEmpty(),
                        contraindications = drug.contraindications.orEmpty(),
                        warnings = drug.warnings.orEmpty(),
                        sideEffects = drug.sideEffects.orEmpty(),
                        interactions = drug.interactions.orEmpty(),
                        pregnancyInformation = drug.pregnancyInformation.orEmpty(),
                        lactationInformation = drug.lactationInformation.orEmpty(),
                        productImageUrl = drug.productImageUrl.orEmpty(),
                        sourceName = drug.sourceName.orEmpty(),
                        sourceUrl = drug.sourceUrl.orEmpty(),
                        sourceDate = drug.sourceDate.orEmpty(),
                        type = drug.type,
                        isVerified = drug.isVerified
                    )
                }
                _loading.value = false
            }
        }
    }

    fun update(transform: (DrugFormState) -> DrugFormState) {
        _form.value = transform(_form.value)
    }

    /** Copies the picked product image into app-private storage. */
    fun onImagePicked(uri: Uri) {
        viewModelScope.launch {
            val path = ImageUtils.copyToAppStorage(context, uri)
            if (path != null) {
                _form.value = _form.value.copy(productImageUrl = path)
            } else {
                _events.send(DrugFormEvent.Error("image_unreadable"))
            }
        }
    }

    fun removeImage() {
        val old = _form.value.productImageUrl
        _form.value = _form.value.copy(productImageUrl = "")
        if (old.isNotBlank()) {
            viewModelScope.launch { ImageUtils.deleteImage(old) }
        }
    }

    fun save() {
        val state = _form.value
        val draft = state.toDrug()
        val validation = DrugEntryValidator.validate(draft)
        if (validation != null) {
            viewModelScope.launch {
                _events.send(DrugFormEvent.Error(validation.second))
            }
            return
        }
        viewModelScope.launch {
            val performedBy = runCatching { authRepository.currentUser.first() }.getOrNull()
                ?.let { it.displayName ?: it.uid } ?: "pharmacist"
            val result = if (state.id == null) {
                addDrug(draft, performedBy)
            } else {
                updateDrug(draft, performedBy)
            }
            when (result) {
                is AppResult.Success -> _events.send(DrugFormEvent.Saved)
                is AppResult.Error -> {
                    val message = when (result.code) {
                        com.maakthi.prescriptionpharmacist.domain.model.ErrorCode.DUPLICATE ->
                            "duplicate:${result.message.orEmpty()}"
                        com.maakthi.prescriptionpharmacist.domain.model.ErrorCode.VALIDATION ->
                            result.message ?: "validation"
                        else -> result.message ?: "unknown"
                    }
                    _events.send(DrugFormEvent.Error(message))
                }
            }
        }
    }

    private fun DrugFormState.toDrug(): Drug {
        val tradeEn = tradeNameEnglish.trim().takeIf { it.isNotEmpty() }
        val tradeAr = tradeNameArabic.trim().takeIf { it.isNotEmpty() }
        val genericEn = genericNameEnglish.trim().takeIf { it.isNotEmpty() }
        val genericAr = genericNameArabic.trim().takeIf { it.isNotEmpty() }
        return Drug(
            id = id ?: UUID.randomUUID().toString(),
            name = tradeEn ?: tradeAr ?: genericEn ?: genericAr ?: "",
            scientificName = genericEn ?: genericAr,
            type = when {
                tradeEn != null || tradeAr != null -> MedicationType.BRAND
                genericEn != null || genericAr != null -> MedicationType.GENERIC
                else -> type
            },
            strength = strength.trim().takeIf { it.isNotEmpty() },
            form = dosageForm.trim().takeIf { it.isNotEmpty() },
            manufacturer = manufacturer.trim().takeIf { it.isNotEmpty() },
            indications = indications.trim().takeIf { it.isNotEmpty() },
            warnings = warnings.trim().takeIf { it.isNotEmpty() },
            isLocal = true,
            tradeNameArabic = tradeAr,
            tradeNameEnglish = tradeEn,
            genericNameArabic = genericAr,
            genericNameEnglish = genericEn,
            activeIngredient = activeIngredient.trim().takeIf { it.isNotEmpty() },
            dosageForm = dosageForm.trim().takeIf { it.isNotEmpty() },
            barcode = barcode.trim().takeIf { it.isNotEmpty() },
            contraindications = contraindications.trim().takeIf { it.isNotEmpty() },
            sideEffects = sideEffects.trim().takeIf { it.isNotEmpty() },
            interactions = interactions.trim().takeIf { it.isNotEmpty() },
            pregnancyInformation = pregnancyInformation.trim().takeIf { it.isNotEmpty() },
            lactationInformation = lactationInformation.trim().takeIf { it.isNotEmpty() },
            productImageUrl = productImageUrl.trim().takeIf { it.isNotEmpty() },
            sourceName = sourceName.trim().takeIf { it.isNotEmpty() },
            sourceUrl = sourceUrl.trim().takeIf { it.isNotEmpty() },
            sourceDate = sourceDate.trim().takeIf { it.isNotEmpty() },
            isVerified = isVerified
        )
    }
}
