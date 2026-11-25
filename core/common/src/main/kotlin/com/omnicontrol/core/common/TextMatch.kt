package com.omnicontrol.core.common

import kotlin.math.max
import kotlin.math.min

/** Small, dependency-free fuzzy text helpers used for app names, on-screen labels and speech fixes. */
object TextMatch {

    private val NON_ALNUM = Regex("[^a-z0-9 ]")
    private val SPACES = Regex("\\s+")

    fun normalize(s: String): String =
        s.lowercase().replace('’', '\'').replace(NON_ALNUM, " ").replace(SPACES, " ").trim()

    fun compact(s: String): String = normalize(s).replace(" ", "")

    fun tokens(s: String): List<String> = normalize(s).split(' ').filter { it.isNotBlank() }

    /** 0..1 similarity based on Levenshtein distance of the compact forms. */
    fun similarity(a: String, b: String): Double {
        val x = compact(a)
        val y = compact(b)
        if (x.isEmpty() || y.isEmpty()) return 0.0
        if (x == y) return 1.0
        val d = levenshtein(x, y)
        return 1.0 - d.toDouble() / max(x.length, y.length)
    }

    /**
     * How well [query] describes [label] (0..1). Handles: exact, prefix, "query is inside label",
     * all query words present, and typo-level similarity.
     */
    fun labelScore(query: String, label: String): Double {
        val q = normalize(query)
        val l = normalize(label)
        if (q.isEmpty() || l.isEmpty()) return 0.0
        if (q == l) return 1.0
        val qc = q.replace(" ", "")
        val lc = l.replace(" ", "")
        if (qc == lc) return 0.98
        var score = 0.0
        if (l.startsWith(q)) score = max(score, 0.9)
        if (Regex("\\b${Regex.escape(q)}\\b").containsMatchIn(l)) score = max(score, 0.85 - min(0.25, (l.length - q.length) / 200.0))
        else if (lc.contains(qc) && qc.length >= 4) score = max(score, 0.7)
        val qt = q.split(' ').filter { it.length > 1 }
        if (qt.isNotEmpty()) {
            val lt = l.split(' ').toSet()
            val hits = qt.count { t -> lt.any { it == t || (t.length >= 4 && it.startsWith(t)) || similarity(it, t) >= 0.85 } }
            val ratio = hits.toDouble() / qt.size
            if (ratio == 1.0) score = max(score, 0.8)
            else score = max(score, ratio * 0.65)
        }
        // whole-string typo similarity (only meaningful for short labels)
        if (l.length <= 30) score = max(score, similarity(q, l) * 0.9)
        return score
    }

    fun levenshtein(a: String, b: String): Int {
        if (a == b) return 0
        if (a.isEmpty()) return b.length
        if (b.isEmpty()) return a.length
        var prev = IntArray(b.length + 1) { it }
        var cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = min(min(cur[j - 1] + 1, prev[j] + 1), prev[j - 1] + cost)
            }
            val t = prev; prev = cur; cur = t
        }
        return prev[b.length]
    }

    private val ORDINALS = linkedMapOf(
        "first" to 1, "1st" to 1, "one" to 1, "top" to 1,
        "second" to 2, "2nd" to 2, "two" to 2,
        "third" to 3, "3rd" to 3, "three" to 3,
        "fourth" to 4, "4th" to 4, "four" to 4,
        "fifth" to 5, "5th" to 5, "five" to 5,
        "sixth" to 6, "6th" to 6, "six" to 6,
        "seventh" to 7, "7th" to 7, "seven" to 7,
        "eighth" to 8, "8th" to 8, "eight" to 8,
        "ninth" to 9, "9th" to 9, "nine" to 9,
        "tenth" to 10, "10th" to 10, "ten" to 10,
        "last" to -1
    )

    /** "the second video" -> 2, "last one" -> -1, "number 3" -> 3, else null. */
    fun ordinal(text: String): Int? {
        val t = tokens(text)
        Regex("(?:number|no|#)\\s*(\\d{1,2})").find(normalize(text))?.let { return it.groupValues[1].toInt() }
        for (w in t) ORDINALS[w]?.let { return it }
        return null
    }
}
