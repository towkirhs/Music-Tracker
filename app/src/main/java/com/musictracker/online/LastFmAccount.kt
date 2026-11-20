package com.musictracker.online

import com.musictracker.net.Http
import okhttp3.FormBody
import okhttp3.HttpUrl.Companion.toHttpUrl
import okhttp3.Request
import org.json.JSONException
import org.json.JSONObject
import java.io.IOException
import java.security.MessageDigest

/**
 * Your Last.fm account: connecting (you approve the app on last.fm, so your password never
 * enters this app), scrobbling, and reading your scrobble history. Needs the API key and its
 * shared secret. Blocking calls: use a background thread.
 */
object LastFmAccount {

    private const val API = "https://ws.audioscrobbler.com/2.0/"

    class Rejected(val code: Int, message: String) : IOException(message)

    /** Last.fm's request signature: md5 of the sorted name+value pairs, then the secret. */
    fun sign(params: Map<String, String>, secret: String): String {
        val text = params.filterKeys { it != "format" && it != "callback" }
            .toSortedMap().entries.joinToString("") { it.key + it.value } + secret
        return MessageDigest.getInstance("MD5").digest(text.toByteArray(Charsets.UTF_8))
            .joinToString("") { "%02x".format(it) }
    }

    private fun post(params: Map<String, String>, secret: String): JSONObject {
        val body = FormBody.Builder().apply {
            params.forEach { (k, v) -> add(k, v) }
            add("api_sig", sign(params, secret))
            add("format", "json")
        }.build()
        val request = Request.Builder().url(API).post(body).header("User-Agent", "MusicTracker/1.0").build()
        Http.client.newCall(request).execute().use { response ->
            val text = response.body?.string().orEmpty()
            val json = try {
                JSONObject(text)
            } catch (e: JSONException) {
                throw IOException("Last.fm sent no answer (${response.code})", e)
            }
            val error = json.optInt("error", 0)
            if (error != 0) throw Rejected(error, json.optString("message"))
            return json
        }
    }

    /** Step 1: a token, and the last.fm page where you approve this app. */
    fun startConnect(key: String, secret: String): Pair<String, String> {
        val token = post(mapOf("method" to "auth.getToken", "api_key" to key), secret).getString("token")
        return token to "https://www.last.fm/api/auth/?api_key=$key&token=$token"
    }

    /** Step 2, after you approved: the session key and your username. Throws Rejected(14) if not approved yet. */
    fun finishConnect(key: String, secret: String, token: String): Pair<String, String> {
        val session = post(mapOf("method" to "auth.getSession", "api_key" to key, "token" to token), secret)
            .getJSONObject("session")
        return session.getString("key") to session.getString("name")
    }

    data class Scrobble(val title: String, val artist: String, val startedAtSec: Long)

    /** Sends up to 50 plays at once. Returns how many Last.fm accepted. */
    fun scrobble(key: String, secret: String, session: String, plays: List<Scrobble>): Int {
        require(plays.size in 1..50)
        val params = mutableMapOf("method" to "track.scrobble", "api_key" to key, "sk" to session)
        plays.forEachIndexed { i, p ->
            params["artist[$i]"] = p.artist
            params["track[$i]"] = p.title
            params["timestamp[$i]"] = p.startedAtSec.toString()
        }
        return post(params, secret).optJSONObject("scrobbles")?.optJSONObject("@attr")?.optInt("accepted") ?: 0
    }

    data class Page(val scrobbles: List<Scrobble>, val totalPages: Int)

    /** One page (200) of your scrobbles newer than [fromSec], newest first. */
    fun recentTracks(key: String, user: String, page: Int, fromSec: Long): Page {
        val url = API.toHttpUrl().newBuilder()
            .addQueryParameter("method", "user.getrecenttracks")
            .addQueryParameter("user", user)
            .addQueryParameter("api_key", key)
            .addQueryParameter("format", "json")
            .addQueryParameter("limit", "200")
            .addQueryParameter("page", "$page")
            .apply { if (fromSec > 0) addQueryParameter("from", "${fromSec + 1}") }
            .build()
        return parseRecentTracks(getJson(url))
    }

    internal fun parseRecentTracks(json: JSONObject): Page {
        if (json.optInt("error", 0) != 0) throw Rejected(json.optInt("error"), json.optString("message"))
        val recent = json.optJSONObject("recenttracks") ?: return Page(emptyList(), 0)
        val scrobbles = recent.objects("track").mapNotNull { t ->
            val uts = t.optJSONObject("date")?.optString("uts")?.toLongOrNull() ?: return@mapNotNull null // "now playing"
            val title = t.optString("name").trim()
            val artist = t.optJSONObject("artist")?.let { it.optString("#text").ifBlank { it.optString("name") } }.orEmpty().trim()
            if (title.isEmpty()) null else Scrobble(title, artist, uts)
        }
        val pages = recent.optJSONObject("@attr")?.optString("totalPages")?.toIntOrNull() ?: 1
        return Page(scrobbles, pages)
    }
}
