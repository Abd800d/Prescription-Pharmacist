package com.maakthi.prescriptionpharmacist.presentation.review

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.PrescriptionStatus
import com.maakthi.prescriptionpharmacist.presentation.components.ConfidenceChip

/**
 * Final pharmacist step: edit / add / delete extracted medications, add
 * notes, then confirm and save (or keep as draft / discard).
 */
@Composable
fun ConfirmEditScreen(
    onSaved: () -> Unit,
    onDiscarded: () -> Unit,
    viewModel: ReviewFlowViewModel
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val notes by viewModel.notes.collectAsStateWithLifecycle()

    var showEditor by remember { mutableStateOf(false) }
    var editingMedication by remember { mutableStateOf<EditableMedication?>(null) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                ReviewEvent.Saved -> onSaved()
            }
        }
    }

    val state = uiState as? ReviewUiState.Result

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Text(
            text = stringResource(R.string.review_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Text(
            text = stringResource(R.string.review_subtitle),
            style = MaterialTheme.typography.bodyMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )

        LazyColumn(
            modifier = Modifier.weight(1f),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (state == null || state.medications.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.review_empty),
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            items(state?.medications ?: emptyList(), key = { it.id }) { medication ->
                EditableMedicationCard(
                    medication = medication,
                    onEdit = {
                        editingMedication = medication
                        showEditor = true
                    },
                    onDelete = { viewModel.removeMedication(medication.id) }
                )
            }
            item {
                OutlinedButton(
                    onClick = {
                        editingMedication = null
                        showEditor = true
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(Modifier.size(8.dp))
                    Text(stringResource(R.string.review_add_medication))
                }
            }
            item {
                OutlinedTextField(
                    value = notes,
                    onValueChange = viewModel::onNotesChanged,
                    label = { Text(stringResource(R.string.review_notes_hint)) },
                    modifier = Modifier.fillMaxWidth(),
                    minLines = 2
                )
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Button(
                onClick = { viewModel.save(PrescriptionStatus.CONFIRMED) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.review_confirm))
            }
            OutlinedButton(
                onClick = { viewModel.save(PrescriptionStatus.PENDING) },
                modifier = Modifier.weight(1f)
            ) {
                Text(stringResource(R.string.review_save_draft))
            }
            TextButton(
                onClick = {
                    viewModel.discard()
                    onDiscarded()
                }
            ) {
                Text(stringResource(R.string.review_reject))
            }
        }
    }

    if (showEditor) {
        MedicationEditorDialog(
            initial = editingMedication,
            onDismiss = { showEditor = false },
            onSave = { medication ->
                val existing = editingMedication
                if (existing == null) {
                    viewModel.addMedication(medication)
                } else {
                    viewModel.updateMedication(medication.copy(id = existing.id))
                }
                showEditor = false
            }
        )
    }
}

@Composable
private fun EditableMedicationCard(
    medication: EditableMedication,
    onEdit: () -> Unit,
    onDelete: () -> Unit
) {
    var confirmDelete by remember { mutableStateOf(false) }

    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(text = medication.name, style = MaterialTheme.typography.titleMedium)
                    medication.scientificName?.let {
                        Text(
                            text = it,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    if (medication.strength != null || medication.form != null) {
                        Text(
                            text = listOfNotNull(medication.strength, medication.form)
                                .joinToString(" · "),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                IconButton(onClick = onEdit) {
                    Icon(
                        Icons.Filled.Edit,
                        contentDescription = stringResource(R.string.review_edit),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
                IconButton(onClick = { confirmDelete = true }) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.review_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            ConfidenceChip(
                level = medication.confidenceLevel,
                confidence = medication.confidence
            )
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.history_delete_title)) },
            text = { Text(stringResource(R.string.history_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        onDelete()
                    }
                ) {
                    Text(stringResource(R.string.history_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }
}
