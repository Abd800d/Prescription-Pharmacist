package com.maakthi.prescriptionpharmacist

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.w3c.dom.Element
import java.io.File
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Arabic & English resources: every string key exists in both locales with
 * matching format placeholders, and every key referenced in code exists.
 */
class BilingualResourcesTest {

    private fun strings(path: String): Map<String, String> {
        val doc = DocumentBuilderFactory.newInstance().newDocumentBuilder().parse(File(path))
        val out = mutableMapOf<String, String>()
        val nodes = doc.getElementsByTagName("string")
        for (i in 0 until nodes.length) {
            val element = nodes.item(i) as Element
            out[element.getAttribute("name")] = element.textContent
        }
        return out
    }

    private fun placeholders(value: String): Set<String> =
        Regex("%\\d+\\\$[a-z]").findAll(value).map { it.value }.toSet()

    @Test
    fun `english and arabic have exactly the same keys`() {
        val en = strings("src/main/res/values/strings.xml")
        val ar = strings("src/main/res/values-ar/strings.xml")
        assertEquals(
            "locales must stay at parity",
            emptySet<String>(),
            (en.keys - ar.keys) + (ar.keys - en.keys)
        )
    }

    @Test
    fun `format placeholders match per key across locales`() {
        val en = strings("src/main/res/values/strings.xml")
        val ar = strings("src/main/res/values-ar/strings.xml")
        for (key in en.keys) {
            assertEquals(
                "placeholders differ for $key",
                placeholders(en.getValue(key)),
                placeholders(ar.getValue(key))
            )
        }
    }

    @Test
    fun `every string key referenced in code exists in resources`() {
        val en = strings("src/main/res/values/strings.xml")
        val sources = File("src/main/java").walkTopDown().filter { it.extension == "kt" }
        val used = mutableSetOf<String>()
        sources.forEach { file ->
            Regex("R\\.string\\.(\\w+)").findAll(file.readText()).forEach { used += it.groupValues[1] }
        }
        assertTrue("expected to find R.string usages", used.isNotEmpty())
        assertEquals(emptySet<String>(), used - en.keys)
    }

    @Test
    fun `no string value is empty in either locale`() {
        for (path in listOf("src/main/res/values/strings.xml", "src/main/res/values-ar/strings.xml")) {
            strings(path).forEach { (key, value) ->
                assertTrue("empty value for $key in $path", value.isNotBlank())
            }
        }
    }
}
