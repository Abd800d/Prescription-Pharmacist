package com.maakthi.prescriptionpharmacist.presentation.drugs

import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.MedicationType
import com.maakthi.prescriptionpharmacist.domain.repository.DrugRepository
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.serialization.json.Json
import javax.inject.Inject

@HiltViewModel
class DrugDetailsViewModel @Inject constructor(
    private val drugRepository: DrugRepository
) : ViewModel() {

    private val _drug = MutableStateFlow<Drug?>(null)
    val drug: StateFlow<Drug?> = _drug.asStateFlow()

    fun load(drugJson: String?) {
        if (_drug.value != null || drugJson.isNullOrBlank()) return
        val decoded = runCatching {
            Json.decodeFromString<Drug>(Uri.decode(drugJson))
        }.getOrNull()
        _drug.value = decoded
    }

    fun deleteLocal(onDeleted: () -> Unit) {
        val current = _drug.value ?: return
        if (!current.isLocal) return
        viewModelScope.launch {
            drugRepository.deleteDrug(current.id)
            onDeleted()
        }
    }
}

/** Drug details: works for local drugs and openFDA results. */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DrugDetailsScreen(
    drugJson: String?,
    onBack: () -> Unit,
    onEditDrug: (String) -> Unit,
    viewModel: DrugDetailsViewModel = hiltViewModel()
) {
    viewModel.load(drugJson)
    val drug by viewModel.drug.collectAsStateWithLifecycle()
    var confirmDelete by remember { mutableStateOf(false) }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.drug_details_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
                    }
                },
                actions = {
                    val current = drug
                    if (current?.isLocal == true) {
                        IconButton(onClick = { onEditDrug(current.id) }) {
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
                }
            )
        }
    ) { padding ->
        val current = drug
        if (current == null) {
            Text(
                text = stringResource(R.string.error_generic),
                modifier = Modifier
                    .padding(padding)
                    .padding(24.dp)
            )
            return@Scaffold
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(text = current.name, style = MaterialTheme.typography.headlineSmall)
            current.scientificName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.titleMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                DetailChip(
                    stringResource(
                        when (current.type) {
                            MedicationType.BRAND -> R.string.medication_type_brand
                            MedicationType.GENERIC -> R.string.medication_type_generic
                            MedicationType.UNKNOWN -> R.string.medication_type_unknown
                        }
                    )
                )
                DetailChip(
                    stringResource(
                        if (current.isLocal) R.string.drug_source_local
                        else R.string.drug_source_remote
                    )
                )
            }

            current.manufacturer?.let {
                DetailSection(stringResource(R.string.drug_manufacturer_hint), it)
            }
            current.strength?.let { DetailSection(stringResource(R.string.medication_strength), it) }
            current.form?.let { DetailSection(stringResource(R.string.medication_form), it) }
            current.indications?.let {
                DetailSection(stringResource(R.string.drug_indications_hint), it)
            }
            current.dosage?.let { DetailSection(stringResource(R.string.drug_dosage_hint), it) }
            current.warnings?.let { DetailSection(stringResource(R.string.drug_warnings_hint), it) }
            current.notes?.let { DetailSection(stringResource(R.string.drug_notes_hint), it) }

            if (current.isLocal) {
                OutlinedButton(
                    onClick = { onEditDrug(current.id) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(stringResource(R.string.review_edit))
                }
            }
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.drug_delete_title)) },
            text = { Text(stringResource(R.string.drug_delete_message)) },
            confirmButton = {
                TextButton(
                    onClick = {
                        confirmDelete = false
                        viewModel.deleteLocal(onDeleted = onBack)
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

@Composable
private fun DetailSection(title: String, body: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(12.dp)) {
            SectionHeader(title = title)
            Text(text = body, style = MaterialTheme.typography.bodyMedium)
        }
    }
}

@Composable
private fun DetailChip(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelMedium,
        color = MaterialTheme.colorScheme.primary
    )
}
