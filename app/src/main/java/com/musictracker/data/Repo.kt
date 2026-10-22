package com.musictracker.data

import android.content.Context

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
            val inserted = dao.insertTrack(
                Track(
                    title = title,
                    artist = artist,
                    normalizedKey = key,
                    language = SongText.language(title, artist),
                    labelRaw = labelRaw,
                    firstSeen = startedAt,
                ),
            )
            if (inserted > 0) inserted else dao.trackByKey(key)!!.id
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

    suspend fun updatePlayedMs(context: Context, playId: Long, playedMs: Long) =
        dao(context).setPlayedMs(playId, playedMs)

    /** Renames a song. If the new name matches another song, the two are merged. */
    suspend fun editTrack(context: Context, trackId: Long, title: String, artist: String, language: String) {
        val dao = dao(context)
        val track = dao.trackById(trackId) ?: return
        val key = SongText.key(title, artist)
        val other = dao.trackByKey(key)
        if (other != null && other.id != trackId) {
            dao.movePlays(trackId, other.id)
            dao.updateTrack(other.copy(isFavourite = other.isFavourite || track.isFavourite, language = language))
            dao.deleteTrack(trackId)
        } else {
            dao.updateTrack(track.copy(title = title, artist = artist, normalizedKey = key, language = language))
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
}
