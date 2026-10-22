package com.musictracker.lyrics

/**
 * Finds a known song title inside transcribed lyrics. Many Bangla and Hindi songs are named
 * after their hook line, so a cover singing the chorus usually contains the title.
 */
object TitleMatcher {

    data class Match(val title: String, val artist: String, val score: Double)

    private val brackets = Regex("""[(\[{].*?[)\]}]""")
    private val extras = Regex("""\s(feat\.?|ft\.?|from|-)\s.*$""", RegexOption.IGNORE_CASE)

    /** Sound skeletons of the title words that carry meaning. */
    fun titleKeys(title: String): List<String> {
        val base = extras.replace(brackets.replace(title, " "), "")
        return Translit.words(base).map { Translit.skeleton(it) }.filter { it.isNotEmpty() }
    }

    private fun close(a: String, b: String): Boolean {
        if (a == b) return true
        if (a.length < 4 || b.length < 4) return false
        return editDistance(a, b) <= 1
    }

    private fun editDistance(a: String, b: String): Int {
        val prev = IntArray(b.length + 1) { it }
        val cur = IntArray(b.length + 1)
        for (i in 1..a.length) {
            cur[0] = i
            for (j in 1..b.length) {
                val cost = if (a[i - 1] == b[j - 1]) 0 else 1
                cur[j] = minOf(prev[j] + 1, cur[j - 1] + 1, prev[j - 1] + cost)
            }
            cur.copyInto(prev)
        }
        return prev[b.length]
    }

    /**
     * Best fraction of the title's words found in order in the lyrics, allowing one extra
     * word between them (singers repeat and stretch words).
     */
    fun score(titleKeys: List<String>, lyricKeys: List<String>): Double {
        if (titleKeys.isEmpty() || lyricKeys.isEmpty()) return 0.0
        var best = 0
        for (start in lyricKeys.indices) {
            if (!close(titleKeys[0], lyricKeys[start])) continue
            var matched = 1
            var pos = start
            for (k in 1 until titleKeys.size) {
                val limit = minOf(lyricKeys.size - 1, pos + 2)
                var found = -1
                for (p in pos + 1..limit) if (close(titleKeys[k], lyricKeys[p])) { found = p; break }
                if (found < 0) break
                matched++
                pos = found
            }
            if (matched > best) best = matched
        }
        return best.toDouble() / titleKeys.size
    }

    /** A title only counts if it is specific enough to not appear by chance. */
    private fun specific(keys: List<String>): Boolean {
        val letters = keys.sumOf { it.length }
        return (keys.size >= 2 && letters >= 5) || (keys.size == 1 && letters >= 5)
    }

    fun best(lyrics: String, songs: List<Pair<String, String>>): Match? {
        val lyricKeys = Translit.words(lyrics).map { Translit.skeleton(it) }
        var best: Match? = null
        var bestLen = 0
        for ((title, artist) in songs) {
            val keys = titleKeys(title)
            if (!specific(keys)) continue
            val s = score(keys, lyricKeys)
            val needed = if (keys.size >= 4) 0.75 else 1.0
            if (s < needed) continue
            val len = keys.sumOf { it.length }
            if (best == null || s > best.score || (s == best.score && len > bestLen)) {
                best = Match(title, artist, s)
                bestLen = len
            }
        }
        return best
    }
}
