package com.maakthi.prescriptionpharmacist.presentation.drugs

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType

/** Add a new drug or edit an existing one (local catalog). */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AddEditDrugScreen(
    isEdit: Boolean,
    onBack: () -> Unit,
    onSaved: () -> Unit,
    viewModel: AddEditDrugViewModel = hiltViewModel()
) {
    val form by viewModel.form.collectAsStateWithLifecycle()
    val loading by viewModel.loading.collectAsStateWithLifecycle()
    var typeExpanded by remember { mutableStateOf(false) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                DrugFormEvent.Saved -> onSaved()
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = {
                    Text(
                        stringResource(
                            if (isEdit) R.string.drug_edit_title else R.string.drug_add_title
                        )
                    )
                },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                }
            )
        }
    ) { padding ->
        if (loading) {
            Text(
                text = stringResource(R.string.analysis_running),
                modifier = Modifier.padding(padding).padding(24.dp)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            OutlinedTextField(
                value = form.name,
                onValueChange = { v -> viewModel.update { it.copy(name = v) } },
                label = { Text(stringResource(R.string.medication_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.scientificName,
                onValueChange = { v -> viewModel.update { it.copy(scientificName = v) } },
                label = { Text(stringResource(R.string.medication_scientific_name)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )

            ExposedDropdownMenuBox(
                expanded = typeExpanded,
                onExpandedChange = { typeExpanded = it }
            ) {
                OutlinedTextField(
                    value = stringResource(
                        when (form.type) {
                            MedicationType.BRAND -> R.string.medication_type_brand
                            MedicationType.GENERIC -> R.string.medication_type_generic
                            MedicationType.UNKNOWN -> R.string.medication_type_unknown
                        }
                    ),
                    onValueChange = {},
                    readOnly = true,
                    label = { Text(stringResource(R.string.medication_type_label)) },
                    trailingIcon = {
                        ExposedDropdownMenuDefaults.TrailingIcon(expanded = typeExpanded)
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .menuAnchor(MenuAnchorType.PrimaryNotEditable)
                )
                ExposedDropdownMenu(
                    expanded = typeExpanded,
                    onDismissRequest = { typeExpanded = false }
                ) {
                    MedicationType.entries.forEach { entry ->
                        DropdownMenuItem(
                            text = {
                                Text(
                                    stringResource(
                                        when (entry) {
                                            MedicationType.BRAND -> R.string.medication_type_brand
                                            MedicationType.GENERIC -> R.string.medication_type_generic
                                            MedicationType.UNKNOWN -> R.string.medication_type_unknown
                                        }
                                    )
                                )
                            },
                            onClick = {
                                viewModel.update { it.copy(type = entry) }
                                typeExpanded = false
                            }
                        )
                    }
                }
            }

            OutlinedTextField(
                value = form.strength,
                onValueChange = { v -> viewModel.update { it.copy(strength = v) } },
                label = { Text(stringResource(R.string.medication_strength)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.form,
                onValueChange = { v -> viewModel.update { it.copy(form = v) } },
                label = { Text(stringResource(R.string.medication_form)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.manufacturer,
                onValueChange = { v -> viewModel.update { it.copy(manufacturer = v) } },
                label = { Text(stringResource(R.string.drug_manufacturer_hint)) },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.indications,
                onValueChange = { v -> viewModel.update { it.copy(indications = v) } },
                label = { Text(stringResource(R.string.drug_indications_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.dosage,
                onValueChange = { v -> viewModel.update { it.copy(dosage = v) } },
                label = { Text(stringResource(R.string.drug_dosage_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.warnings,
                onValueChange = { v -> viewModel.update { it.copy(warnings = v) } },
                label = { Text(stringResource(R.string.drug_warnings_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )
            OutlinedTextField(
                value = form.notes,
                onValueChange = { v -> viewModel.update { it.copy(notes = v) } },
                label = { Text(stringResource(R.string.drug_notes_hint)) },
                minLines = 2,
                modifier = Modifier.fillMaxWidth()
            )

            Button(
                onClick = viewModel::save,
                enabled = form.name.isNotBlank(),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(stringResource(R.string.medication_save))
            }
        }
    }
}
