package com.maakthi.prescriptionpharmacist.core.analysis

/**
 * Arabic/English text normalization used by every matching and grounding
 * step: diacritics removal, letter unification, Arabic-Indic digits, and
 * string similarity.
 */
object TextNormalizer {

    private val diacritics = Regex("[\\u064B-\\u0652\\u0670\\u0640]")
    private val punctuation = Regex("[\\p{Punct}\\u060C\\u061B}\\u061F\\u2010-\\u2015]+")
    private val multiSpace = Regex("\\s+")

    /** Converts Arabic-Indic (٠-٩, ۰-۹) digits to ASCII. */
    fun normalizeDigits(text: String): String = buildString {
        for (ch in text) {
            when {
                ch in '\u0660'..'\u0669' -> append('0' + (ch - '\u0660'))
                ch in '\u06F0'..'\u06F9' -> append('0' + (ch - '\u06F0'))
                else -> append(ch)
            }
        }
    }

    /**
     * Lowercased, diacritics-free, unified-letter form used for comparison.
     * Arabic: أإآٱ→ا, ة→ه, ى→ي, ؤ→و, ئ→ي.
     */
    fun normalize(text: String): String {
        var s = normalizeDigits(text).lowercase()
        s = diacritics.replace(s, "")
        s = s.map { ch ->
            when (ch) {
                '\u0623', '\u0625', '\u0622', '\u0671' -> '\u0627' // ا
                '\u0629' -> '\u0647' // ة → ه
                '\u0649' -> '\u064A' // ى → ي
                '\u0624' -> '\u0648' // ؤ → و
                '\u0626' -> '\u064A' // ئ → ي
                else -> ch
            }
        }.joinToString("")
        s = punctuation.replace(s, " ")
        return multiSpace.replace(s, " ").trim()
    }

    /** Normalized comparison tokens of length ≥ 2 (single letters dropped). */
    fun tokens(text: String): List<String> =
        normalize(text).split(" ").filter { it.length >= 2 }

    /** Levenshtein edit distance. */
    fun levenshtein(a: String, b: String): Int {
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(cur[j - 1] + 1, prev[j] + 1, prev[j - 1] + cost)
            }
            val tmp = prev
            prev = cur
            cur = tmp
        }
        return prev[b.length]
    }

    /**
     * Similarity 0..1 combining character-level edit distance with a
     * token-set score (order-insensitive).
     */
    fun similarity(a: String, b: String): Float {
        val na = normalize(a)
        val nb = normalize(b)
        if (na.isEmpty() || nb.isEmpty()) return 0f
        if (na == nb) return 1f
        val charSim = 1f - levenshtein(na, nb).toFloat() / maxOf(na.length, nb.length)
        val ta = na.split(" ").toSet()
        val tb = nb.split(" ").toSet()
        val inter = ta.intersect(tb).size
        val tokenSim = inter.toFloat() / maxOf(ta.size, tb.size)
        val containmentBoost = if (na.contains(nb) || nb.contains(na)) 0.15f else 0f
        return (maxOf(charSim, tokenSim) + containmentBoost).coerceIn(0f, 1f)
    }
}
