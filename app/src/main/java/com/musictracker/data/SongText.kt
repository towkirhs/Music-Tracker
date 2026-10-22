package com.musictracker.data

import java.util.Locale

/** Small helpers for comparing song names written in different ways. */
object SongText {

    private val brackets = Regex("""[(\[{].*?[)\]}]""")
    private val noise = Regex(
        """\b(official|music|lyric|lyrics|video|audio|full|song|hd|4k|remastered|remaster)\b""",
        RegexOption.IGNORE_CASE,
    )
    private val notLetter = Regex("""[^\p{L}\p{M}\p{N}]+""")

    /** Key that treats "Tumi Robe Nirobe (Official Video)" and "tumi robe nirobe" as the same song. */
    fun key(title: String, artist: String): String = clean(title) + "|" + clean(artist)

    private fun clean(s: String): String {
        val noBrackets = brackets.replace(s, " ")
        val base = noBrackets.ifBlank { s }
        return notLetter.replace(noise.replace(base.lowercase(Locale.ROOT), " "), " ").trim()
    }

    /** Bangla script → Bangla, Devanagari → Hindi, anything else → English/other. */
    fun language(vararg parts: String): String {
        var bangla = 0
        var hindi = 0
        for (part in parts) for (c in part) {
            when (c.code) {
                in 0x0980..0x09FF -> bangla++
                in 0x0900..0x097F -> hindi++
            }
        }
        return when {
            bangla == 0 && hindi == 0 -> "English/other"
            bangla >= hindi -> "Bangla"
            else -> "Hindi"
        }
    }

    val LANGUAGES = listOf("Bangla", "Hindi", "English/other")
}
