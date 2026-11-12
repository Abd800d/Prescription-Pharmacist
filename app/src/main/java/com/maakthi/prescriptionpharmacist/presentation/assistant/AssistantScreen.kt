package com.maakthi.prescriptionpharmacist.presentation.assistant

import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
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
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.domain.model.AssistantCitation
import com.maakthi.prescriptionpharmacist.domain.model.ChatRole
import com.maakthi.prescriptionpharmacist.domain.model.ErrorCode

/**
 * Bilingual (AR/EN) chat with the RAG drug assistant.
 *
 * Every answer shows source name + link + update date; answers without
 * reliable evidence show the explicit "not enough reliable information"
 * message; a permanent banner reminds the user the assistant does not
 * replace a doctor or pharmacist; the local history can be deleted at any time.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AssistantScreen(
    onBack: () -> Unit,
    viewModel: AssistantViewModel = hiltViewModel()
) {
    val messages by viewModel.messages.collectAsStateWithLifecycle()
    val input by viewModel.input.collectAsStateWithLifecycle()
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }
    val listState = rememberLazyListState()
    var confirmDelete by remember { mutableStateOf(false) }

    val errorGeneric = stringResource(R.string.assistant_error)
    val errorNotConfigured = stringResource(R.string.assistant_not_configured)
    val errorRateLimited = stringResource(R.string.assistant_rate_limited)
    val errorNetwork = stringResource(R.string.assistant_error_network)

    LaunchedEffect(uiState) {
        val state = uiState
        if (state is AssistantUiState.Error) {
            snackbarHostState.showSnackbar(
                when (state.code) {
                    ErrorCode.NOT_CONFIGURED -> errorNotConfigured
                    ErrorCode.RATE_LIMITED -> errorRateLimited
                    ErrorCode.NETWORK -> errorNetwork
                    else -> errorGeneric
                }
            )
            viewModel.dismissError()
        }
    }

    LaunchedEffect(messages.size) {
        if (messages.isNotEmpty()) {
            listState.animateScrollToItem(messages.size - 1)
        }
    }

    if (confirmDelete) {
        AlertDialog(
            onDismissRequest = { confirmDelete = false },
            title = { Text(stringResource(R.string.assistant_delete_title)) },
            text = { Text(stringResource(R.string.assistant_delete_message)) },
            confirmButton = {
                TextButton(onClick = {
                    confirmDelete = false
                    viewModel.deleteHistory()
                }) {
                    Text(stringResource(R.string.assistant_delete_confirm))
                }
            },
            dismissButton = {
                TextButton(onClick = { confirmDelete = false }) {
                    Text(stringResource(R.string.cancel))
                }
            }
        )
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.assistant_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = stringResource(R.string.back))
                    }
                },
                actions = {
                    IconButton(onClick = { confirmDelete = true }) {
                        Icon(Icons.Filled.Delete, contentDescription = stringResource(R.string.assistant_delete_title))
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHostState) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
        ) {
            // ---- permanent disclaimer banner ----
            Card(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp, vertical = 6.dp),
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)
            ) {
                Text(
                    text = stringResource(R.string.assistant_disclaimer_banner),
                    style = MaterialTheme.typography.bodySmall,
                    modifier = Modifier.padding(10.dp)
                )
            }

            LazyColumn(
                state = listState,
                modifier = Modifier
                    .weight(1f)
                    .fillMaxWidth()
                    .padding(horizontal = 12.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                if (messages.isEmpty()) {
                    item {
                        Text(
                            text = stringResource(R.string.assistant_welcome),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.outline,
                            modifier = Modifier.padding(16.dp)
                        )
                    }
                }
                items(messages.size) { index ->
                    ChatBubbleCard(messages[index])
                }
                if (uiState == AssistantUiState.Thinking) {
                    item {
                        Row(
                            modifier = Modifier.padding(8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            CircularProgressIndicator(modifier = Modifier.widthIn(max = 20.dp))
                            Text(
                                text = stringResource(R.string.assistant_thinking),
                                style = MaterialTheme.typography.bodySmall
                            )
                        }
                    }
                }
            }

            // ---- input ----
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(12.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = viewModel::onInputChange,
                    modifier = Modifier.weight(1f),
                    placeholder = { Text(stringResource(R.string.assistant_input_hint)) },
                    maxLines = 4
                )
                IconButton(
                    onClick = viewModel::send,
                    enabled = input.isNotBlank() && uiState != AssistantUiState.Thinking
                ) {
                    Icon(
                        Icons.AutoMirrored.Filled.Send,
                        contentDescription = stringResource(R.string.assistant_send)
                    )
                }
            }
        }
    }
}

@Composable
private fun ChatBubbleCard(bubble: ChatBubble) {
    val isUser = bubble.role == ChatRole.USER
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = if (isUser) Arrangement.End else Arrangement.Start
    ) {
        Card(
            modifier = Modifier.widthIn(max = 320.dp),
            shape = RoundedCornerShape(
                topStart = 14.dp,
                topEnd = 14.dp,
                bottomStart = if (isUser) 14.dp else 4.dp,
                bottomEnd = if (isUser) 4.dp else 14.dp
            ),
            colors = if (isUser) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer)
            } else if (bubble.insufficient) {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.errorContainer)
            } else {
                CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surfaceVariant)
            }
        ) {
            Column(modifier = Modifier.padding(10.dp)) {
                Text(
                    text = bubble.content,
                    style = MaterialTheme.typography.bodyMedium
                )
                if (!isUser && bubble.citations.isNotEmpty()) {
                    Text(
                        text = stringResource(R.string.assistant_sources_title),
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.Bold,
                        modifier = Modifier.padding(top = 6.dp)
                    )
                    bubble.citations.forEach { citation ->
                        CitationLine(citation)
                    }
                }
            }
        }
    }
}

@Composable
private fun CitationLine(citation: AssistantCitation) {
    val parts = buildList {
        add(citation.sourceName)
        if (citation.sourceDate.isNotBlank()) add(citation.sourceDate)
    }.joinToString(" • ")
    Text(
        text = parts,
        style = MaterialTheme.typography.labelSmall,
        modifier = Modifier.padding(top = 2.dp)
    )
    if (citation.sourceUrl.isNotBlank()) {
        Text(
            text = citation.sourceUrl,
            style = MaterialTheme.typography.labelSmall,
            color = MaterialTheme.colorScheme.primary,
            modifier = Modifier.padding(top = 1.dp)
        )
    }
}
