package com.musictracker.online

import com.musictracker.net.Http
import com.musictracker.player.Song
import okhttp3.MediaType.Companion.toMediaType
import okhttp3.Request
import okhttp3.RequestBody.Companion.toRequestBody
import org.json.JSONArray
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/**
 * Your YouTube Music library (liked songs and playlists) through the same unofficial web API the
 * YouTube Music website uses, signed in with the cookies from signing in inside the app.
 * Free, but Google can change it at any time. Blocking calls: use a background thread.
 */
object YtMusic {

    private const val ORIGIN = "https://music.youtube.com"

    class SignedOut : IOException("Signed out of YouTube Music. Sign in again in Settings.")

    data class Playlist(val browseId: String, val title: String, val subtitle: String, val thumbnail: String?)

    /** True when the cookies are from a signed-in Google account. */
    fun isSignedIn(cookie: String): Boolean = sapisid(cookie) != null

    private fun sapisid(cookie: String): String? = cookie.split(";").map { it.trim() }
        .firstOrNull { it.startsWith("SAPISID=") || it.startsWith("__Secure-3PAPISID=") }
        ?.substringAfter("=")?.ifBlank { null }

    /** The header Google's web apps send to prove the request comes from the signed-in page. */
    fun authorization(sapisid: String, nowSec: Long): String {
        val hash = MessageDigest.getInstance("SHA-1").digest("$nowSec $sapisid $ORIGIN".toByteArray())
            .joinToString("") { "%02x".format(it) }
        return "SAPISIDHASH ${nowSec}_$hash"
    }

    private fun browse(cookie: String, extra: JSONObject): JSONObject {
        val sapisid = sapisid(cookie) ?: throw SignedOut()
        val body = JSONObject(extra.toString()).put(
            "context",
            JSONObject().put(
                "client",
                JSONObject().put("clientName", "WEB_REMIX").put("clientVersion", "1.20241111.01.00").put("hl", "en"),
            ),
        )
        val request = Request.Builder()
            .url("$ORIGIN/youtubei/v1/browse?prettyPrint=false")
            .post(body.toString().toRequestBody("application/json".toMediaType()))
            .header("Cookie", cookie)
            .header("Authorization", authorization(sapisid, System.currentTimeMillis() / 1000))
            .header("Origin", ORIGIN)
            .header("X-Origin", ORIGIN)
            .header("X-Goog-AuthUser", "0")
            .header("User-Agent", Http.USER_AGENT)
            .build()
        Http.client.newCall(request).execute().use { response ->
            if (response.code == 401 || response.code == 403) throw SignedOut()
            val text = response.body?.string().orEmpty()
            if (!response.isSuccessful) throw IOException("YouTube Music answered ${response.code}")
            return try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw IOException("YouTube Music sent an unexpected answer", e)
            }
        }
    }

    /** Your liked songs (up to [max]). */
    fun likedSongs(cookie: String, max: Int = 500): List<Song> = playlistSongs(cookie, "VLLM", max)

    /** Songs of one playlist, following "load more" pages up to [max]. */
    fun playlistSongs(cookie: String, browseId: String, max: Int = 500): List<Song> {
        val songs = mutableListOf<Song>()
        var page = browse(cookie, JSONObject().put("browseId", browseId))
        while (true) {
            songs += parseSongs(page)
            val token = continuationToken(page) ?: break
            if (songs.size >= max) break
            page = browse(cookie, JSONObject().put("continuation", token))
        }
        return songs.distinctBy { it.videoId }.take(max)
    }

    /** Playlists in your library. */
    fun playlists(cookie: String): List<Playlist> = parsePlaylists(browse(cookie, JSONObject().put("browseId", "FEmusic_liked_playlists")))

    // Parsing. The answers are deep and change shape often, so these search for the pieces they need.

    internal fun parseSongs(json: JSONObject): List<Song> =
        find(json, "musicResponsiveListItemRenderer").mapNotNull { item ->
            val columns = item.optJSONArray("flexColumns") ?: return@mapNotNull null
            fun column(i: Int) = columns.optJSONObject(i)?.optJSONObject("musicResponsiveListItemFlexColumnRenderer")
                ?.optJSONObject("text")?.optJSONArray("runs")
            val titleRuns = column(0) ?: return@mapNotNull null
            val title = titleRuns.optJSONObject(0)?.optString("text").orEmpty()
            val videoId = item.optJSONObject("playlistItemData")?.optString("videoId")?.ifBlank { null }
                ?: find(titleRuns, "watchEndpoint").firstOrNull()?.optString("videoId")?.ifBlank { null }
                ?: return@mapNotNull null
            val artist = column(1)?.let { runs -> (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() } }
                .orEmpty().substringBefore(" • ").trim()
            val durationText = item.optJSONArray("fixedColumns")?.optJSONObject(0)
                ?.optJSONObject("musicResponsiveListItemFixedColumnRenderer")?.optJSONObject("text")
                ?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            if (title.isBlank()) null else Song(videoId, title, artist, lastThumbnail(item.optJSONObject("thumbnail")), clockToMs(durationText))
        }

    internal fun parsePlaylists(json: JSONObject): List<Playlist> =
        find(json, "musicTwoRowItemRenderer").mapNotNull { item ->
            val browseId = find(item.optJSONObject("navigationEndpoint") ?: return@mapNotNull null, "browseEndpoint")
                .firstOrNull()?.optString("browseId").orEmpty()
            if (!browseId.startsWith("VL")) return@mapNotNull null // e.g. the "New playlist" button
            val title = item.optJSONObject("title")?.optJSONArray("runs")?.optJSONObject(0)?.optString("text").orEmpty()
            val subtitleRuns = item.optJSONObject("subtitle")?.optJSONArray("runs")
            val subtitle = subtitleRuns?.let { runs -> (0 until runs.length()).joinToString("") { runs.optJSONObject(it)?.optString("text").orEmpty() } }.orEmpty()
            Playlist(browseId, title, subtitle, lastThumbnail(item.optJSONObject("thumbnailRenderer")))
        }.distinctBy { it.browseId }

    internal fun continuationToken(json: JSONObject): String? =
        find(json, "continuationCommand").firstOrNull()?.optString("token")?.ifBlank { null }
            ?: find(json, "nextContinuationData").firstOrNull()?.optString("continuation")?.ifBlank { null }

    /** The largest thumbnail (the last one listed). */
    private fun lastThumbnail(holder: JSONObject?): String? =
        find(holder ?: return null, "thumbnails").firstOrNull()?.optString("url")?.ifBlank { null }

    private fun clockToMs(text: String): Long {
        val parts = text.split(":").mapNotNull { it.toLongOrNull() }
        if (parts.isEmpty() || parts.size != text.split(":").size) return 0
        return parts.fold(0L) { acc, p -> acc * 60 + p } * 1000
    }

    /** Every object stored under [key] anywhere inside [root] (arrays under that key give their last element). */
    private fun find(root: Any, key: String): List<JSONObject> {
        val out = mutableListOf<JSONObject>()
        fun walk(node: Any?) {
            when (node) {
                is JSONObject -> {
                    val keys = node.keys()
                    while (keys.hasNext()) {
                        val k = keys.next()
                        val v = node.opt(k)
                        if (k == key) {
                            when (v) {
                                is JSONObject -> out += v
                                is JSONArray -> v.optJSONObject(v.length() - 1)?.let(out::add)
                            }
                        }
                        walk(v)
                    }
                }
                is JSONArray -> for (i in 0 until node.length()) walk(node.opt(i))
            }
        }
        walk(root)
        return out
    }
}
