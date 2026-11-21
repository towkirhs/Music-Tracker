package com.musictracker.web

import android.content.Context
import com.musictracker.BuildConfig
import com.musictracker.data.AppDatabase
import com.musictracker.data.PlayRow
import com.musictracker.data.Repo
import androidx.room.InvalidationTracker
import com.musictracker.online.Scrobbler
import com.musictracker.tracking.PlayTracker
import com.musictracker.online.YtMusic
import com.musictracker.player.Song
import com.musictracker.player.YouTube
import com.musictracker.tracking.Prefs
import fi.iki.elonen.NanoHTTPD
import kotlinx.coroutines.runBlocking
import org.json.JSONArray
import org.json.JSONObject

/**
 * The website on your home Wi-Fi: the page itself (from the app's assets/web) and a small API
 * for your library, edits, search and YouTube Music. Every API call needs your 6-digit code.
 */
class WebServer(private val context: Context, port: Int) : NanoHTTPD(port) {

    /** Goes up whenever songs or plays change, so the website knows when to fetch the library again. */
    @Volatile private var version = 0L
    private val changes = object : InvalidationTracker.Observer(arrayOf("plays", "tracks")) {
        override fun onInvalidated(tables: Set<String>) {
            version++
        }
    }

    init {
        AppDatabase.get(context).invalidationTracker.addObserver(changes)
    }

    override fun stop() {
        AppDatabase.get(context).invalidationTracker.removeObserver(changes)
        super.stop()
    }

    override fun serve(session: IHTTPSession): Response = try {
        if (session.uri.startsWith("/api/")) api(session) else asset(session.uri)
    } catch (e: Exception) {
        json(Response.Status.INTERNAL_ERROR, JSONObject().put("error", e.message ?: "Something went wrong"))
    }

    private fun asset(uri: String): Response {
        val path = uri.trimStart('/').ifEmpty { "index.html" }
        if (".." in path) return newFixedLengthResponse(Response.Status.FORBIDDEN, MIME_PLAINTEXT, "No")
        val stream = runCatching { context.assets.open("web/$path") }.getOrNull()
            ?: return newFixedLengthResponse(Response.Status.NOT_FOUND, MIME_PLAINTEXT, "Not found")
        val mime = when (path.substringAfterLast('.')) {
            "html" -> "text/html; charset=utf-8"
            "js" -> "text/javascript; charset=utf-8"
            "css" -> "text/css; charset=utf-8"
            "ttf" -> "font/ttf"
            "svg" -> "image/svg+xml"
            "png" -> "image/png"
            "json" -> "application/json"
            "webmanifest" -> "application/manifest+json"
            else -> "application/octet-stream"
        }
        return newChunkedResponse(Response.Status.OK, mime, stream)
    }

