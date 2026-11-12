package com.maakthi.prescriptionpharmacist.core.transfer

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Assert.fail
import org.junit.Test

/** CSV parsing/writing: valid files, edge cases and corrupted input. */
class CsvCodecTest {

    @Test
    fun `parses a simple csv`() {
        val table = CsvCodec.parse("name,strength\nPanadol,500 mg\nAugmentin,1 g\n")
        assertEquals(listOf("name", "strength"), table.headers)
        assertEquals(2, table.rows.size)
        assertEquals(listOf("Panadol", "500 mg"), table.rows[0])
    }

    @Test
    fun `handles quotes, embedded delimiters and newlines`() {
        val text = "name,notes\n\"Panadol, Extra\",\"line1\nline2 \"\"quoted\"\"\"\n"
        val table = CsvCodec.parse(text)
        assertEquals("Panadol, Extra", table.rows[0][0])
        assertEquals("line1\nline2 \"quoted\"", table.rows[0][1])
    }

    @Test
    fun `detects semicolon and tab delimiters`() {
        val semi = CsvCodec.parse("a;b;c\n1;2;3\n")
        assertEquals(3, semi.headers.size)
        val tab = CsvCodec.parse("a\tb\n1\t2\n")
        assertEquals(2, tab.headers.size)
    }

    @Test
    fun `handles bom and crlf line endings and arabic text`() {
        val text = "\uFEFFالاسم,التركيز\r\nبنادول,٥٠٠ مجم\r\n"
        val table = CsvCodec.parse(text)
        assertEquals("الاسم", table.headers[0])
        assertEquals("بنادول", table.rows[0][0])
    }

    @Test
    fun `write then parse roundtrip preserves data`() {
        val original = TransferTable(
            headers = listOf("name", "note", "num"),
            rows = listOf(
                listOf("Panadol", "comma, and \"quote\"", "500"),
                listOf("أوجمنتين", "سطر\nثاني", "1000")
            )
        )
        val parsed = CsvCodec.parse(CsvCodec.write(original))
        assertEquals(original, parsed)
    }

    @Test
    fun `short rows are padded and long rows are trimmed`() {
        val table = CsvCodec.parse("a,b,c\n1,2\n1,2,3,4\n")
        assertEquals(listOf("1", "2", ""), table.rows[0])
        assertEquals(listOf("1", "2", "3"), table.rows[1])
    }

    @Test
    fun `corrupted file with unbalanced quote fails cleanly`() {
        try {
            CsvCodec.parse("a,b\n\"unclosed,2\n")
            fail("expected TransferFormatException")
        } catch (e: TransferFormatException) {
            assertTrue(e.message!!.contains("unbalanced"))
        }
    }

    @Test
    fun `empty input produces an empty table`() {
        assertEquals(0, CsvCodec.parse("").rows.size)
        assertEquals(0, CsvCodec.parse("   ").rows.size)
    }

    @Test
    fun `large file parses quickly`() {
        val sb = StringBuilder("tradeNameEnglish,strength\n")
        repeat(5_000) { i -> sb.append("Drug $i,${i} mg\n") }
        val table = CsvCodec.parse(sb.toString())
        assertEquals(5_000, table.rows.size)
    }
}
