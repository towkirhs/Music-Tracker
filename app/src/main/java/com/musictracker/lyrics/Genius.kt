package com.musictracker.lyrics

import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.net.URLEncoder

/**
 * Free Genius API search (needs a free account's Client Access Token).
 * Only short pieces of the transcribed lyrics are sent; nothing else.
 */
object Genius {

    data class Hit(val title: String, val artist: String)

    /** Top search result for a piece of text, or null. Throws on a bad token. */
    fun search(token: String, query: String): Hit? {
        val url = URL("https://api.genius.com/search?q=" + URLEncoder.encode(query, "UTF-8"))
        val conn = (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 8_000
            readTimeout = 8_000
            setRequestProperty("Authorization", "Bearer $token")
            setRequestProperty("User-Agent", "MusicTracker/1.0")
        }
        try {
            val code = conn.responseCode
            if (code == 401 || code == 403) throw IllegalStateException("Genius token was refused")
            if (code != 200) return null
            val body = conn.inputStream.use { it.readBytes().toString(Charsets.UTF_8) }
            val hits = JSONObject(body).getJSONObject("response").getJSONArray("hits")
            for (i in 0 until hits.length()) {
                val hit = hits.getJSONObject(i)
                if (hit.optString("type") != "song") continue
                val result = hit.getJSONObject("result")
                val title = result.optString("title").trim()
                val artist = result.optJSONObject("primary_artist")?.optString("name").orEmpty().trim()
                if (title.isNotEmpty()) return Hit(title, artist)
            }
            return null
        } finally {
            conn.disconnect()
        }
    }

    data class Guess(val hit: Hit, val confident: Boolean)

    /**
     * Searches with two different stretches of the lyrics. It is confident when both agree,
     * or when the song's title is actually sung in the lyrics.
     */
    fun identify(token: String, lyrics: String): Guess? {
        val words = lyrics.split(Regex("""\s+""")).filter { it.isNotBlank() }
        if (words.size < 4) return null
        val queries = mutableListOf(words.take(10).joinToString(" "))
        if (words.size >= 12) queries += words.takeLast(10).joinToString(" ")
        if (Translit.hasIndicScript(lyrics)) {
            queries += Translit.words(words.take(10).joinToString(" ")).joinToString(" ")
        }
        val hits = queries.distinct().mapNotNull { runCatching { search(token, it) }.getOrNull() }
        if (hits.isEmpty()) return null
        val counts = hits.groupingBy { com.musictracker.data.SongText.key(it.title, it.artist) }.eachCount()
        val top = hits.maxBy { counts.getValue(com.musictracker.data.SongText.key(it.title, it.artist)) }
        val agreed = counts.getValue(com.musictracker.data.SongText.key(top.title, top.artist)) >= 2
        val lyricKeys = Translit.words(lyrics).map { Translit.skeleton(it) }
        val titleSung = TitleMatcher.score(TitleMatcher.titleKeys(top.title), lyricKeys) >= 0.99 &&
            TitleMatcher.titleKeys(top.title).sumOf { it.length } >= 4
        return Guess(top, agreed || titleSung)
    }

    /** Checks a token with one tiny search. */
    fun test(token: String): Boolean = runCatching { search(token, "tum hi ho") != null }.getOrDefault(false)
}
