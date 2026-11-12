package com.maakthi.prescriptionpharmacist.presentation.home

import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountCircle
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CameraAlt
import androidx.compose.material.icons.filled.Chat
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.ImageSearch
import androidx.compose.material.icons.filled.ManageSearch
import androidx.compose.material.icons.filled.MedicalServices
import androidx.compose.material.icons.filled.PhotoLibrary
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SupportAgent
import androidx.compose.material3.Card
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.AppLanguage
import com.maakthi.prescriptionpharmacist.domain.model.Prescription
import com.maakthi.prescriptionpharmacist.presentation.components.LanguageToggleButton
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader
import com.maakthi.prescriptionpharmacist.presentation.components.StatusBadge
import com.maakthi.prescriptionpharmacist.presentation.navigation.Routes
import java.text.DateFormat
import java.util.Date

private data class QuickAction(val icon: ImageVector, val labelRes: Int, val route: String)

@Composable
fun HomeScreen(
    onNavigate: (String) -> Unit,
    onToggleLanguage: (AppLanguage) -> Unit,
    viewModel: HomeViewModel = hiltViewModel()
) {
    val prescriptions by viewModel.prescriptions.collectAsStateWithLifecycle()
    val currentUser by viewModel.currentUser.collectAsStateWithLifecycle()
    val settings by viewModel.settings.collectAsStateWithLifecycle()

    val quickActions = listOf(
        QuickAction(Icons.Filled.CameraAlt, R.string.action_capture, Routes.CAPTURE),
        QuickAction(Icons.Filled.PhotoLibrary, R.string.action_gallery, Routes.GALLERY),
        QuickAction(Icons.Filled.ManageSearch, R.string.action_drug_search, Routes.DRUG_SEARCH),
        QuickAction(Icons.Filled.Add, R.string.action_add_drug, Routes.DRUG_ADD),
        QuickAction(Icons.Filled.Save, R.string.action_saved, Routes.SAVED),
        QuickAction(Icons.Filled.ImageSearch, R.string.action_products, Routes.PRODUCTS),
        QuickAction(Icons.Filled.PhotoLibrary, R.string.action_media, Routes.MEDIA),
        QuickAction(Icons.Filled.FileUpload, R.string.action_transfer, Routes.TRANSFER),
        QuickAction(Icons.Filled.Chat, R.string.action_assistant, Routes.ASSISTANT),
        QuickAction(Icons.Filled.Backup, R.string.action_backup, Routes.BACKUP),
        QuickAction(Icons.Filled.SupportAgent, R.string.action_support, Routes.SUPPORT),
        QuickAction(Icons.Filled.Settings, R.string.action_settings, Routes.SETTINGS),
        QuickAction(Icons.Filled.MedicalServices, R.string.action_language, Routes.LANGUAGE_SETTINGS)
    )

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        // Header
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = stringResource(R.string.home_welcome),
                        style = MaterialTheme.typography.headlineSmall
                    )
                    Text(
                        text = currentUser?.let {
                            stringResource(R.string.account_signed_in, it.uid)
                        } ?: stringResource(R.string.account_guest),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
                LanguageToggleButton(
                    currentLanguage = settings.language,
                    onToggle = { onToggleLanguage(settings.language) }
                )
                IconButton(onClick = { onNavigate(Routes.LOGIN) }) {
                    Icon(
                        Icons.Filled.AccountCircle,
                        contentDescription = stringResource(R.string.login_title),
                        tint = MaterialTheme.colorScheme.primary
                    )
                }
            }
        }

        // Quick actions grid
        item {
            SectionHeader(title = stringResource(R.string.home_quick_actions))
        }
        item {
            LazyVerticalGrid(
                columns = GridCells.Fixed(3),
                modifier = Modifier.height(320.dp),
                horizontalArrangement = Arrangement.spacedBy(10.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp),
                userScrollEnabled = false
            ) {
                items(quickActions) { action ->
                    QuickActionCard(action = action, onClick = { onNavigate(action.route) })
                }
            }
        }

        // Recent prescriptions
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                SectionHeader(
                    title = stringResource(R.string.home_recent),
                    modifier = Modifier.weight(1f)
                )
                if (prescriptions.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.home_view_all),
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary,
                        modifier = Modifier.clickable { onNavigate(Routes.SAVED) }
                    )
                }
            }
        }

        if (prescriptions.isEmpty()) {
            item {
                Text(
                    text = stringResource(R.string.history_empty),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        } else {
            items(prescriptions.take(5), key = { it.id }) { prescription ->
                RecentPrescriptionCard(
                    prescription = prescription,
                    onClick = { onNavigate(Routes.detail(prescription.id)) }
                )
            }
        }

        item {
            Text(
                text = stringResource(R.string.developer_credit),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(top = 8.dp),
            )
        }
    }
}

@Composable
private fun QuickActionCard(
    action: QuickAction,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick),
        shape = RoundedCornerShape(14.dp)
    ) {
        Column(
            modifier = Modifier.padding(vertical = 14.dp, horizontal = 6.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Icon(
                imageVector = action.icon,
                contentDescription = null,
                tint = MaterialTheme.colorScheme.primary,
                modifier = Modifier.size(28.dp)
            )
            Spacer(Modifier.height(6.dp))
            Text(
                text = stringResource(action.labelRes),
                style = MaterialTheme.typography.labelSmall,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun RecentPrescriptionCard(
    prescription: Prescription,
    onClick: () -> Unit
) {
    Card(
        modifier = Modifier
            .fillMaxWidth()
            .clickable(onClick = onClick)
    ) {
        Row(
            modifier = Modifier.padding(12.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(
                    text = DateFormat.getDateTimeInstance(DateFormat.MEDIUM, DateFormat.SHORT)
                        .format(Date(prescription.createdAt)),
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = FontWeight.Medium
                )
                Text(
                    text = prescription.medications.joinToString { it.name }
                        .ifEmpty { stringResource(R.string.review_empty) },
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
            StatusBadge(status = prescription.status)
        }
    }
}
