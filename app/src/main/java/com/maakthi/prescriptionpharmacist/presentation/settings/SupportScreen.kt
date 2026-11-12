package com.maakthi.prescriptionpharmacist.presentation.settings

import android.content.ClipData
import android.content.Intent
import android.net.Uri
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Image
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
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
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.maakthi.prescriptionpharmacist.BuildConfig
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.core.support.SupportReportBuilder
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader

/**
 * Full technical support page:
 *
 * - WhatsApp chat button and email button (real support contacts);
 * - problem-report form with unique report number and the app version;
 * - optional error-log and screenshot attachments;
 * - contact email is sent ONLY with explicit consent (no personal data
 *   leaves the device silently);
 * - a clear notice that support does NOT provide medical diagnosis.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(
    onBack: () -> Unit,
    viewModel: SupportViewModel = hiltViewModel()
) {
    val context = LocalContext.current
    val message by viewModel.message.collectAsStateWithLifecycle()
    val contactEmail by viewModel.contactEmail.collectAsStateWithLifecycle()
    val contactConsent by viewModel.contactConsent.collectAsStateWithLifecycle()
    val includeLog by viewModel.includeLog.collectAsStateWithLifecycle()
    val includeScreenshot by viewModel.includeScreenshot.collectAsStateWithLifecycle()
    val busy by viewModel.busy.collectAsStateWithLifecycle()
    val snackbarHostState = remember { SnackbarHostState() }

    val messageRequiredMsg = stringResource(R.string.support_error_message_required)
    val sendFailedMsg = stringResource(R.string.sync_error_generic)
    val sentMsg = stringResource(R.string.support_report_sent, viewModel.reportId)

    val screenshotPicker = rememberLauncherForActivityResult(
        ActivityResultContracts.PickVisualMedia()
    ) { uri -> viewModel.onScreenshotPicked(uri) }

    LaunchedEffect(Unit) {
        viewModel.events.collect { event ->
            when (event) {
                is SupportEvent.SendReport -> {
                    val intent = Intent(Intent.ACTION_SEND).apply {
                        type = "message/rfc822"
                        putExtra(Intent.EXTRA_EMAIL, arrayOf(event.email))
                        putExtra(Intent.EXTRA_SUBJECT, event.subject)
                        putExtra(Intent.EXTRA_TEXT, event.body)
                            if (event.attachments.isNotEmpty()) {
                                putParcelableArrayListExtra(
                                    Intent.EXTRA_STREAM,
                                    ArrayList<Uri>(event.attachments)
                                )
                            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
                            clipData = ClipData.newRawUri("support", event.attachments.first()).also { clip ->
                                event.attachments.drop(1).forEach { clip.addItem(ClipData.Item(it)) }
                            }
                        }
                    }
                    runCatching {
                        context.startActivity(Intent.createChooser(intent, event.subject))
                    }
                    snackbarHostState.showSnackbar(sentMsg)
                }
                SupportEvent.MessageRequired -> snackbarHostState.showSnackbar(messageRequiredMsg)
                is SupportEvent.Failed -> snackbarHostState.showSnackbar(sendFailedMsg)
            }
        }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text(stringResource(R.string.support_title)) },
                navigationIcon = {
                    IconButton(onClick = onBack) {
                        Icon(
                            Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = stringResource(R.string.back)
                        )
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
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            // ---- medical notice (product law) ----
            Card(
                modifier = Modifier.fillMaxWidth(),
                colors = CardDefaults.cardColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer
                )
            ) {
                Text(
                    text = stringResource(R.string.support_medical_warning),
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onErrorContainer,
                    modifier = Modifier.padding(12.dp)
                )
            }

            // ---- quick contact ----
            SectionHeader(title = stringResource(R.string.support_contact))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Button(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(SupportReportBuilder.whatsappLink()))
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text(stringResource(R.string.support_whatsapp))
                        }
                        OutlinedButton(
                            onClick = {
                                runCatching {
                                    context.startActivity(
                                        Intent(Intent.ACTION_VIEW, Uri.parse(SupportReportBuilder.mailtoLink("")))
                                    )
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Email, contentDescription = null)
                            Text(
                                text = stringResource(R.string.support_email),
                                modifier = Modifier.padding(start = 6.dp)
                            )
                        }
                    }
                    Text(
                        text = stringResource(
                            R.string.support_contact_hint,
                            SupportReportBuilder.WHATSAPP_NUMBER,
                            SupportReportBuilder.SUPPORT_EMAIL
                        ),
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.outline
                    )
                }
            }

            // ---- problem report form ----
            SectionHeader(title = stringResource(R.string.support_form_title))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column(
                    modifier = Modifier.padding(12.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.CheckCircle,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary
                        )
                        Text(
                            text = stringResource(R.string.support_report_id, viewModel.reportId),
                            style = MaterialTheme.typography.labelLarge,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }
                    Text(
                        text = stringResource(
                            R.string.support_app_version,
                            BuildConfig.VERSION_NAME,
                            BuildConfig.VERSION_CODE
                        ),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.outline
                    )

                    OutlinedTextField(
                        value = message,
                        onValueChange = viewModel::onMessageChange,
                        label = { Text(stringResource(R.string.support_message_label)) },
                        minLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = contactEmail,
                        onValueChange = viewModel::onContactChange,
                        label = { Text(stringResource(R.string.support_contact_label)) },
                        singleLine = true,
                        enabled = contactConsent,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Checkbox(
                            checked = contactConsent,
                            onCheckedChange = viewModel::onConsentChange
                        )
                        Text(
                            text = stringResource(R.string.support_consent_label),
                            style = MaterialTheme.typography.bodySmall
                        )
                    }
                    Text(
                        text = stringResource(R.string.support_consent_note),
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.outline
                    )

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.support_attach_log),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(checked = includeLog, onCheckedChange = viewModel::onIncludeLogChange)
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(
                            text = stringResource(R.string.support_attach_screenshot),
                            style = MaterialTheme.typography.bodyMedium,
                            modifier = Modifier.weight(1f)
                        )
                        Switch(
                            checked = includeScreenshot,
                            onCheckedChange = { enabled ->
                                if (enabled) {
                                    screenshotPicker.launch(
                                        PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                    )
                                } else {
                                    viewModel.onIncludeScreenshotChange(false)
                                }
                            }
                        )
                    }
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            Icons.Filled.Image,
                            contentDescription = null,
                            tint = if (includeScreenshot) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        )
                        Text(
                            text = stringResource(
                                if (includeScreenshot) R.string.support_screenshot_attached
                                else R.string.support_screenshot_none
                            ),
                            style = MaterialTheme.typography.labelSmall,
                            modifier = Modifier.padding(start = 6.dp)
                        )
                    }

                    Button(
                        onClick = viewModel::submit,
                        enabled = !busy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(stringResource(R.string.support_send))
                    }
                }
            }

            // ---- FAQ ----
            SectionHeader(title = stringResource(R.string.support_faq_title))

            FaqCard(
                question = stringResource(R.string.support_faq_q1),
                answer = stringResource(R.string.support_faq_a1)
            )
            FaqCard(
                question = stringResource(R.string.support_faq_q2),
                answer = stringResource(R.string.support_faq_a2)
            )
        }
    }
}

@Composable
private fun FaqCard(question: String, answer: String) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(14.dp)) {
            Text(text = question, style = MaterialTheme.typography.titleSmall)
            Text(
                text = answer,
                style = MaterialTheme.typography.bodyMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant,
                modifier = Modifier.padding(top = 4.dp)
            )
        }
    }
}
