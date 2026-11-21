package com.musictracker.player

import android.content.ContentValues
import android.content.Context
import android.net.Uri
import android.provider.MediaStore
import com.musictracker.audio.Notifications
import com.musictracker.net.Http
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.launch
import okhttp3.Request
import java.io.IOException

/**
 * Saves songs from YouTube to Music/Music Tracker on the phone, one at a time, with a progress
 * notification. Saved songs are normal audio files: other music apps can play them too.
 */
object Downloads {

    private const val FOLDER = "Music/Music Tracker/"
    private const val CHUNK = 1L shl 20 // YouTube sends big files faster in 1 MB pieces

    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    private val queue = Channel<Song>(Channel.UNLIMITED)
    private var waiting = 0

    /** Songs being saved or waiting, by [Song.query]; the value is percent done, -1 while waiting. */
    private val _progress = MutableStateFlow<Map<String, Int>>(emptyMap())
    val progress: StateFlow<Map<String, Int>> = _progress

    private var started = false

    @Synchronized
    fun download(context: Context, songs: List<Song>) {
        val app = context.applicationContext
        if (!started) {
            started = true
            scope.launch { for (song in queue) save(app, song) }
        }
        songs.filter { it.localUri == null && it.query !in _progress.value }.forEach {
            _progress.value = _progress.value + (it.query to -1)
            waiting++
            queue.trySend(it)
        }
    }

    private fun fileName(song: Song): String =
        listOf(song.artist, song.title).filter { it.isNotBlank() }.joinToString(" - ")
            .replace(Regex("""[\\/:*?"<>|]"""), "_").take(120)

    private suspend fun save(context: Context, song: Song) {
        waiting--
        val name = fileName(song)
        val more = if (waiting > 0) " · $waiting more waiting" else ""
        var item: Uri? = null
        try {
            if (alreadySaved(context, name)) {
                Notifications.showDownload(context, "Already downloaded", song.title, null)
                return
            }
            Notifications.showDownload(context, "Downloading ${song.title}", "Finding the song$more", -1)
            val id = song.videoId ?: YouTube.videoFor(song.query) ?: throw IOException("Couldn't find it on YouTube")
            val (url, mime) = YouTube.downloadStream(id)
            val ext = if (mime == "audio/webm") "webm" else "m4a"
            val values = ContentValues().apply {
                put(MediaStore.Audio.Media.DISPLAY_NAME, "$name.$ext")
                put(MediaStore.Audio.Media.MIME_TYPE, mime)
                put(MediaStore.Audio.Media.RELATIVE_PATH, FOLDER)
                put(MediaStore.Audio.Media.TITLE, song.title)
                put(MediaStore.Audio.Media.ARTIST, song.artist)
                put(MediaStore.Audio.Media.IS_MUSIC, 1)
                put(MediaStore.Audio.Media.IS_PENDING, 1)
            }
            val resolver = context.contentResolver
            item = resolver.insert(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, values) ?: throw IOException("Couldn't create the file")
            resolver.openOutputStream(item)!!.use { out ->
                var done = 0L
                var total = Long.MAX_VALUE
                while (done < total) {
                    val request = Request.Builder().url(url).header("Range", "bytes=$done-${done + CHUNK - 1}")
                        .header("User-Agent", Http.USER_AGENT).build()
                    Http.client.newCall(request).execute().use { response ->
                        if (!response.isSuccessful) throw IOException("YouTube answered ${response.code}")
                        total = response.header("Content-Range")?.substringAfter('/')?.toLongOrNull() ?: response.body!!.contentLength()
                        val bytes = response.body!!.bytes()
                        if (bytes.isEmpty()) throw IOException("The download stopped")
                        out.write(bytes)
                        done += bytes.size
                    }
                    val percent = (done * 100 / total).toInt()
                    _progress.value = _progress.value + (song.query to percent)
                    Notifications.showDownload(context, "Downloading ${song.title}", "$percent%$more", percent)
                }
            }
            resolver.update(item, ContentValues().apply { put(MediaStore.Audio.Media.IS_PENDING, 0) }, null, null)
            Notifications.showDownload(context, "Downloaded ${song.title}", "Saved to Music/Music Tracker$more", if (waiting > 0) -1 else null)
        } catch (e: Exception) {
            item?.let { runCatching { context.contentResolver.delete(it, null, null) } }
            Notifications.showDownload(context, "Couldn't download ${song.title}", e.message ?: "Check your internet", null)
        } finally {
            _progress.value = _progress.value - song.query
        }
    }

    /** True if Music/Music Tracker already has this song (any file type). */
    private fun alreadySaved(context: Context, name: String): Boolean =
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, arrayOf(MediaStore.Audio.Media._ID),
            "${MediaStore.Audio.Media.RELATIVE_PATH} = ? AND ${MediaStore.Audio.Media.DISPLAY_NAME} LIKE ?",
            arrayOf(FOLDER, "$name.%"), null,
        )?.use { it.count > 0 } ?: false
}
