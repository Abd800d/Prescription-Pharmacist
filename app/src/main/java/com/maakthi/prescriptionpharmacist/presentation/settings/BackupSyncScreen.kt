package com.maakthi.prescriptionpharmacist.presentation.settings

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
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
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.ConnectionState
import com.maakthi.prescriptionpharmacist.domain.model.ConflictChoice
import com.maakthi.prescriptionpharmacist.domain.model.SyncConflict
import java.text.DateFormat
import java.util.Date

/**
 * Sync center: real phone↔server sync (drugs, prescriptions, images),
 * offline-first with connection status, last sync time, "sync now", conflict
 * resolution by explicit user choice, and encrypted manual/automatic backups
 * with restore.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun BackupSyncScreen(
    onBack: () -> Unit,
    viewModel: BackupSyncViewModel = hiltViewModel()
) {
    val status by viewModel.status.collectAsStateWithLifecycle()
    val conflicts by viewModel.conflicts.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val hasPassphrase by viewModel.hasPassphrase.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    var passphraseDialog by remember { mutableStateOf(false) }
    var restoreFilePassDialog by remember { mutableStateOf<android.net.Uri?>(null) }

    val syncDoneMsg = stringResource(R.string.sync_done)
    val backupDoneMsg = stringResource(R.string.sync_backup_done)
    val restoredMsg = stringResource(R.string.sync_restored)
    val passphraseSavedMsg = stringResource(R.string.sync_passphrase_saved)
    val errorOffline = stringResource(R.string.sync_error_offline)
    val errorPassphrase = stringResource(R.string.sync_error_passphrase)
    val errorGeneric = stringResource(R.string.sync_error_generic)

    val backupFilePicker = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri -> if (uri != null) restoreFilePassDialog = uri }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            val message = when (event) {
                is SyncEvent.SyncDone -> if (event.report.interrupted) errorOffline
                else syncDoneMsg
                SyncEvent.BackupDone -> backupDoneMsg
                SyncEvent.Restored -> restoredMsg
                SyncEvent.PassphraseSaved -> passphraseSavedMsg
                is SyncEvent.Failed -> when (event.message) {
                    "offline" -> errorOffline
                    "backup_passphrase_missing" -> errorPassphrase
                    else -> errorGeneric
                }
            }
            snackbarHostState.showSnackbar(message)
        }
    }

    if (passphraseDialog) {
        PassphraseSetupDialog(
            onSave = {
                passphraseDialog = false
                viewModel.savePassphrase(it)
            },
            onDismiss = { passphraseDialog = false }
        )
    }

    restoreFilePassDialog?.let { uri ->
        PassphraseSetupDialog(
            title = stringResource(R.string.sync_restore_file),
            onSave = { passphrase ->
                restoreFilePassDialog = null
                viewModel.restoreFromFile(uri, passphrase)
            },
            onDismiss = { restoreFilePassDialog = null }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.sync_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- status + sync now ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            AssistChip(
                                onClick = {},
                                label = {
                                    Text(
                                        when (status.connection) {
                                            ConnectionState.ONLINE -> stringResource(R.string.sync_online)
                                            ConnectionState.OFFLINE -> stringResource(R.string.sync_offline)
                                            ConnectionState.UNKNOWN -> stringResource(R.string.sync_unknown)
                                        }
                                    )
                                }
                            )
                        }
                        Text(
                            text = stringResource(
                                R.string.sync_last_sync,
                                if (status.lastSyncAt > 0) {
                                    DateFormat.getDateTimeInstance().format(Date(status.lastSyncAt))
                                } else {
                                    stringResource(R.string.sync_never)
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Text(
                            text = stringResource(
                                R.string.sync_last_backup,
                                if (status.lastBackupAt > 0) {
                                    DateFormat.getDateTimeInstance().format(Date(status.lastBackupAt))
                                } else {
                                    stringResource(R.string.sync_never)
                                }
                            ),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Button(
                            onClick = viewModel::syncNow,
                            enabled = !busy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(stringResource(R.string.sync_now))
                        }
                        Text(
                            text = stringResource(R.string.sync_offline_note),
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.outline
                        )
                    }
                }
            }

            // ---- conflicts ----
            item { Text(stringResource(R.string.sync_conflicts_title), style = MaterialTheme.typography.titleSmall) }
            if (conflicts.isEmpty()) {
                item {
                    Text(
                        text = stringResource(R.string.sync_no_conflicts),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            } else {
                items(conflicts, key = { it.id }) { conflict ->
                    ConflictCard(
                        conflict = conflict,
                        onKeepLocal = { viewModel.resolveConflict(conflict.id, ConflictChoice.KEEP_LOCAL) },
                        onKeepRemote = { viewModel.resolveConflict(conflict.id, ConflictChoice.KEEP_REMOTE) }
                    )
                }
            }

            // ---- backups ----
            item {
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(
                        modifier = Modifier.padding(12.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Text(stringResource(R.string.sync_backup_title), style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = stringResource(R.string.sync_backup_hint),
                            style = MaterialTheme.typography.bodySmall
                        )
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = stringResource(R.string.sync_auto_backup),
                                style = MaterialTheme.typography.bodyMedium,
                                modifier = Modifier.weight(1f)
                            )
                            Switch(
                                checked = status.autoBackupEnabled && hasPassphrase,
                                onCheckedChange = viewModel::setAutoBackup
                            )
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = { passphraseDialog = true }) {
                                Text(
                                    if (hasPassphrase) stringResource(R.string.sync_change_passphrase)
                                    else stringResource(R.string.sync_set_passphrase)
                                )
                            }
                            Button(onClick = viewModel::backupNow, enabled = hasPassphrase && !busy) {
                                Text(stringResource(R.string.sync_backup_now))
                            }
                        }
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            OutlinedButton(onClick = viewModel::restoreFromCloud, enabled = hasPassphrase && !busy) {
                                Text(stringResource(R.string.sync_restore_cloud))
                            }
                            OutlinedButton(
                                onClick = { backupFilePicker.launch(arrayOf("*/*")) },
                                enabled = !busy
                            ) {
                                Text(stringResource(R.string.sync_restore_file))
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun ConflictCard(
    conflict: SyncConflict,
    onKeepLocal: () -> Unit,
    onKeepRemote: () -> Unit
) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(
            modifier = Modifier.padding(12.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Text(
                text = stringResource(R.string.sync_conflict_item, conflict.entityId),
                style = MaterialTheme.typography.titleSmall
            )
            Text(
                text = stringResource(
                    R.string.sync_conflict_times,
                    DateFormat.getDateTimeInstance().format(Date(conflict.localUpdatedAt)),
                    DateFormat.getDateTimeInstance().format(Date(conflict.remoteUpdatedAt))
                ),
                style = MaterialTheme.typography.bodySmall
            )
            Text(
                text = stringResource(R.string.sync_conflict_warning),
                style = MaterialTheme.typography.labelSmall,
                color = MaterialTheme.colorScheme.error
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = onKeepLocal) { Text(stringResource(R.string.sync_keep_local)) }
                OutlinedButton(onClick = onKeepRemote) { Text(stringResource(R.string.sync_keep_remote)) }
            }
        }
    }
}

@Composable
private fun PassphraseSetupDialog(
    title: String = stringResource(R.string.sync_set_passphrase),
    onSave: (String) -> Unit,
    onDismiss: () -> Unit
) {
    var passphrase by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(stringResource(R.string.sync_passphrase_hint))
                OutlinedTextField(
                    value = passphrase,
                    onValueChange = { passphrase = it },
                    label = { Text(stringResource(R.string.transfer_backup_passphrase)) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            TextButton(
                onClick = { if (passphrase.isNotBlank()) onSave(passphrase) },
                enabled = passphrase.isNotBlank()
            ) {
                Text(stringResource(R.string.media_add_save))
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text(stringResource(R.string.cancel))
            }
        }
    )
}
