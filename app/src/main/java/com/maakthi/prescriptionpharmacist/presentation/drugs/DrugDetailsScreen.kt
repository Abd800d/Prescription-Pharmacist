package com.maakthi.prescriptionpharmacist.presentation.drugs

import android.graphics.BitmapFactory
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.Drug
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditAction
import com.maakthi.prescriptionpharmacist.domain.model.DrugAuditEntry
import com.maakthi.prescriptionpharmacist.presentation.components.LoadingView
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader
import java.text.DateFormat
import java.util.Date

/**
 * Full drug record: names, composition, usage, safety text, product image,
 * source/verification status and the change history. Delete asks for
 * confirmation first.
 */
@Composable
fun DrugDetailsScreen(
    onEditDrug: (String) -> Unit,
    onBack: () -> Unit,
    viewModel: DrugDetailsViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val confirmDelete by viewModel.confirmDelete.collectAsStateWithLifecycle()
    val deleted by viewModel.deleted.collectAsStateWithLifecycle()

    LaunchedEffect(deleted) {
        if (deleted) onBack()
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = viewModel::dismissDelete,
            title = { Text(stringResource(R.string.drug_delete_title)) },
            text = { Text(stringResource(R.string.drug_delete_message)) },
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

    when (val state = uiState) {
        DrugDetailsUiState.Loading -> LoadingView(
            message = stringResource(R.string.analysis_running),
            modifier = Modifier.fillMaxSize()
        )

        DrugDetailsUiState.NotFound -> Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Text(stringResource(R.string.drug_search_no_results))
            Button(onClick = onBack) { Text(stringResource(R.string.back)) }
        }

        is DrugDetailsUiState.Loaded -> DrugDetailsContent(
            state = state,
            onEditDrug = onEditDrug,
            onDelete = viewModel::requestDelete
        )
    }
}

@Composable
private fun DrugDetailsContent(
    state: DrugDetailsUiState.Loaded,
    onEditDrug: (String) -> Unit,
    onDelete: () -> Unit
) {
    val drug = state.drug
    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(
                text = drug.name,
                style = MaterialTheme.typography.headlineSmall,
                modifier = Modifier.weight(1f)
            )
            Text(
                text = stringResource(
                    if (drug.isVerified) R.string.drug_badge_verified
                    else R.string.drug_badge_manual
                ),
                style = MaterialTheme.typography.labelLarge,
                color = if (drug.isVerified) MaterialTheme.colorScheme.primary
                else MaterialTheme.colorScheme.outline
            )
        }

        SectionHeader(title = stringResource(R.string.drug_details_basic))
        Field(R.string.drug_trade_name_arabic, drug.tradeNameArabic)
        Field(R.string.drug_trade_name_english, drug.tradeNameEnglish)
        Field(R.string.drug_generic_name_arabic, drug.genericNameArabic)
        Field(R.string.drug_generic_name_english, drug.genericNameEnglish)

        SectionHeader(title = stringResource(R.string.drug_details_composition))
        Field(R.string.drug_active_ingredient, drug.activeIngredient)
        Field(R.string.medication_strength, drug.strength)
        Field(R.string.drug_dosage_form, drug.dosageForm ?: drug.form)
        Field(R.string.drug_manufacturer_hint, drug.manufacturer)
        Field(R.string.drug_barcode, drug.barcode)

        SectionHeader(title = stringResource(R.string.drug_details_usage))
        Field(R.string.drug_indications_hint, drug.indications)
        Field(R.string.drug_dosage_hint, drug.dosage)

        SectionHeader(title = stringResource(R.string.drug_details_safety))
        Field(R.string.drug_contraindications, drug.contraindications)
        Field(R.string.drug_warnings_hint, drug.warnings)
        Field(R.string.drug_side_effects, drug.sideEffects)
        Field(R.string.drug_interactions, drug.interactions)
        Field(R.string.drug_pregnancy, drug.pregnancyInformation)
        Field(R.string.drug_lactation, drug.lactationInformation)

        drug.productImageUrl?.let { path ->
            SectionHeader(title = stringResource(R.string.drug_product_image))
            val bitmap = remember(path) {
                runCatching {
                    val options = BitmapFactory.Options().apply { inSampleSize = 4 }
                    BitmapFactory.decodeFile(path, options)
                }.getOrNull()
            }
            bitmap?.let {
                Image(
                    bitmap = it.asImageBitmap(),
                    contentDescription = stringResource(R.string.drug_product_image_desc),
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp)
                )
            }
        }

        SectionHeader(title = stringResource(R.string.drug_source_section))
        Field(R.string.drug_source_name, drug.sourceName)
        Field(R.string.drug_source_url, drug.sourceUrl)
        Field(R.string.drug_source_date, drug.sourceDate)
        Field(R.string.drug_created_by, drug.createdBy)
        Field(
            R.string.drug_updated_at,
            drug.updatedAt.takeIf { it > 0 }?.let { formatTime(it) }
        )

        SectionHeader(title = stringResource(R.string.drug_audit_section))
        if (state.audit.isEmpty()) {
            Text(
                text = stringResource(R.string.drug_audit_empty),
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.outline
            )
        } else {
            state.audit.forEach { entry -> AuditRow(entry) }
        }

        Button(
            onClick = { onEditDrug(drug.id) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(Icons.Filled.Edit, contentDescription = null)
            Text(
                text = stringResource(R.string.drug_action_edit),
                modifier = Modifier.padding(start = 8.dp)
            )
        }
        OutlinedButton(
            onClick = onDelete,
            modifier = Modifier.fillMaxWidth()
        ) {
            Icon(
                Icons.Filled.Delete,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.error
            )
            Text(
                text = stringResource(R.string.drug_action_delete),
                color = MaterialTheme.colorScheme.error,
                modifier = Modifier.padding(start = 8.dp)
            )
        }
    }
}

@Composable
private fun Field(label: Int, value: String?) {
    if (!value.isNullOrBlank()) {
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = stringResource(label),
                    style = MaterialTheme.typography.labelMedium,
                    color = MaterialTheme.colorScheme.outline
                )
                Text(text = value, style = MaterialTheme.typography.bodyMedium)
            }
        }
    }
}

@Composable
private fun AuditRow(entry: DrugAuditEntry) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(10.dp)) {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = stringResource(
                        when (entry.action) {
                            DrugAuditAction.ADD -> R.string.drug_audit_add
                            DrugAuditAction.UPDATE -> R.string.drug_audit_update
                            DrugAuditAction.DELETE -> R.string.drug_audit_delete
                        }
                    ),
                    style = MaterialTheme.typography.labelLarge,
                    fontWeight = FontWeight.Bold,
                    modifier = Modifier.weight(1f)
                )
                Text(
                    text = formatTime(entry.timestamp),
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.outline
                )
            }
            Text(text = entry.changes, style = MaterialTheme.typography.bodySmall)
            Text(
                text = stringResource(R.string.drug_audit_by, entry.performedBy),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.outline
            )
        }
    }
}

private fun formatTime(millis: Long): String =
    DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT).format(Date(millis))