    private fun api(session: IHTTPSession): Response {
        val path = session.uri.removePrefix("/api")
        if (path == "/ping") return ok(JSONObject().put("app", "Music Tracker").put("version", BuildConfig.VERSION_NAME))
        if (session.headers["x-code"] != Prefs.webCode(context)) {
            return json(Response.Status.UNAUTHORIZED, JSONObject().put("error", "Wrong code"))
        }
        val q = session.parameters.mapValues { it.value.firstOrNull().orEmpty() }
        return when (session.method) {
            Method.GET -> when (path) {
                "/library" -> ok(library().put("version", version))
                "/version" -> ok(JSONObject().put("version", version))
                "/search" -> ok(JSONObject().put("songs", songs(YouTube.search(q["q"].orEmpty()))))
                "/video" -> ok(JSONObject().put("videoId", YouTube.videoFor(q["q"].orEmpty()).orEmpty()))
                "/ytm/playlists" -> ok(
                    JSONObject().put(
                        "playlists",
                        JSONArray(
                            YtMusic.playlists(Prefs.ytMusicCookie(context)).map {
                                JSONObject().put("browseId", it.browseId).put("title", it.title)
                                    .put("subtitle", it.subtitle).put("thumbnail", it.thumbnail.orEmpty())
                            },
                        ),
                    ),
                )
                "/ytm/playlist" -> {
                    val cookie = Prefs.ytMusicCookie(context)
                    val id = q["id"].orEmpty()
                    ok(JSONObject().put("songs", songs(if (id == "LIKED") YtMusic.likedSongs(cookie) else YtMusic.playlistSongs(cookie, id))))
                }
                else -> notFound()
            }
            Method.POST -> {
                val files = HashMap<String, String>()
                session.parseBody(files)
                val body = JSONObject(files["postData"] ?: "{}")
                runBlocking {
                    when (path) {
                        "/track/edit" -> Repo.editTrack(
                            context, body.getLong("id"), body.getString("title").trim(),
                            body.optString("artist").trim(), body.getString("language"),
                        )
                        "/track/favourite" -> Repo.setFavourite(context, body.getLong("id"), body.getBoolean("favourite"))
                        "/track/delete" -> Repo.deleteTrack(context, body.getLong("id"))
                        "/play/delete" -> Repo.deletePlay(context, body.getLong("playId"), body.getLong("trackId"))
                        "/play/record" -> return@runBlocking ok(JSONObject().put("playId", recordPlay(body)))
                        else -> return@runBlocking notFound()
                    }
                    ok(JSONObject().put("ok", true))
                }
            }
            else -> notFound()
        }
    }

    /**
     * A song played on the website: added to the library like the app's own player (same 10-second
     * rule and the same "count the built-in player" setting). The first call adds the play; later
     * calls with its playId update how long it played. Returns the playId, or 0 when not counted.
     */
    private suspend fun recordPlay(body: JSONObject): Long {
        val playedMs = body.getLong("playedMs")
        if (!Prefs.countPlayer(context) || playedMs < PlayTracker.MIN_PLAY_MS) return 0
        val playId = body.optLong("playId", 0)
        if (playId > 0) {
            Repo.updatePlayedMs(context, playId, playedMs)
        } else {
            return Repo.addPlay(
                context, body.getString("title").trim(), body.optString("artist").trim(), "played on the website",
                "Website", "website", body.getLong("startedAt"), playedMs,
            )
        }
        Scrobbler.kick(context)
        return playId
    }

    private fun library(): JSONObject {
        val rows = runBlocking { AppDatabase.get(context).music().allPlayRows() }
        return JSONObject()
            .put("plays", JSONArray(rows.map(::playJson)))
            .put("lastFmKey", Prefs.lastFmKey(context))
            .put("lastFmUser", Prefs.lastFmSessionUser(context).ifBlank { Prefs.lastFmUser(context) })
            .put("ytSignedIn", YtMusic.isSignedIn(Prefs.ytMusicCookie(context)))
    }

    private fun playJson(r: PlayRow) = JSONObject()
        .put("playId", r.playId).put("trackId", r.trackId).put("startedAt", r.startedAt).put("playedMs", r.playedMs)
        .put("sourceApp", r.sourceApp).put("detector", r.detector).put("title", r.title).put("artist", r.artist)
        .put("language", r.language).put("isFavourite", r.isFavourite).put("firstSeen", r.firstSeen)
        .put("coverUrl", r.coverUrl).put("genre", r.genre).put("onlineTitle", r.onlineTitle).put("onlineArtist", r.onlineArtist)

    private fun songs(list: List<Song>) = JSONArray(
        list.map {
            JSONObject().put("videoId", it.videoId.orEmpty()).put("title", it.title).put("artist", it.artist)
                .put("thumbnail", it.thumbnail.orEmpty()).put("durationMs", it.durationMs)
        },
    )

    private fun ok(body: JSONObject) = json(Response.Status.OK, body)
    private fun notFound() = json(Response.Status.NOT_FOUND, JSONObject().put("error", "Not found"))

    private fun json(status: Response.Status, body: JSONObject): Response =
        newFixedLengthResponse(status, "application/json; charset=utf-8", body.toString())
            .apply { addHeader("Cache-Control", "no-store") }
}
