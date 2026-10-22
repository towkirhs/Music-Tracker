package com.musictracker.lyrics

import java.util.Locale

/**
 * Rough Bangla/Hindi script → Latin letters, plus a "sound skeleton" so that
 * "তুমি রবে নীরবে", "Tumi Robe Nirobe" and "tumi robey neerobe" compare as equal.
 */
object Translit {

    private val consonants: Map<Char, String> = mapOf(
        // Bangla
        'ক' to "k", 'খ' to "kh", 'গ' to "g", 'ঘ' to "gh", 'ঙ' to "ng",
        'চ' to "ch", 'ছ' to "chh", 'জ' to "j", 'ঝ' to "jh", 'ঞ' to "n",
        'ট' to "t", 'ঠ' to "th", 'ড' to "d", 'ঢ' to "dh", 'ণ' to "n",
        'ত' to "t", 'থ' to "th", 'দ' to "d", 'ধ' to "dh", 'ন' to "n",
        'প' to "p", 'ফ' to "ph", 'ব' to "b", 'ভ' to "bh", 'ম' to "m",
        'য' to "j", 'র' to "r", 'ল' to "l", 'শ' to "sh", 'ষ' to "sh",
        'স' to "s", 'হ' to "h", 'ড়' to "r", 'ঢ়' to "rh", 'য়' to "y",
        // Hindi (Devanagari)
        'क' to "k", 'ख' to "kh", 'ग' to "g", 'घ' to "gh", 'ङ' to "ng",
        'च' to "ch", 'छ' to "chh", 'ज' to "j", 'झ' to "jh", 'ञ' to "n",
        'ट' to "t", 'ठ' to "th", 'ड' to "d", 'ढ' to "dh", 'ण' to "n",
        'त' to "t", 'थ' to "th", 'द' to "d", 'ध' to "dh", 'न' to "n",
        'प' to "p", 'फ' to "ph", 'ब' to "b", 'भ' to "bh", 'म' to "m",
        'य' to "y", 'र' to "r", 'ल' to "l", 'व' to "v", 'श' to "sh",
        'ष' to "sh", 'स' to "s", 'ह' to "h",
        'क़' to "q", 'ख़' to "kh", 'ग़' to "g", 'ज़' to "z", 'ड़' to "r",
        'ढ़' to "rh", 'फ़' to "f",
    )

    private val vowels: Map<Char, String> = mapOf(
        // Bangla independent vowels and vowel signs
        'অ' to "o", 'আ' to "a", 'ই' to "i", 'ঈ' to "i", 'উ' to "u",
        'ঊ' to "u", 'ঋ' to "ri", 'এ' to "e", 'ঐ' to "oi", 'ও' to "o", 'ঔ' to "ou",
        'া' to "a", 'ি' to "i", 'ী' to "i", 'ু' to "u", 'ূ' to "u",
        'ৃ' to "ri", 'ে' to "e", 'ৈ' to "oi", 'ো' to "o", 'ৌ' to "ou",
        // Hindi independent vowels and vowel signs
        'अ' to "a", 'आ' to "aa", 'इ' to "i", 'ई' to "ee", 'उ' to "u",
        'ऊ' to "oo", 'ऋ' to "ri", 'ए' to "e", 'ऐ' to "ai", 'ओ' to "o", 'औ' to "au",
        'ा' to "a", 'ि' to "i", 'ी' to "i", 'ु' to "u", 'ू' to "u",
        'ृ' to "ri", 'े' to "e", 'ै' to "ai", 'ो' to "o", 'ौ' to "au",
    )

    private val nasal: Map<Char, String> = mapOf(
        'ং' to "ng", 'ঁ' to "n", 'ঃ' to "h", 'ৎ' to "t",
        'ं' to "n", 'ँ' to "n", 'ः' to "h",
    )

    private val viramas = setOf('্', '्')
    private val ignored = setOf('़', '়', '‌', '‍')

    fun hasIndicScript(s: String): Boolean = s.any { it.code in 0x0900..0x09FF }

    fun toLatin(s: String): String {
        val sb = StringBuilder()
        var i = 0
        while (i < s.length) {
            val c = s[i]
            val cons = consonants[c]
            when {
                cons != null -> {
                    sb.append(cons)
                    var j = i + 1
                    while (j < s.length && s[j] in ignored) j++
                    val next = if (j < s.length) s[j] else ' '
                    when {
                        next in viramas -> j++
                        vowels.containsKey(next) && next.code % 128 >= 0x3E -> { sb.append(vowels[next]); j++ }
                        else -> sb.append(if (c.code < 0x0980) "a" else "o")
                    }
                    i = j
                    continue
                }
                vowels.containsKey(c) -> sb.append(vowels[c])
                nasal.containsKey(c) -> sb.append(nasal[c])
                c in viramas || c in ignored -> Unit
                else -> sb.append(c)
            }
            i++
        }
        return sb.toString()
    }

    private val notLetters = Regex("""[^a-z0-9 ]+""")
    private val spaces = Regex("""\s+""")

    /** Lower-case Latin words, script converted, punctuation removed. */
    fun words(s: String): List<String> {
        val latin = if (hasIndicScript(s)) toLatin(s) else s
        return notLetters.replace(latin.lowercase(Locale.ROOT), " ").trim().split(spaces).filter { it.isNotEmpty() }
    }

    /** How a word sounds, roughly: spelling variants and vowels removed after the first letter. */
    fun skeleton(word: String): String {
        var w = word
        for ((from, to) in listOf(
            "chh" to "c", "ch" to "c", "sh" to "s", "kh" to "k", "gh" to "g", "th" to "t", "dh" to "d",
            "bh" to "b", "ph" to "f", "jh" to "j", "ee" to "i", "oo" to "u", "w" to "v", "z" to "j",
            "q" to "k", "x" to "ks", "y" to "",
        )) w = w.replace(from, to)
        if (w.isEmpty()) return word
        val sb = StringBuilder().append(w[0])
        for (k in 1 until w.length) {
            val ch = w[k]
            if (ch in "aeiou") continue
            if (sb.last() == ch) continue
            sb.append(ch)
        }
        return sb.toString()
    }
}
