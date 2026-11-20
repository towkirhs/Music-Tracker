package com.musictracker.data

import android.content.Context
import com.musictracker.online.Lookup

/** All writes to the library go through here. */
object Repo {

    private fun dao(context: Context) = AppDatabase.get(context).music()

    /** Finds the track for this title + artist (creating it if new) and logs a play. Returns the play id. */
    suspend fun addPlay(
        context: Context,
        title: String,
        artist: String,
        labelRaw: String,
        sourceApp: String,
        detector: String,
        startedAt: Long,
        playedMs: Long,
    ): Long {
        val dao = dao(context)
        val key = SongText.key(title, artist)
        val trackId = dao.trackByKey(key)?.id ?: run {
            val artistLanguage = if (artist.isBlank()) null else dao.languagesOfArtist(artist, -1)
                .groupingBy { it }.eachCount().maxByOrNull { it.value }?.key
            val inserted = dao.insertTrack(
                Track(
                    title = title,
                    artist = artist,
                    normalizedKey = key,
                    language = SongText.detectLanguage(title, artist, artistLanguage = artistLanguage),
                    labelRaw = labelRaw,
                    firstSeen = startedAt,
                ),
            )
            if (inserted > 0) {
                Lookup.kick(context)
                inserted
            } else {
                dao.trackByKey(key)!!.id
            }
        }
        return dao.insertPlay(
            Play(
                trackId = trackId,
                startedAt = startedAt,
                playedMs = playedMs,
                sourceApp = sourceApp,
                detector = detector,
            ),
        )
    }

    /**
     * Adds a play imported from Last.fm, Spotify or YouTube Music, unless this song already has a play
     * within 2 minutes of it (counted here, or imported before). Returns true when added.
     */
    suspend fun importPlay(context: Context, title: String, artist: String, sourceApp: String, detector: String, startedAt: Long, playedMs: Long): Boolean {
        val dao = dao(context)
        dao.trackByKey(SongText.key(title, artist))?.let { track ->
            if (dao.countPlaysBetween(track.id, startedAt - 120_000, startedAt + 120_000) > 0) return false
        }
        addPlay(context, title, artist, "imported from $sourceApp", sourceApp, detector, startedAt, playedMs)
        return true
    }

    suspend fun updatePlayedMs(context: Context, playId: Long, playedMs: Long) =
        dao(context).setPlayedMs(playId, playedMs)

    /** Renames a song. If the new name matches another song, the two are merged. */
    suspend fun editTrack(context: Context, trackId: Long, title: String, artist: String, language: String) {
        val dao = dao(context)
        val track = dao.trackById(trackId) ?: return
        val key = SongText.key(title, artist)
        val other = dao.trackByKey(key)
        val byUser = track.languageByUser || language != track.language
        if (other != null && other.id != trackId) {
            dao.movePlays(trackId, other.id)
            dao.updateTrack(
                other.copy(
                    isFavourite = other.isFavourite || track.isFavourite,
                    language = language,
                    languageByUser = other.languageByUser || byUser,
                ),
            )
            dao.deleteTrack(trackId)
        } else {
            val renamed = key != track.normalizedKey
            dao.updateTrack(
                track.copy(
                    title = title, artist = artist, normalizedKey = key, language = language, languageByUser = byUser,
                    // A new name may be a different song: look it up again.
                    lookedUpAt = if (renamed) 0 else track.lookedUpAt,
                    coverUrl = if (renamed) "" else track.coverUrl,
                    onlineTitle = if (renamed) "" else track.onlineTitle,
                    onlineArtist = if (renamed) "" else track.onlineArtist,
                ),
            )
            if (renamed) Lookup.kick(context)
        }
    }

    suspend fun setFavourite(context: Context, trackId: Long, favourite: Boolean) =
        dao(context).setFavourite(trackId, favourite)

    suspend fun deleteTrack(context: Context, trackId: Long) {
        val dao = dao(context)
        dao.deletePlaysOfTrack(trackId)
        dao.deleteTrack(trackId)
    }

    /** Deletes one play; removes the song too if that was its only play. */
    suspend fun deletePlay(context: Context, playId: Long, trackId: Long) {
        val dao = dao(context)
        dao.deletePlay(playId)
        if (dao.countPlaysOfTrack(trackId) == 0) dao.deleteTrack(trackId)
    }

    suspend fun saveSamples(context: Context, samples: List<LabelSample>) {
        if (samples.isEmpty()) return
        val dao = dao(context)
        dao.insertSamples(samples)
        dao.trimSamples()
    }

    suspend fun addUnidentified(context: Context, item: Unidentified) {
        val dao = dao(context)
        dao.insertUnidentified(item)
        dao.trimUnidentified()
    }

    /** Names an unidentified clip: logs it as a play and removes it from the inbox. */
    suspend fun nameUnidentified(context: Context, item: Unidentified, title: String, artist: String) {
        addPlay(
            context, title.trim(), artist.trim(), "named by you: " + item.lyrics.take(120),
            item.sourceApp, "you", item.capturedAt, PlayTracker_MIN_MS,
        )
        dao(context).deleteUnidentified(item.id)
    }

    suspend fun dismissUnidentified(context: Context, id: Long) = dao(context).deleteUnidentified(id)

    private const val PlayTracker_MIN_MS = 15_000L
}
