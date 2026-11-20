package com.musictracker.online

import org.json.JSONArray
import java.time.Instant
import java.time.LocalDateTime
import java.time.ZoneOffset
import java.time.format.DateTimeFormatter

/** Reads listening history files downloaded from Spotify and Google Takeout (YouTube Music). */
object HistoryFiles {

    data class Entry(val title: String, val artist: String, val startedAt: Long, val playedMs: Long, val source: String)

    /** Imported YouTube Music plays don't say how long they lasted; each counts as this long. */
    const val UNKNOWN_LENGTH_MS = 180_000L

    private val spotifyMinute = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm")

    /**
     * Entries from one JSON file, or null if it isn't a history file this app knows:
     * Spotify "Account data" (StreamingHistory_music_*.json), Spotify "Extended streaming history"
     * (Streaming_History_Audio_*.json), or Takeout's YouTube watch-history.json (YouTube Music part only).
     * Plays under 10 seconds and podcasts are left out, like in the app itself.
     */
    fun parse(text: String): List<Entry>? {
        val array = runCatching { JSONArray(text.trimStart('﻿')) }.getOrNull() ?: return null
        val first = array.optJSONObject(0) ?: return emptyList()
        return when {
            first.has("endTime") && first.has("msPlayed") -> array.objects().mapNotNull { o ->
                val title = o.optString("trackName").trim()
                val ms = o.optLong("msPlayed")
                if (title.isEmpty() || ms < 10_000) return@mapNotNull null
                val end = LocalDateTime.parse(o.getString("endTime"), spotifyMinute).toInstant(ZoneOffset.UTC).toEpochMilli()
                Entry(title, o.optString("artistName").trim(), end - ms, ms, "Spotify")
            }
            first.has("ts") && first.has("ms_played") -> array.objects().mapNotNull { o ->
                val title = o.optString("master_metadata_track_name").takeUnless { o.isNull("master_metadata_track_name") }?.trim()
                val ms = o.optLong("ms_played")
                if (title.isNullOrEmpty() || ms < 10_000) return@mapNotNull null
                val end = Instant.parse(o.getString("ts")).toEpochMilli()
                val artist = o.optString("master_metadata_album_artist_name").takeUnless { o.isNull("master_metadata_album_artist_name") }
                Entry(title, artist.orEmpty().trim(), end - ms, ms, "Spotify")
            }
            first.has("header") && first.has("time") -> array.objects().mapNotNull { o ->
                if (o.optString("header") != "YouTube Music" || !o.has("titleUrl")) return@mapNotNull null
                val title = o.optString("title").removePrefix("Watched ").trim()
                if (title.isEmpty()) return@mapNotNull null
                val artist = o.optJSONArray("subtitles")?.optJSONObject(0)?.optString("name").orEmpty()
                    .removeSuffix(" - Topic").trim()
                Entry(title, artist, Instant.parse(o.getString("time")).toEpochMilli(), UNKNOWN_LENGTH_MS, "YouTube Music")
            }
            else -> null
        }
    }

    /** True for the names of history files inside a Spotify or Takeout zip. */
    fun isHistoryFileName(name: String): Boolean {
        val n = name.substringAfterLast('/')
        return n.endsWith(".json") &&
            (n.startsWith("StreamingHistory_music") || n.startsWith("StreamingHistory") || n.startsWith("Streaming_History_Audio") || n == "watch-history.json")
    }
}
