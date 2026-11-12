package com.maakthi.prescriptionpharmacist.core.transfer

import org.w3c.dom.Element
import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.util.zip.ZipEntry
import java.util.zip.ZipInputStream
import java.util.zip.ZipOutputStream
import javax.xml.parsers.DocumentBuilderFactory

/**
 * Minimal XLSX reader/writer built on the JDK/Android standard library
 * (ZIP + SpreadsheetML) — no heavy external dependency.
 *
 * Writer: one sheet, inline-string and numeric cells.
 * Reader: shared strings, inline strings, numbers and booleans, sparse rows.
 * Corrupted archives raise [TransferFormatException].
 */
object XlsxCodec {

    private const val MAIN_NS = "http://schemas.openxmlformats.org/spreadsheetml/2006/main"

    // ---------------- writer ----------------

    fun write(table: TransferTable): ByteArray {
        val entries = LinkedHashMap<String, ByteArray>()
        entries["[Content_Types].xml"] = CONTENT_TYPES.toByteArray()
        entries["_rels/.rels"] = ROOT_RELS.toByteArray()
        entries["xl/workbook.xml"] = WORKBOOK.toByteArray()
        entries["xl/_rels/workbook.xml.rels"] = WORKBOOK_RELS.toByteArray()
        entries["xl/worksheets/sheet1.xml"] = sheetXml(table)

        val out = ByteArrayOutputStream()
        ZipOutputStream(out).use { zip ->
            entries.forEach { (name, bytes) ->
                zip.putNextEntry(ZipEntry(name))
                zip.write(bytes)
                zip.closeEntry()
            }
        }
        return out.toByteArray()
    }

    private fun sheetXml(table: TransferTable): ByteArray {
        val xml = StringBuilder()
        xml.append("""<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""").append('\n')
        xml.append("""<worksheet xmlns="$MAIN_NS"><sheetData>""")
        val allRows = listOf(table.headers) + table.rows
        allRows.forEachIndexed { rowIndex, row ->
            xml.append("<row r=\"").append(rowIndex + 1).append("\">")
            row.forEachIndexed { colIndex, value ->
                val ref = cellRef(colIndex, rowIndex)
                val number = value.trim()
                if (number.isNotEmpty() && NUMBER.matches(number)) {
                    xml.append("""<c r="$ref"><v>$number</v></c>""")
                } else {
                    xml.append("""<c r="$ref" t="inlineStr"><is><t>${escapeXml(value)}</t></is></c>""")
                }
            }
            xml.append("</row>")
        }
        xml.append("</sheetData></worksheet>")
        return xml.toString().toByteArray(Charsets.UTF_8)
    }

    // ---------------- reader ----------------

    fun read(bytes: ByteArray): TransferTable {
        val entries = readZip(bytes)
        val sheetBytes = entries["xl/worksheets/sheet1.xml"]
            ?: entries.keys.firstOrNull { it.startsWith("xl/worksheets/") && it.endsWith(".xml") }
                ?.let { entries[it] }
            ?: throw TransferFormatException("XLSX: no worksheet found")
        val sharedStrings = entries["xl/sharedStrings.xml"]?.let { parseSharedStrings(it) }.orEmpty()

        val doc = parseXml(sheetBytes)
        val rows = ArrayList<List<String>>()
        val rowNodes = doc.getElementsByTagNameNS("*", "row")
        for (r in 0 until rowNodes.length) {
            val rowElement = rowNodes.item(r) as Element
            val cells = ArrayList<String>()
            var lastIndex = -1
            val cellNodes = rowElement.getElementsByTagNameNS("*", "c")
            for (c in 0 until cellNodes.length) {
                val cell = cellNodes.item(c) as Element
                val index = cell.getAttribute("r").takeIf { it.isNotEmpty() }?.let { columnIndex(it) }
                    ?: (lastIndex + 1)
                while (cells.size < index) cells.add("")
                cells.add(cellValue(cell, sharedStrings))
                lastIndex = index
            }
            rows.add(cells)
        }
        if (rows.isEmpty()) return TransferTable(emptyList(), emptyList())
        val headers = rows.first()
        val width = headers.size
        val body = rows.drop(1).map { row ->
            if (row.size == width) row
            else if (row.size < width) row + List(width - row.size) { "" }
            else row.take(width)
        }
        return TransferTable(headers, body)
    }

    private fun readZip(bytes: ByteArray): Map<String, ByteArray> {
        val entries = LinkedHashMap<String, ByteArray>()
        try {
            ZipInputStream(ByteArrayInputStream(bytes)).use { zip ->
                var entry = zip.nextEntry
                while (entry != null) {
                    if (!entry.isDirectory) {
                        entries[entry.name] = zip.readBytes()
                    }
                    entry = zip.nextEntry
                }
            }
        } catch (e: Exception) {
            throw TransferFormatException("XLSX: corrupted archive (${e.message})")
        }
        if (entries.isEmpty()) throw TransferFormatException("XLSX: empty archive")
        return entries
    }

