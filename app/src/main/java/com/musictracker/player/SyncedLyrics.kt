package com.musictracker.player

import com.musictracker.net.Http
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONArray
import org.json.JSONObject
import java.util.concurrent.ConcurrentHashMap

/** One lyric line; [timeMs] is -1 for lyrics without timing. */
data class LyricLine(val timeMs: Long, val text: String)

data class Lyrics(val lines: List<LyricLine>, val synced: Boolean)

/** Time-synced lyrics from LRCLIB (free, open, no key), with plain lyrics as a fallback. */
object SyncedLyrics {

    private val cache = ConcurrentHashMap<String, Lyrics>()
    private val NONE = Lyrics(emptyList(), false)

    /** Blocking: call off the main thread. Empty lines when nothing is found. */
    fun find(title: String, artist: String, durationMs: Long): Lyrics {
        val key = "$title|$artist"
        cache[key]?.let { return it }
        val cleanTitle = title.replace(Regex("""\s*[(\[].*?[)\]]"""), "").trim().ifBlank { title }
        val found = runCatching {
            exact(cleanTitle, artist, durationMs) ?: search("$cleanTitle $artist") ?: search(cleanTitle)
        }.getOrNull() ?: NONE
        if (found.lines.isNotEmpty()) cache[key] = found
        return found
    }

    private fun exact(title: String, artist: String, durationMs: Long): Lyrics? {
        if (artist.isBlank()) return null
        val url = "https://lrclib.net/api/get".toHttpUrl().newBuilder()
            .addQueryParameter("track_name", title)
            .addQueryParameter("artist_name", artist)
            .apply { if (durationMs > 0) addQueryParameter("duration", (durationMs / 1000).toString()) }
            .build()
        val body = get(url.toString()) ?: return null
        return parse(JSONObject(body))
    }

    private fun search(query: String): Lyrics? {
        val url = "https://lrclib.net/api/search".toHttpUrl().newBuilder().addQueryParameter("q", query).build()
        val body = get(url.toString()) ?: return null
        val results = JSONArray(body)
        val items = (0 until results.length()).mapNotNull { results.optJSONObject(it) }
        val best = items.firstOrNull { !it.optString("syncedLyrics").isNullOrBlank() && it.optString("syncedLyrics") != "null" }
            ?: items.firstOrNull()
            ?: return null
        return parse(best)
    }

    private fun get(url: String): String? {
        val request = Request.Builder().url(url).header("User-Agent", "MusicTracker (personal Android app)").build()
        Http.client.newCall(request).execute().use { r ->
            return if (r.isSuccessful) r.body?.string() else null
        }
    }

    private fun parse(json: JSONObject): Lyrics? {
        val synced = json.optString("syncedLyrics").takeIf { it.isNotBlank() && it != "null" }
        if (synced != null) {
            val lines = parseLrc(synced)
            if (lines.isNotEmpty()) return Lyrics(lines, true)
        }
        val plain = json.optString("plainLyrics").takeIf { it.isNotBlank() && it != "null" } ?: return null
        return Lyrics(plain.lines().map { LyricLine(-1, it.trim()) }, false)
    }

    private val TIME = Regex("""\[(\d{1,2}):(\d{2})(?:[.:](\d{1,3}))?]""")

    /** Reads "[mm:ss.xx] text" lines; a line with several times appears at each of them. */
    fun parseLrc(lrc: String): List<LyricLine> {
        val out = ArrayList<LyricLine>()
        for (raw in lrc.lines()) {
            val times = TIME.findAll(raw).toList()
            if (times.isEmpty()) continue
            val text = raw.substring(times.last().range.last + 1).trim()
            for (t in times) {
                val (m, s, frac) = t.destructured
                val ms = when (frac.length) {
                    0 -> 0
                    1 -> frac.toInt() * 100
                    2 -> frac.toInt() * 10
                    else -> frac.take(3).toInt()
                }
                out += LyricLine(m.toLong() * 60_000 + s.toLong() * 1000 + ms, text)
            }
        }
        return out.sortedBy { it.timeMs }
    }
}
