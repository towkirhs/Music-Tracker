package com.musictracker.online

import com.musictracker.data.SongText
import okhttp3.HttpUrl.Companion.toHttpUrl

/** Apple's free iTunes Search API (no key, no account): cover, genre and proper name of a song. */
object Itunes {

    data class Match(val title: String, val artist: String, val coverUrl: String, val genre: String)

    /** The best match for this song, or null when nothing fits. Throws IOException when offline. */
    fun find(title: String, artist: String): Match? {
        val url = "https://itunes.apple.com/search".toHttpUrl().newBuilder()
            .addQueryParameter("term", listOf(title, artist).filter { it.isNotBlank() }.joinToString(" "))
            .addQueryParameter("media", "music")
            .addQueryParameter("entity", "song")
            .addQueryParameter("limit", "10")
            // India's store has the most Hindi and Bangla songs.
            .addQueryParameter("country", "IN")
            .build()
        return getJson(url).optJSONArray("results")?.objects().orEmpty()
            .map {
                Match(
                    title = it.optString("trackName"),
                    artist = it.optString("artistName"),
                    coverUrl = it.optString("artworkUrl100").replace("100x100bb", "600x600bb"),
                    genre = it.optString("primaryGenreName"),
                )
            }
            .firstOrNull { sameSong(title, artist, it.title, it.artist) }
    }

    /** Same title (ignoring "(From …)" and the like), and the artist matches when we know it. */
    internal fun sameSong(title: String, artist: String, otherTitle: String, otherArtist: String): Boolean {
        val a = SongText.clean(title)
        val b = SongText.clean(otherTitle)
        if (a.isEmpty() || b.isEmpty()) return false
        val titleOk = a == b || (a.length >= 4 && b.startsWith(a)) || (b.length >= 4 && a.startsWith(b))
        if (!titleOk) return false
        val ourArtist = SongText.clean(artist)
        if (ourArtist.isEmpty()) return true
        val theirs = SongText.clean(otherArtist)
        return ourArtist.split(" ").any { it.length >= 3 && it in theirs } || theirs.split(" ").any { it.length >= 3 && it in ourArtist }
    }
}
