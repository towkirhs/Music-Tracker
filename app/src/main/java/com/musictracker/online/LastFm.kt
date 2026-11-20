package com.musictracker.online

import okhttp3.HttpUrl.Companion.toHttpUrl
import org.json.JSONObject
import java.io.IOException

/**
 * Last.fm's free API (needs a free API key): similar songs and artists for radio and discovery,
 * your Last.fm top songs, and tags that tell genre and language. Blocking calls: use a background thread.
 */
object LastFm {

    data class Track(val title: String, val artist: String)

    class BadKey : IOException("Last.fm didn't accept the API key")

    private fun call(key: String, method: String, vararg params: Pair<String, String>): JSONObject? {
        val url = "https://ws.audioscrobbler.com/2.0/".toHttpUrl().newBuilder()
            .addQueryParameter("method", method)
            .addQueryParameter("api_key", key)
            .addQueryParameter("format", "json")
            .addQueryParameter("autocorrect", "1")
            .apply { params.forEach { (k, v) -> addQueryParameter(k, v) } }
            .build()
        val json = getJson(url)
        return when (json.optInt("error", 0)) {
            0 -> json
            10, 26 -> throw BadKey() // invalid or suspended key
            else -> null // e.g. 6: song or artist not found
        }
    }

    private fun tracks(json: JSONObject?, outer: String): List<Track> =
        json?.optJSONObject(outer)?.objects("track").orEmpty().mapNotNull { t ->
            val title = t.optString("name").trim()
            val artist = t.optJSONObject("artist")?.optString("name")?.trim().orEmpty()
            if (title.isEmpty()) null else Track(title, artist)
        }

    private fun names(json: JSONObject?, outer: String, inner: String): List<String> =
        json?.optJSONObject(outer)?.objects(inner).orEmpty().map { it.optString("name").trim() }.filter { it.isNotEmpty() }

    fun similarTracks(key: String, title: String, artist: String, limit: Int = 30): List<Track> =
        tracks(call(key, "track.getsimilar", "track" to title, "artist" to artist, "limit" to "$limit"), "similartracks")

    fun similarArtists(key: String, artist: String, limit: Int = 10): List<String> =
        names(call(key, "artist.getsimilar", "artist" to artist, "limit" to "$limit"), "similarartists", "artist")

    fun artistTopTracks(key: String, artist: String, limit: Int = 10): List<Track> =
        tracks(call(key, "artist.gettoptracks", "artist" to artist, "limit" to "$limit"), "toptracks")

    fun tagTopTracks(key: String, tag: String, limit: Int = 30): List<Track> =
        tracks(call(key, "tag.gettoptracks", "tag" to tag, "limit" to "$limit"), "tracks")

    fun userTopTracks(key: String, user: String, limit: Int = 20): List<Track> =
        tracks(call(key, "user.gettoptracks", "user" to user, "period" to "1month", "limit" to "$limit"), "toptracks")

    fun userTopArtists(key: String, user: String, limit: Int = 10): List<String> =
        names(call(key, "user.gettopartists", "user" to user, "period" to "3month", "limit" to "$limit"), "topartists", "artist")

    /** The song's top tags (genres, languages, moods), or the artist's when the song has none. */
    fun tags(key: String, title: String, artist: String): List<String> {
        val song = names(call(key, "track.gettoptags", "track" to title, "artist" to artist), "toptags", "tag")
        if (song.isNotEmpty() || artist.isBlank()) return song.take(8)
        return names(call(key, "artist.gettoptags", "artist" to artist), "toptags", "tag").take(8)
    }

    /** True when the key works. */
    fun test(key: String): Boolean = runCatching { call(key, "tag.gettoptracks", "tag" to "bangla", "limit" to "1") != null }
        .getOrDefault(false)

    /** True when the Last.fm user exists. */
    fun userExists(key: String, user: String): Boolean =
        runCatching { call(key, "user.getinfo", "user" to user) != null }.getOrDefault(false)
}
