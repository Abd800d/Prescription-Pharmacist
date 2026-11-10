package com.maakthi.prescriptionpharmacist.presentation.settings

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Email
import androidx.compose.material.icons.filled.Phone
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Card
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.ListItem
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import com.maakthi.prescriptionpharmacist.BuildConfig
import com.maakthi.prescriptionpharmacist.R
import com.maakthi.prescriptionpharmacist.presentation.components.SectionHeader

private const val SUPPORT_EMAIL = "support@prescription-pharmacist.app"

/**
 * Technical support: real contact actions (email / phone / WhatsApp) plus a
 * small FAQ. Actions open system apps through intents.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SupportScreen(onBack: () -> Unit) {
    val context = LocalContext.current

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
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            SectionHeader(title = stringResource(R.string.support_contact))

            Card(modifier = Modifier.fillMaxWidth()) {
                Column {
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.support_email)) },
                        supportingContent = { Text(SUPPORT_EMAIL) },
                        leadingContent = { Icon(Icons.Filled.Email, contentDescription = null) },
                        modifier = Modifier.padding(4.dp)
                    )
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_SENDTO).apply {
                                data = Uri.parse("mailto:$SUPPORT_EMAIL")
                                putExtra(
                                    Intent.EXTRA_SUBJECT,
                                    context.getString(R.string.support_report_subject)
                                )
                                putExtra(
                                    Intent.EXTRA_TEXT,
                                    "App version: ${BuildConfig.VERSION_NAME}\n"
                                )
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Send,
                            contentDescription = stringResource(R.string.support_report),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    ListItem(
                        headlineContent = { Text(stringResource(R.string.support_phone)) },
                        supportingContent = { Text("+967 000 000 000") },
                        leadingContent = { Icon(Icons.Filled.Phone, contentDescription = null) },
                        modifier = Modifier.padding(4.dp)
                    )
                    IconButton(
                        onClick = {
                            val intent = Intent(Intent.ACTION_DIAL).apply {
                                data = Uri.parse("tel:+967000000000")
                            }
                            runCatching { context.startActivity(intent) }
                        },
                        modifier = Modifier.padding(start = 8.dp)
                    ) {
                        Icon(
                            Icons.Filled.Phone,
                            contentDescription = stringResource(R.string.support_phone),
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                }
            }

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
