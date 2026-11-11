package com.maakthi.prescriptionpharmacist.presentation.drugs

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.presentation.components.LoadingView

/**
 * Full add/edit form for one drug database record: names, composition,
 * usage, safety text, product image, source metadata and the verified flag.
 */
@Composable
fun AddEditDrugScreen(
    onSaved: () -> Unit,
    onBack: () -> Unit,
    viewModel: AddEditDrugViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    val snackbarHostState = androidx.compose.material3.SnackbarHostState()
    val errorDuplicate = stringResource(R.string.drug_duplicate_error)
    val errorName = stringResource(R.string.drug_name_required_error)
    val errorBarcode = stringResource(R.string.drug_barcode_invalid_error)
    val errorImage = stringResource(R.string.error_image_unreadable)

    val imagePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.GetContent()
    ) { uri ->
        uri?.let(viewModel::onImagePicked)
    }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                DrugFormEvent.Saved -> onSaved()
                is DrugFormEvent.Error -> {
                    val message = when {
                        event.message.startsWith("duplicate") -> errorDuplicate
                        event.message == "name_required" -> errorName
                        event.message == "barcode_invalid" -> errorBarcode
                        event.message == "image_unreadable" -> errorImage
                        else -> event.message
                    }
                    snackbarHostState.showSnackbar(message)
                }
            }
        }
    }

    if (loading) {
        LoadingView(
            message = stringResource(R.string.analysis_running),
            modifier = Modifier.fillMaxSize()
        )
        return
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(
                if (form.id == null) R.string.drug_add_title else R.string.drug_edit_title
            ),
            style = MaterialTheme.typography.headlineSmall
        )

        // --- names ---
        FormField(R.string.drug_trade_name_english, form.tradeNameEnglish) { v ->
            viewModel.update { it.copy(tradeNameEnglish = v) }
        }
        FormField(R.string.drug_trade_name_arabic, form.tradeNameArabic) { v ->
            viewModel.update { it.copy(tradeNameArabic = v) }
        }
        FormField(R.string.drug_generic_name_english, form.genericNameEnglish) { v ->
            viewModel.update { it.copy(genericNameEnglish = v) }
        }
        FormField(R.string.drug_generic_name_arabic, form.genericNameArabic) { v ->
            viewModel.update { it.copy(genericNameArabic = v) }
        }

        // --- composition ---
        FormField(R.string.drug_active_ingredient, form.activeIngredient) { v ->
            viewModel.update { it.copy(activeIngredient = v) }
        }
        FormField(R.string.medication_strength, form.strength) { v ->
            viewModel.update { it.copy(strength = v) }
        }
        FormField(R.string.drug_dosage_form, form.dosageForm) { v ->
            viewModel.update { it.copy(dosageForm = v) }
        }
        FormField(R.string.drug_manufacturer_hint, form.manufacturer) { v ->
            viewModel.update { it.copy(manufacturer = v) }
        }
        FormField(R.string.drug_barcode, form.barcode) { v ->
            viewModel.update { it.copy(barcode = v) }
        }

        // --- usage ---
        FormField(R.string.drug_indications_hint, form.indications, singleLine = false) { v ->
            viewModel.update { it.copy(indications = v) }
        }

        // --- safety ---
        FormField(R.string.drug_contraindications, form.contraindications, singleLine = false) { v ->
            viewModel.update { it.copy(contraindications = v) }
        }
        FormField(R.string.drug_warnings_hint, form.warnings, singleLine = false) { v ->
            viewModel.update { it.copy(warnings = v) }
        }
        FormField(R.string.drug_side_effects, form.sideEffects, singleLine = false) { v ->
            viewModel.update { it.copy(sideEffects = v) }
        }
        FormField(R.string.drug_interactions, form.interactions, singleLine = false) { v ->
            viewModel.update { it.copy(interactions = v) }
        }
        FormField(R.string.drug_pregnancy, form.pregnancyInformation, singleLine = false) { v ->
            viewModel.update { it.copy(pregnancyInformation = v) }
        }
        FormField(R.string.drug_lactation, form.lactationInformation, singleLine = false) { v ->
            viewModel.update { it.copy(lactationInformation = v) }
        }

        // --- product image ---
        Text(
            text = stringResource(R.string.drug_product_image),
            style = MaterialTheme.typography.titleSmall
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            OutlinedButton(onClick = { imagePicker.launch("image/*") }) {
                Text(stringResource(R.string.drug_product_image_pick))
            }
            if (form.productImageUrl.isNotBlank()) {
                OutlinedButton(onClick = viewModel::removeImage) {
                    Text(stringResource(R.string.drug_product_image_remove))
                }
            }
        }
        if (form.productImageUrl.isNotBlank()) {
            Text(
                text = form.productImageUrl,
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        }

        // --- source / verification ---
        Text(
            text = stringResource(R.string.drug_source_section),
            style = MaterialTheme.typography.titleSmall
        )
        FormField(R.string.drug_source_name, form.sourceName) { v ->
            viewModel.update { it.copy(sourceName = v) }
        }
        FormField(R.string.drug_source_url, form.sourceUrl) { v ->
            viewModel.update { it.copy(sourceUrl = v) }
        }
        FormField(R.string.drug_source_date, form.sourceDate) { v ->
            viewModel.update { it.copy(sourceDate = v) }
        }
        Row(
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Switch(
                checked = form.isVerified,
                onCheckedChange = { checked -> viewModel.update { it.copy(isVerified = checked) } }
            )
            Text(
                text = stringResource(
                    if (form.isVerified) R.string.drug_badge_verified else R.string.drug_badge_manual
                ),
                style = MaterialTheme.typography.bodyMedium
            )
        }

        Button(
            onClick = viewModel::save,
            enabled = form.tradeNameEnglish.isNotBlank() ||
                form.tradeNameArabic.isNotBlank() ||
                form.genericNameEnglish.isNotBlank() ||
                form.genericNameArabic.isNotBlank(),
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.medication_save))
        }
        OutlinedButton(onClick = onBack, modifier = Modifier.fillMaxWidth()) {
            Text(stringResource(R.string.cancel))
        }

        androidx.compose.material3.SnackbarHost(hostState = snackbarHostState)
    }
}

@Composable
private fun FormField(
    label: Int,
    value: String,
    singleLine: Boolean = true,
    onChange: (String) -> Unit
) {
    OutlinedTextField(
        value = value,
        onValueChange = onChange,
        label = { Text(stringResource(label)) },
        singleLine = singleLine,
        minLines = if (singleLine) 1 else 2,
        modifier = Modifier.fillMaxWidth()
    )
}
