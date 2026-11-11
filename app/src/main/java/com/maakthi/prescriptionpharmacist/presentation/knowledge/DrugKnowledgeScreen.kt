package com.maakthi.prescriptionpharmacist.presentation.knowledge

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
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.RadioButton
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
import com.maakthi.prescriptionpharmacist.domain.model.DrugFact
import com.maakthi.prescriptionpharmacist.domain.model.DrugKnowledgeConflict
import com.maakthi.prescriptionpharmacist.domain.model.KnowledgeWarning
import com.maakthi.prescriptionpharmacist.presentation.components.EmptyStateView
import com.maakthi.prescriptionpharmacist.presentation.components.LoadingView
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader

/**
 * Drug-knowledge search over OFFICIAL sources only (openFDA, authority
 * APIs). Every fact shows its source name, link and update time. When two
 * sources disagree the app shows a warning and lets the pharmacist pick
 * manually — it never chooses a value automatically.
 */
@Composable
fun DrugKnowledgeScreen(
    onImportForReview: () -> Unit,
    viewModel: DrugKnowledgeViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val query by viewModel.query.collectAsStateWithLifecycle()
    val conflictPicks by viewModel.conflictPicks.collectAsStateWithLifecycle()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(
            text = stringResource(R.string.knowledge_title),
            style = MaterialTheme.typography.headlineSmall
        )
        Card(
            modifier = Modifier.fillMaxWidth(),
            colors = CardDefaults.cardColors(
                containerColor = MaterialTheme.colorScheme.secondaryContainer
            )
        ) {
            Text(
                text = stringResource(R.string.knowledge_disclaimer),
                style = MaterialTheme.typography.bodySmall,
                modifier = Modifier.padding(10.dp)
            )
        }

        OutlinedTextField(
            value = query,
            onValueChange = viewModel::onQueryChange,
            label = { Text(stringResource(R.string.knowledge_search_hint)) },
            leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Button(
            onClick = viewModel::search,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(stringResource(R.string.knowledge_search_action))
        }

        when (val state = uiState) {
            DrugKnowledgeUiState.Idle -> EmptyStateView(
                message = stringResource(R.string.knowledge_idle),
                modifier = Modifier.weight(1f)
            )

            DrugKnowledgeUiState.Loading -> LoadingView(
                message = stringResource(R.string.analysis_running),
                modifier = Modifier.fillMaxSize()
            )

            DrugKnowledgeUiState.Empty -> EmptyStateView(
                message = stringResource(R.string.knowledge_no_results),
                modifier = Modifier.weight(1f)
            )

            is DrugKnowledgeUiState.Error -> EmptyStateView(
                message = state.message,
                modifier = Modifier.weight(1f)
            )

            is DrugKnowledgeUiState.Loaded -> {
                val result = state.result
                LazyColumn(
                    modifier = Modifier.weight(1f),
                    contentPadding = PaddingValues(bottom = 80.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (KnowledgeWarning.SOURCE_UNAVAILABLE in result.warnings) {
                        item {
                            WarningBanner(
                                text = stringResource(
                                    R.string.knowledge_source_unavailable,
                                    result.failedSources.joinToString { it.name }
                                )
                            )
                        }
                    }
                    if (KnowledgeWarning.CONFLICT_PRESENT in result.warnings) {
                        item {
                            WarningBanner(text = stringResource(R.string.knowledge_conflict_warning))
                        }
                    }

                    if (result.conflicts.isNotEmpty()) {
                        item { SectionHeader(title = stringResource(R.string.knowledge_conflicts_section)) }
                        items(result.conflicts, key = { "conflict-${it.field}" }) { conflict ->
                            ConflictCard(
                                conflict = conflict,
                                picked = conflictPicks[conflict.field],
                                onPick = { viewModel.pickConflict(conflict.field, it) }
                            )
                        }
                    }

                    if (result.facts.isNotEmpty()) {
                        item { SectionHeader(title = stringResource(R.string.knowledge_facts_section)) }
                        items(result.facts, key = { "fact-${it.field}-${it.source.id}" }) { fact ->
                            FactCard(fact = fact)
                        }
                    }

                    item {
                        Button(
                            onClick = { viewModel.importForReview(onImportForReview) },
                            enabled = result.facts.isNotEmpty(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.knowledge_import))
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun WarningBanner(text: String) {
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
    ) {
        Text(
            text = text,
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onErrorContainer,
            modifier = Modifier.padding(10.dp)
        )
    }
}

@Composable
private fun ConflictCard(
    conflict: DrugKnowledgeConflict,
    picked: DrugFact?,
    onPick: (DrugFact) -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.knowledge_conflict_field, fieldLabel(conflict.field)),
                style = MaterialTheme.typography.titleSmall,
                fontWeight = FontWeight.Bold
            )
            Text(
                text = stringResource(R.string.knowledge_conflict_pick),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
            conflict.facts.forEach { fact ->
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { onPick(fact) }
                ) {
                    RadioButton(selected = picked == fact, onClick = { onPick(fact) })
                    Column {
                        Text(text = fact.value, style = MaterialTheme.typography.bodySmall)
                        SourceLine(fact)
                    }
                }
            }
        }
    }
}

@Composable
private fun FactCard(fact: DrugFact) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(4.dp)
        ) {
            Text(
                text = fieldLabel(fact.field),
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.primary
            )
            Text(text = fact.value, style = MaterialTheme.typography.bodyMedium)
            SourceLine(fact)
        }
    }
}

@Composable
private fun SourceLine(fact: DrugFact) {
    Text(
        text = stringResource(
            R.string.knowledge_source_line,
            fact.source.name,
            formatTime(fact.source.retrievedAt),
            fact.source.url
        ),
        style = MaterialTheme.typography.labelSmall,
        color = MaterialTheme.colorScheme.outline
    )
}

@Composable
private fun fieldLabel(field: String): String = stringResource(
    when (field) {
        "tradeName" -> R.string.drug_trade_name_english
        "genericName" -> R.string.drug_generic_name_english
        "activeIngredient" -> R.string.drug_active_ingredient
        "strength" -> R.string.medication_strength
        "dosageForm" -> R.string.drug_dosage_form
        "manufacturer" -> R.string.drug_manufacturer_hint
        "indications" -> R.string.drug_indications_hint
        "dosage" -> R.string.drug_dosage_hint
        "warnings" -> R.string.drug_warnings_hint
        "contraindications" -> R.string.drug_contraindications
        "sideEffects" -> R.string.drug_side_effects
        "interactions" -> R.string.drug_interactions
        "pregnancyInformation" -> R.string.drug_pregnancy
        "lactationInformation" -> R.string.drug_lactation
        "description" -> R.string.drug_notes_hint
        else -> R.string.drug_notes_hint
    }
)

private fun formatTime(millis: Long): String =
    java.text.DateFormat.getDateTimeInstance(
        java.text.DateFormat.MEDIUM,
        java.text.DateFormat.SHORT
    ).format(java.util.Date(millis))