    private fun parseSharedStrings(bytes: ByteArray): List<String> {
        val doc = parseXml(bytes)
        val siNodes = doc.getElementsByTagNameNS("*", "si")
        val strings = ArrayList<String>()
        for (i in 0 until siNodes.length) {
            val si = siNodes.item(i) as Element
            val tNodes = si.getElementsByTagNameNS("*", "t")
            val sb = StringBuilder()
            for (t in 0 until tNodes.length) {
                sb.append(tNodes.item(t).textContent)
            }
            strings.add(sb.toString())
        }
        return strings
    }

    private fun cellValue(cell: Element, sharedStrings: List<String>): String {
        return when (cell.getAttribute("t")) {
            "s" -> {
                val v = firstText(cell, "v").toIntOrNull()
                if (v != null && v in sharedStrings.indices) sharedStrings[v] else ""
            }
            "inlineStr" -> {
                val tNodes = cell.getElementsByTagNameNS("*", "t")
                val sb = StringBuilder()
                for (t in 0 until tNodes.length) sb.append(tNodes.item(t).textContent)
                sb.toString()
            }
            "b" -> if (firstText(cell, "v") == "1") "true" else "false"
            else -> firstText(cell, "v")
        }
    }

    private fun firstText(parent: Element, tag: String): String {
        val nodes = parent.getElementsByTagNameNS("*", tag)
        return if (nodes.length > 0) nodes.item(0).textContent.orEmpty() else ""
    }

    private fun parseXml(bytes: ByteArray) = try {
        val factory = DocumentBuilderFactory.newInstance()
        factory.isNamespaceAware = true
        factory.setFeature("http://apache.org/xml/features/disallow-doctype-decl", true)
        factory.newDocumentBuilder().parse(ByteArrayInputStream(bytes))
    } catch (e: Exception) {
        throw TransferFormatException("XLSX: invalid XML (${e.message})")
    }

    internal fun cellRef(col: Int, row: Int): String {
        var c = col
        val sb = StringBuilder()
        while (true) {
            sb.insert(0, ('A' + (c % 26)))
            c = c / 26 - 1
            if (c < 0) break
        }
        return sb.append(row + 1).toString()
    }

    internal fun columnIndex(ref: String): Int {
        var index = 0
        for (ch in ref) {
            when {
                ch in 'A'..'Z' -> index = index * 26 + (ch - 'A' + 1)
                ch in 'a'..'z' -> index = index * 26 + (ch - 'a' + 1)
                else -> break
            }
        }
        return index - 1
    }

    private fun escapeXml(value: String): String = buildString(value.length) {
        for (ch in value) {
            when (ch) {
                '&' -> append("&amp;")
                '<' -> append("&lt;")
                '>' -> append("&gt;")
                '"' -> append("&quot;")
                '\u0000' -> Unit
                else -> append(ch)
            }
        }
    }

    private val NUMBER = Regex("^-?\\d+(\\.\\d+)?$")

    private val CONTENT_TYPES =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Types xmlns="http://schemas.openxmlformats.org/package/2006/content-types">""" +
            """<Default Extension="rels" ContentType="application/vnd.openxmlformats-package.relationships+xml"/>""" +
            """<Default Extension="xml" ContentType="application/xml"/>""" +
            """<Override PartName="/xl/workbook.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.sheet.main+xml"/>""" +
            """<Override PartName="/xl/worksheets/sheet1.xml" ContentType="application/vnd.openxmlformats-officedocument.spreadsheetml.worksheet+xml"/>""" +
            "</Types>"

    private val ROOT_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/officeDocument" Target="xl/workbook.xml"/>""" +
            "</Relationships>"

    private val WORKBOOK =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<workbook xmlns="$MAIN_NS" xmlns:r="http://schemas.openxmlformats.org/officeDocument/2006/relationships">""" +
            """<sheets><sheet name="Sheet1" sheetId="1" r:id="rId1"/></sheets></workbook>"""

    private val WORKBOOK_RELS =
        """<?xml version="1.0" encoding="UTF-8" standalone="yes"?>""" +
            """<Relationships xmlns="http://schemas.openxmlformats.org/package/2006/relationships">""" +
            """<Relationship Id="rId1" Type="http://schemas.openxmlformats.org/officeDocument/2006/relationships/worksheet" Target="worksheets/sheet1.xml"/>""" +
            "</Relationships>"
}
