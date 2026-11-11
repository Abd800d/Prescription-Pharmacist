package com.maakthi.prescriptionpharmacist.core.transfer

/** Thrown when a transfer file cannot be parsed (corrupted/unsupported). */
class TransferFormatException(message: String) : Exception(message)

/** Simple in-memory table shared by all formats. */
data class TransferTable(
    val headers: List<String>,
    val rows: List<List<String>>
)

/**
 * RFC-4180 style CSV reader/writer.
 *
 * Handles quoted cells, embedded delimiters/newlines, doubled quotes,
 * BOM, CRLF/LF and automatic delimiter detection (`,` `;` `\t`).
 * Corrupted input (unbalanced quote) raises [TransferFormatException].
 */
object CsvCodec {

    fun parse(text: String): TransferTable {
        val source = text.removePrefix("\uFEFF")
        if (source.isBlank()) return TransferTable(emptyList(), emptyList())
        val delimiter = detectDelimiter(source)

        val rows = ArrayList<List<String>>()
        var cells = ArrayList<String>()
        val cell = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < source.length) {
            val ch = source[i]
            if (inQuotes) {
                when {
                    ch == '"' && i + 1 < source.length && source[i + 1] == '"' -> {
                        cell.append('"'); i++
                    }
                    ch == '"' -> inQuotes = false
                    else -> cell.append(ch)
                }
            } else {
                when {
                    ch == '"' -> inQuotes = true
                    ch == delimiter -> {
                        cells.add(cell.toString()); cell.setLength(0)
                    }
                    ch == '\n' || ch == '\r' -> {
                        if (ch == '\r' && i + 1 < source.length && source[i + 1] == '\n') i++
                        cells.add(cell.toString()); cell.setLength(0)
                        rows.add(cells); cells = ArrayList()
                    }
                    else -> cell.append(ch)
                }
            }
            i++
        }
        if (inQuotes) throw TransferFormatException("CSV: unbalanced quote")
        // Flush the last cell/row (ignore a single trailing empty line).
        cells.add(cell.toString())
        if (cells.size > 1 || cells[0].isNotEmpty()) rows.add(cells)

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

    fun write(table: TransferTable, delimiter: Char = ','): String {
        val out = StringBuilder()
        out.appendLine(table.headers.joinToString(delimiter.toString()) { escape(it, delimiter) })
        table.rows.forEach { row ->
            out.appendLine(row.joinToString(delimiter.toString()) { escape(it, delimiter) })
        }
        return out.toString()
    }

    fun writeBytes(table: TransferTable, delimiter: Char = ','): ByteArray =
        write(table, delimiter).toByteArray(Charsets.UTF_8)

    private fun escape(value: String, delimiter: Char): String {
        val needs = value.contains(delimiter) || value.contains('"') ||
            value.contains('\n') || value.contains('\r')
        return if (needs) "\"" + value.replace("\"", "\"\"") + "\"" else value
    }

    private fun detectDelimiter(source: String): Char {
        val firstLine = source.lineSequence().firstOrNull().orEmpty()
        return listOf(',', ';', '\t')
            .maxByOrNull { ch -> firstLine.count { it == ch } }
            ?.takeIf { ch -> firstLine.count { it == ch } > 0 }
            ?: ','
    }
}
