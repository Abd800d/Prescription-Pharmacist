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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Card
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.presentation.components.EmptyStateView
import com.maakthi.prescriptionpharmacist.presentation.components.LoadingView
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader

/**
 * Drug search across the local catalog (Room) and the public openFDA
 * database. Results open the drug details screen.
 */
@Composable
fun DrugSearchScreen(
    onOpenDrug: (Drug) -> Unit,
    onAddDrug: () -> Unit,
    viewModel: DrugSearchViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()

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
                            DrugRow(drug = drug, onClick = { onOpenDrug(drug) })
                        }
                    }
                    if (state.remote.isNotEmpty()) {
                        item { SectionHeader(title = stringResource(R.string.drug_search_remote)) }
                        items(state.remote, key = { "remote-${it.id}" }) { drug ->
                            DrugRow(drug = drug, onClick = { onOpenDrug(drug) })
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun DrugRow(drug: Drug, onClick: () -> Unit) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Column(modifier = Modifier.padding(12.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(
                    text = drug.name,
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = stringResource(
                        if (drug.isLocal) R.string.drug_source_local else R.string.drug_source_remote
                    ),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }
            drug.scientificName?.let {
                Text(
                    text = it,
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            if (drug.strength != null || drug.form != null) {
                Text(
                    text = listOfNotNull(drug.strength, drug.form).joinToString(" · "),
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }
    }
}
