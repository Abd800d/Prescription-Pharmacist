package com.maakthi.prescriptionpharmacist.core.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** XLSX reading/writing: roundtrip, Arabic text, numbers and corruption. */
class XlsxCodecTest {

    @Test
    fun `write then read roundtrip preserves headers and rows`() {
        val original = TransferTable(
            headers = listOf("tradeNameArabic", "tradeNameEnglish", "strength"),
            rows = listOf(
                listOf("بنادول", "Panadol", "500"),
                listOf("أوجمنتين", "Augmentin, forte", "1000")
            )
        )
        val parsed = XlsxCodec.read(XlsxCodec.write(original))
        assertEquals(original.headers, parsed.headers)
        assertEquals(original.rows, parsed.rows)
    }

    @Test
    fun `numeric cells come back as numbers`() {
        val table = XlsxCodec.read(
            XlsxCodec.write(TransferTable(listOf("n"), listOf(listOf("42"), listOf("3.5"), listOf("text"))))
        )
        assertEquals(listOf("42"), table.rows[0])
        assertEquals(listOf("3.5"), table.rows[1])
        assertEquals(listOf("text"), table.rows[2])
    }

    @Test
    fun `xml special characters survive the roundtrip`() {
        val tricky = "a < b & c > d \"quoted\""
        val table = XlsxCodec.read(XlsxCodec.write(TransferTable(listOf("x"), listOf(listOf(tricky)))))
        assertEquals(tricky, table.rows[0][0])
    }

    @Test
    fun `cell references convert both ways`() {
        assertEquals("A1", XlsxCodec.cellRef(0, 0))
        assertEquals("Z1", XlsxCodec.cellRef(25, 0))
        assertEquals("AA1", XlsxCodec.cellRef(26, 0))
        assertEquals(0, XlsxCodec.columnIndex("A1"))
        assertEquals(25, XlsxCodec.columnIndex("Z9"))
        assertEquals(26, XlsxCodec.columnIndex("AA1"))
    }

    @Test
    fun `corrupted archive fails cleanly`() {
        try {
            XlsxCodec.read("this is not a zip".toByteArray())
            fail("expected TransferFormatException")
        } catch (e: TransferFormatException) {
            assertTrue(e.message!!.contains("XLSX"))
        }
    }

    @Test
    fun `empty workbook parses to an empty table`() {
        val emptyZip = XlsxCodec.write(TransferTable(emptyList(), emptyList()))
        val parsed = XlsxCodec.read(emptyZip)
        assertTrue(parsed.headers.isEmpty())
    }

    @Test
    fun `large workbook roundtrip works`() {
        val rows = (1..1_000).map { listOf("دواء $it", "Drug $it", it.toString()) }
        val parsed = XlsxCodec.read(XlsxCodec.write(TransferTable(listOf("ar", "en", "n"), rows)))
        assertEquals(1_000, parsed.rows.size)
        assertEquals(listOf("دواء 1000", "Drug 1000", "1000"), parsed.rows[999])
    }
}
