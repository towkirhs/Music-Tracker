package com.musictracker.online

import android.content.Context
import com.musictracker.data.AppDatabase
import com.musictracker.data.SongText
import com.musictracker.data.Track
import com.musictracker.player.YouTube
import com.musictracker.tracking.Prefs
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import java.io.IOException

/**
 * Looks each new song up online once: cover, genre, its proper name, and a better guess at its
 * language. Runs in the background; when offline it stops and tries again next time.
 */
object Lookup {

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val running = Mutex()

    /** Starts looking up songs that haven't been yet (does nothing if it is already running). */
    fun kick(context: Context) {
        val app = context.applicationContext
        if (running.isLocked) return
        scope.launch {
            running.withLock {
                try {
                    run(app)
                } catch (e: Exception) {
                    android.util.Log.w("Lookup", "song lookup stopped", e) // try again next time
                }
            }
        }
    }

    private suspend fun run(context: Context) {
        val dao = AppDatabase.get(context).music()
        while (true) {
            val batch = dao.tracksToLookUp(20)
            if (batch.isEmpty()) return
            for (track in batch) {
                val found = try {
                    lookUp(context, track)
                } catch (_: IOException) {
                    return // offline or a server is down: try again next time
                }
                dao.setLookup(track.id, found.coverUrl, found.genre, found.title, found.artist, System.currentTimeMillis())
                val artistLanguage = dao.languagesOfArtist(track.artist, track.id)
                    .takeIf { track.artist.isNotBlank() }
                    ?.groupingBy { it }?.eachCount()?.maxByOrNull { it.value }?.key
                dao.setAutoLanguage(track.id, SongText.detectLanguage(track.title, track.artist, found.hints, artistLanguage))
                delay(3_000) // iTunes allows about 20 searches a minute
            }
        }
    }

    private class Found(val coverUrl: String, val genre: String, val title: String, val artist: String, val hints: List<String>)

    private fun lookUp(context: Context, track: Track): Found {
        val itunes = Itunes.find(track.title, track.artist)
        val key = Prefs.lastFmKey(context)
        val tags = if (key.isBlank()) emptyList() else runCatching { LastFm.tags(key, track.title, track.artist) }.getOrDefault(emptyList())
        val cover = itunes?.coverUrl?.ifBlank { null } ?: youTubeCover(track)
        val genre = itunes?.genre?.ifBlank { null } ?: tags.firstOrNull { it.lowercase() !in NOT_GENRES }.orEmpty()
        return Found(
            coverUrl = cover.orEmpty(),
            genre = genre,
            title = itunes?.title.orEmpty(),
            artist = itunes?.artist.orEmpty(),
            hints = listOfNotNull(itunes?.genre) + tags,
        )
    }

    /** YouTube Music has the most Bangla songs; its song results come with square covers. */
    private fun youTubeCover(track: Track): String? = runCatching {
        YouTube.search(listOf(track.title, track.artist).filter { it.isNotBlank() }.joinToString(" "))
            .firstOrNull { Itunes.sameSong(track.title, "", it.title, "") }?.thumbnail
    }.getOrNull()

    private val NOT_GENRES = setOf("seen live", "favorites", "favourite", "love", "beautiful", "awesome", "spotify")
}
