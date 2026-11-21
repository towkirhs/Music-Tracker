package com.musictracker.data

import android.Manifest
import android.content.ContentUris
import android.content.Context
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.MediaStore
import androidx.core.content.ContextCompat
import com.musictracker.player.Song

/** Music files on this phone, from Android's media library (needs permission to read audio). */
object DeviceMusic {

    data class Track(
        val uri: String,
        val title: String,
        val artist: String,
        val album: String,
        val folder: String,
        val durationMs: Long,
        val coverUri: String,
        val addedAt: Long,
    ) {
        fun toSong() = Song(videoId = null, title = title, artist = artist, thumbnail = coverUri, durationMs = durationMs, localUri = uri)
    }

    val permission: String =
        if (Build.VERSION.SDK_INT >= 33) Manifest.permission.READ_MEDIA_AUDIO else Manifest.permission.READ_EXTERNAL_STORAGE

    fun canRead(context: Context) = ContextCompat.checkSelfPermission(context, permission) == PackageManager.PERMISSION_GRANTED

    private val ALBUM_ART: Uri = Uri.parse("content://media/external/audio/albumart")

    /** Every song on the phone (not ringtones or voice notes), newest first. Blocking: use a background thread. */
    fun all(context: Context): List<Track> {
        if (!canRead(context)) return emptyList()
        val columns = arrayOf(
            MediaStore.Audio.Media._ID, MediaStore.Audio.Media.TITLE, MediaStore.Audio.Media.ARTIST,
            MediaStore.Audio.Media.ALBUM, MediaStore.Audio.Media.ALBUM_ID, MediaStore.Audio.Media.DURATION,
            MediaStore.Audio.Media.RELATIVE_PATH, MediaStore.Audio.Media.DATE_ADDED, MediaStore.Audio.Media.DISPLAY_NAME,
        )
        val out = mutableListOf<Track>()
        context.contentResolver.query(
            MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, columns,
            "${MediaStore.Audio.Media.IS_MUSIC} != 0", null, "${MediaStore.Audio.Media.DATE_ADDED} DESC",
        )?.use { c ->
            while (c.moveToNext()) {
                val id = c.getLong(0)
                val (title, artist) = names(c.getString(1).orEmpty(), c.getString(2).orEmpty(), c.getString(8).orEmpty())
                out += Track(
                    uri = ContentUris.withAppendedId(MediaStore.Audio.Media.EXTERNAL_CONTENT_URI, id).toString(),
                    title = title,
                    artist = artist,
                    album = c.getString(3).orEmpty().takeUnless { it == "<unknown>" }.orEmpty(),
                    folder = c.getString(6).orEmpty().trimEnd('/').ifEmpty { "Phone storage" },
                    durationMs = c.getLong(5),
                    coverUri = ContentUris.withAppendedId(ALBUM_ART, c.getLong(4)).toString(),
                    addedAt = c.getLong(7) * 1000,
                )
            }
        }
        return out
    }

    /**
     * Title and artist. Files with no artist tag (like this app's downloads, named "Artist - Title")
     * get them from the file name.
     */
    internal fun names(title: String, artist: String, fileName: String): Pair<String, String> {
        val knownArtist = artist.takeUnless { it.isBlank() || it == "<unknown>" }
        if (knownArtist != null) return title.ifBlank { fileName.substringBeforeLast('.') } to knownArtist
        val base = title.ifBlank { fileName.substringBeforeLast('.') }
        val dash = base.indexOf(" - ")
        return if (dash > 0) base.substring(dash + 3).trim() to base.substring(0, dash).trim() else base to ""
    }
}
