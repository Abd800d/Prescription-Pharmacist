package com.maakthi.prescriptionpharmacist.presentation.settings

import android.content.Context
import android.net.Uri
import androidx.core.content.FileProvider
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.maakthi.prescriptionpharmacist.BuildConfig
import com.maakthi.prescriptionpharmacist.core.support.SupportReportBuilder
import com.maakthi.prescriptionpharmacist.domain.model.SupportReport
import dagger.hilt.android.lifecycle.HiltViewModel
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.receiveAsFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import javax.inject.Inject

sealed interface SupportEvent {
    /** Everything is ready — the screen opens the email app with this. */
    data class SendReport(
        val email: String,
        val subject: String,
        val body: String,
        val attachments: List<Uri>
    ) : SupportEvent

    data object MessageRequired : SupportEvent
    data class Failed(val reason: String? = null) : SupportEvent
}

/**
 * Support form state + report preparation. Personal data (contact email) is
 * attached ONLY when the user explicitly consents.
 */
@HiltViewModel
class SupportViewModel @Inject constructor(
    @ApplicationContext private val context: Context
) : ViewModel() {

    /** Stable report number shown in the form and sent with the report. */
    val reportId: String =
        SupportReportBuilder.reportId(System.currentTimeMillis(), System.nanoTime())

    private val _message = MutableStateFlow("")
    val message: StateFlow<String> = _message.asStateFlow()

    private val _contactEmail = MutableStateFlow("")
    val contactEmail: StateFlow<String> = _contactEmail.asStateFlow()

    private val _contactConsent = MutableStateFlow(false)
    val contactConsent: StateFlow<Boolean> = _contactConsent.asStateFlow()

    private val _includeLog = MutableStateFlow(false)
    val includeLog: StateFlow<Boolean> = _includeLog.asStateFlow()

    private val _includeScreenshot = MutableStateFlow(false)
    val includeScreenshot: StateFlow<Boolean> = _includeScreenshot.asStateFlow()

    private val _busy = MutableStateFlow(false)
    val busy: StateFlow<Boolean> = _busy.asStateFlow()

    private val _events = Channel<SupportEvent>(Channel.BUFFERED)
    val events = _events.receiveAsFlow()

    private var screenshotSource: Uri? = null

    fun onMessageChange(value: String) { _message.value = value }
    fun onContactChange(value: String) { _contactEmail.value = value }
    fun onConsentChange(value: Boolean) { _contactConsent.value = value }
    fun onIncludeLogChange(value: Boolean) { _includeLog.value = value }

    fun onIncludeScreenshotChange(value: Boolean) {
        _includeScreenshot.value = value
        if (!value) screenshotSource = null
    }

    fun onScreenshotPicked(uri: Uri?) {
        screenshotSource = uri
        _includeScreenshot.value = uri != null
    }

    fun submit() {
        val message = _message.value
        if (!SupportReportBuilder.isMessageValid(message)) {
            viewModelScope.launch { _events.send(SupportEvent.MessageRequired) }
            return
        }
        viewModelScope.launch {
            _busy.value = true
            try {
                val report = SupportReportBuilder.build(
                    reportId = reportId,
                    appVersion = BuildConfig.VERSION_NAME,
                    appVersionCode = BuildConfig.VERSION_CODE,
                    message = message,
                    contactEmail = _contactEmail.value,
                    contactConsent = _contactConsent.value,
                    includeLog = _includeLog.value,
                    includeScreenshot = _includeScreenshot.value && screenshotSource != null,
                    createdAt = System.currentTimeMillis()
                )

                val attachments = mutableListOf<Uri>()
                if (report.includeLog) {
                    captureErrorLog()?.let { attachments += fileUri(it) }
                }
                if (report.includeScreenshot) {
                    copyScreenshot()?.let { attachments += fileUri(it) }
                }

                _events.send(
                    SupportEvent.SendReport(
                        email = SupportReportBuilder.SUPPORT_EMAIL,
                        subject = SupportReportBuilder.emailSubject(report),
                        body = SupportReportBuilder.emailBody(report),
                        attachments = attachments
                    )
                )
            } catch (e: Exception) {
                _events.send(SupportEvent.Failed(e.message))
            } finally {
                _busy.value = false
            }
        }
    }

    // ---------- attachment preparation ----------

    private suspend fun captureErrorLog(): File? = withContext(Dispatchers.IO) {
        runCatching {
            val process = Runtime.getRuntime().exec(arrayOf("logcat", "-d", "-t", "300"))
            val text = process.inputStream.bufferedReader().use { it.readText() }
            process.waitFor()
            val dir = File(context.cacheDir, "support").apply { mkdirs() }
            File(dir, "error_log.txt").apply { writeText(text.takeLast(80_000)) }
        }.getOrNull()
    }

    private suspend fun copyScreenshot(): File? = withContext(Dispatchers.IO) {
        runCatching<File?> {
            val source = screenshotSource ?: return@runCatching null
            val input = context.contentResolver.openInputStream(source) ?: return@runCatching null
            val dir = File(context.cacheDir, "support").apply { mkdirs() }
            File(dir, "screenshot.png").apply {
                outputStream().use { output -> input.copyTo(output) }
            }
        }.getOrNull()
    }

    private fun fileUri(file: File): Uri =
        FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", file)
}
