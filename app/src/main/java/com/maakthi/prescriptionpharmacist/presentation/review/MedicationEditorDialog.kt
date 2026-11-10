package com.maakthi.prescriptionpharmacist.presentation.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.MenuAnchorType
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType

/**
 * Dialog to add a medication manually or edit one extracted by the AI.
 * Manual entries carry full pharmacist confidence (1.0).
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MedicationEditorDialog(
    initial: EditableMedication?,
    onDismiss: () -> Unit,
    onSave: (EditableMedication) -> Unit
) {
    var name by remember { mutableStateOf(initial?.name.orEmpty()) }
    var scientificName by remember { mutableStateOf(initial?.scientificName.orEmpty()) }
    var strength by remember { mutableStateOf(initial?.strength.orEmpty()) }
    var form by remember { mutableStateOf(initial?.form.orEmpty()) }
    var type by remember { mutableStateOf(initial?.type ?: MedicationType.UNKNOWN) }
    var typeExpanded by remember { mutableStateOf(false) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(
                stringResource(
                    if (initial == null) R.string.review_add_medication else R.string.review_edit
                )
            )
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = name,
                    onValueChange = { name = it },
                    label = { Text(stringResource(R.string.medication_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = scientificName,
                    onValueChange = { scientificName = it },
                    label = { Text(stringResource(R.string.medication_scientific_name)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = strength,
                    onValueChange = { strength = it },
                    label = { Text(stringResource(R.string.medication_strength)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                OutlinedTextField(
                    value = form,
                    onValueChange = { form = it },
                    label = { Text(stringResource(R.string.medication_form)) },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true
                )
                ExposedDropdownMenuBox(
                    expanded = typeExpanded,
                    onExpandedChange = { typeExpanded = it }
                ) {
                    OutlinedTextField(
                        value = stringResource(
                            when (type) {
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
                                    type = entry
                                    typeExpanded = false
                                }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(
                enabled = name.isNotBlank(),
                onClick = {
                    onSave(
                        EditableMedication(
                            id = initial?.id ?: java.util.UUID.randomUUID().toString(),
                            name = name.trim(),
                            scientificName = scientificName.trim().takeIf { it.isNotEmpty() },
                            type = type,
                            strength = strength.trim().takeIf { it.isNotEmpty() },
                            form = form.trim().takeIf { it.isNotEmpty() },
                            confidence = initial?.confidence ?: 1f,
                            rawText = initial?.rawText
                        )
                    )
                }
            ) {
                Text(stringResource(R.string.medication_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.medication_cancel))
            }
        }
    )
}
