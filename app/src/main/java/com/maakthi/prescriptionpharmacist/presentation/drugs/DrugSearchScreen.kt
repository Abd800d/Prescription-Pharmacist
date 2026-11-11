package com.maakthi.prescriptionpharmacist.presentation.drugs

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.ExposedDropdownMenuBox
import androidx.compose.material3.ExposedDropdownMenuDefaults
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TextField
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.VerificationFilter
import com.maakthi.prescriptionpharmacist.presentation.components.EmptyStateView
import com.maakthi.prescriptionpharmacist.presentation.components.LoadingView
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader

/**
 * Drug database search: multi-field query (trade/generic names, active
 * ingredient, barcode), result filters, verified/manual badges, delete with
 * confirmation, and import from the openFDA database.
 */
@Composable
fun DrugSearchScreen(
    onOpenDrug: (String) -> Unit,
    onAddDrug: () -> Unit,
    viewModel: DrugSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val filters by viewModel.filters.collectAsStateWithLifecycle()
    val options by viewModel.options.collectAsStateWithLifecycle()
    val deleteTarget by viewModel.deleteTarget.collectAsStateWithLifecycle()

    deleteTarget?.let { drug ->
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.drug_delete_title)) },
            text = {
                Text(stringResource(R.string.drug_delete_message) + "\n" + drug.name)
            },
            confirmButton = {
                TextButton(onClick = viewModel::confirmDelete) {
                    Text(stringResource(R.string.drug_action_delete))
                }
            },
            dismissButton = {
                TextButton(onClick = viewModel::dismissDelete) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.drug_search_title),
            style = MaterialTheme.typography.headlineSmall
        )

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.drug_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )

        DrugFilterRow(filters = filters, options = options, viewModel = viewModel)

        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            ExtendedFloatingActionButton(
                onClick = viewModel::search,
                icon = { Icon(Icons.Filled.Search, contentDescription = null) },
                text = { Text(stringResource(R.string.drug_search_action)) }
            )
            ExtendedFloatingActionButton(
                onClick = onAddDrug,
                icon = { Icon(Icons.Filled.Add, contentDescription = null) },
                text = { Text(stringResource(R.string.review_add_medication)) }
            )
        }

        when (val state = uiState) {
            DrugSearchUiState.Idle -> EmptyStateView(
                message = stringResource(R.string.drug_search_empty),
                modifier = Modifier.weight(1f)
            )

            DrugSearchUiState.Loading -> LoadingView(
                message = stringResource(R.string.analysis_running),
                modifier = Modifier.fillMaxSize()
            )

            DrugSearchUiState.Empty -> EmptyStateView(
                message = stringResource(R.string.drug_search_no_results),
                modifier = Modifier.weight(1f)
            )

            is DrugSearchUiState.Error -> EmptyStateView(
                message = state.message ?: stringResource(R.string.drug_search_error),
                modifier = Modifier.weight(1f)
            )

            is DrugSearchUiState.Results -> {
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (state.local.isNotEmpty()) {
                        item { SectionHeader(title = stringResource(R.string.drug_search_local)) }
                        items(state.local, key = { "local-${it.id}" }) { drug ->
                            DrugRow(
                                drug = drug,
                                onClick = { onOpenDrug(drug.id) },
                                onDelete = { viewModel.requestDelete(drug) }
                            )
                        }
                    }
                    if (state.remote.isNotEmpty()) {
                        item { SectionHeader(title = stringResource(R.string.drug_search_remote)) }
                        items(state.remote, key = { "remote-${it.id}" }) { drug ->
                            DrugRow(
                                drug = drug,
                                onClick = { viewModel.importRemote(drug, onOpenDrug) },
                                onDelete = null
                            )
                        }
                    }
                }
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun DrugFilterRow(
    filters: com.maakthi.prescriptionpharmacist.domain.model.DrugFilters,
    options: com.maakthi.prescriptionpharmacist.domain.model.DrugFilterOptions,
    viewModel: DrugSearchViewModel
) {
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterChip(
                selected = filters.verification == VerificationFilter.ALL,
                onClick = { viewModel.onVerificationFilter(VerificationFilter.ALL) },
                label = { Text(stringResource(R.string.drug_filter_all)) }
            )
            FilterChip(
                selected = filters.verification == VerificationFilter.VERIFIED,
                onClick = { viewModel.onVerificationFilter(VerificationFilter.VERIFIED) },
                label = { Text(stringResource(R.string.drug_filter_verified)) }
            )
            FilterChip(
                selected = filters.verification == VerificationFilter.MANUAL,
                onClick = { viewModel.onVerificationFilter(VerificationFilter.MANUAL) },
                label = { Text(stringResource(R.string.drug_filter_manual)) }
            )
        }
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            FilterDropdown(
                label = stringResource(R.string.drug_filter_dosage_form),
                selected = filters.dosageForm,
                values = options.dosageForms,
                onSelect = viewModel::onDosageFormFilter,
                modifier = Modifier.weight(1f)
            )
            FilterDropdown(
                label = stringResource(R.string.drug_filter_manufacturer),
                selected = filters.manufacturer,
                values = options.manufacturers,
                onSelect = viewModel::onManufacturerFilter,
                modifier = Modifier.weight(1f)
            )
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun FilterDropdown(
    label: String,
    selected: String?,
    values: List<String>,
    onSelect: (String?) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    ExposedDropdownMenuBox(
        expanded = expanded,
        onExpandedChange = { expanded = it },
        modifier = modifier
    ) {
        TextField(
            value = selected ?: stringResource(R.string.drug_filter_any),
            onValueChange = {},
            readOnly = true,
            label = { Text(label) },
            trailingIcon = { ExposedDropdownMenuDefaults.TrailingIcon(expanded = expanded) },
            modifier = Modifier
                .fillMaxWidth()
                .menuAnchor(androidx.compose.material3.MenuAnchorType.PrimaryNotEditable)
        )
        ExposedDropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
            DropdownMenuItem(
                text = { Text(stringResource(R.string.drug_filter_any)) },
                onClick = {
                    onSelect(null)
                    expanded = false
                }
            )
            values.forEach { value ->
                DropdownMenuItem(
                    text = { Text(value) },
                    onClick = {
                        onSelect(value)
                        expanded = false
                    }
                )
            }
        }
    }
}

@Composable
private fun DrugRow(
    drug: Drug,
    onClick: () -> Unit,
    onDelete: (() -> Unit)?
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = drug.name,
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = stringResource(
                            if (drug.isVerified) R.string.drug_badge_verified
                            else R.string.drug_badge_manual
                        ),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (drug.isVerified) {
                            MaterialTheme.colorScheme.primary
                        } else {
                            MaterialTheme.colorScheme.outline
                        }
                    )
                }
                drug.scientificName?.let {
                    Text(text = it, style = MaterialTheme.typography.bodyMedium)
                }
                val subtitle = listOfNotNull(
                    drug.strength,
                    drug.dosageForm ?: drug.form,
                    drug.manufacturer,
                    drug.activeIngredient
                ).joinToString(" • ")
                if (subtitle.isNotEmpty()) {
                    Text(
                        text = subtitle,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }
            if (onDelete != null) {
                IconButton(onClick = onDelete) {
                    Icon(
                        Icons.Filled.Delete,
                        contentDescription = stringResource(R.string.drug_action_delete),
                        tint = MaterialTheme.colorScheme.error
                    )
                }
            }
        }
    }
}
