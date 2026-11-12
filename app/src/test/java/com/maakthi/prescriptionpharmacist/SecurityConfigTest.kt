package com.maakthi.prescriptionpharmacist

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Permissions & security configuration smoke tests: the manifest asks for
 * exactly what the app needs, cleartext traffic is blocked, and the Firebase
 * rules stay owner-only with no public grants.
 */
class SecurityConfigTest {

    private fun read(path: String): String =
        File(path).takeIf { it.exists() }?.readText()
            ?: error("missing file: $path (cwd=${File(".").absolutePath})")

    private fun manifestText(): String = read("src/main/AndroidManifest.xml")

    // ---------- permissions ----------

    @Test
    fun `only the required permissions are declared`() {
        val manifest = manifestText()
        assertTrue(manifest.contains("android.permission.INTERNET"))
        assertTrue(manifest.contains("android.permission.CAMERA"))

        val forbidden = listOf(
            "android.permission.READ_SMS",
            "android.permission.SEND_SMS",
            "android.permission.READ_CALL_LOG",
            "android.permission.WRITE_CALL_LOG",
            "android.permission.READ_CONTACTS",
            "android.permission.ACCESS_FINE_LOCATION",
            "android.permission.RECORD_AUDIO",
            "android.permission.MANAGE_EXTERNAL_STORAGE",
            "android.permission.SYSTEM_ALERT_WINDOW",
            "android.permission.REQUEST_INSTALL_PACKAGES"
        )
        for (permission in forbidden) {
            assertFalse("health apps must not ask for $permission", manifest.contains(permission))
        }
    }

    @Test
    fun `the file provider is not exported and only shares granted uris`() {
        val manifest = manifestText()
        assertTrue(manifest.contains("androidx.core.content.FileProvider"))
        // within the provider block: exported=false, grantUriPermissions=true
        val providerBlock = manifest.substringAfter("<provider").substringBefore("</provider>")
        assertTrue(providerBlock.contains("android:exported=\"false\""))
        assertTrue(providerBlock.contains("android:grantUriPermissions=\"true\""))
    }

    // ---------- HTTPS enforcement ----------

    @Test
    fun `cleartext traffic is blocked at the network level`() {
        val config = read("src/main/res/xml/network_security_config.xml")
        assertTrue(config.contains("cleartextTrafficPermitted=\"false\""))
    }

    @Test
    fun `the manifest uses the strict network security config`() {
        assertTrue(manifestText().contains("android:networkSecurityConfig"))
    }

    // ---------- Firebase rules ----------

    @Test
    fun `firestore rules grant nothing publicly`() {
        val rules = read("../firestore.rules")
        assertTrue(rules.contains("request.auth.uid == uid"))
        assertFalse("no blanket allow", Regex("allow\\s+read,\\s*write:\\s*if\\s*true").containsMatchIn(rules))
        // default-deny catch-all exists
        assertTrue(rules.contains("allow read, write: if false"))
    }

    @Test
    fun `storage rules enforce ownership and upload limits`() {
        val rules = read("../storage.rules")
        assertTrue(rules.contains("request.auth.uid == userId"))
        assertTrue(rules.contains("10 * 1024 * 1024"))
        assertTrue(rules.contains("25 * 1024 * 1024"))
        assertTrue(rules.contains(".ppbak"))
        assertFalse("no public read", Regex("allow\\s+read:\\s*if\\s*true").containsMatchIn(rules))
    }

    // ---------- manifest sanity (XML well-formed) ----------

    @Test
    fun `the manifest is well-formed xml`() {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder()
            .parse(File("src/main/AndroidManifest.xml"))
        assertTrue(doc.documentElement.nodeName == "manifest")
    }

    private fun Element.attr(name: String): String? = getAttribute(name).takeIf { it.isNotEmpty() }
}
