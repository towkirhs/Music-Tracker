package com.musictracker.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import kotlinx.coroutines.flow.Flow

@Dao
interface MusicDao {

    // Tracks

    @Query("SELECT * FROM tracks WHERE normalizedKey = :key LIMIT 1")
    suspend fun trackByKey(key: String): Track?

    @Query("SELECT * FROM tracks WHERE id = :id LIMIT 1")
    suspend fun trackById(id: Long): Track?

    @Insert(onConflict = OnConflictStrategy.IGNORE)
    suspend fun insertTrack(track: Track): Long

    @Update
    suspend fun updateTrack(track: Track)

    @Query("UPDATE tracks SET isFavourite = :favourite WHERE id = :id")
    suspend fun setFavourite(id: Long, favourite: Boolean)

    @Query("DELETE FROM tracks WHERE id = :id")
    suspend fun deleteTrack(id: Long)

    @Query("SELECT * FROM tracks")
    suspend fun allTracks(): List<Track>

    // Plays

    @Insert
    suspend fun insertPlay(play: Play): Long

    @Query("UPDATE plays SET playedMs = :playedMs WHERE id = :id")
    suspend fun setPlayedMs(id: Long, playedMs: Long)

    @Query("UPDATE plays SET trackId = :toTrack WHERE trackId = :fromTrack")
    suspend fun movePlays(fromTrack: Long, toTrack: Long)

    @Query("DELETE FROM plays WHERE id = :id")
    suspend fun deletePlay(id: Long)

    @Query("DELETE FROM plays WHERE trackId = :trackId")
    suspend fun deletePlaysOfTrack(trackId: Long)

    @Query("SELECT COUNT(*) FROM plays WHERE trackId = :trackId")
    suspend fun countPlaysOfTrack(trackId: Long): Int

    @Query("SELECT * FROM plays")
    suspend fun allPlays(): List<Play>

    @Query(
        """
        SELECT p.id AS playId, p.trackId AS trackId, p.startedAt AS startedAt, p.playedMs AS playedMs,
               p.sourceApp AS sourceApp, p.detector AS detector,
               t.title AS title, t.artist AS artist, t.language AS language,
               t.isFavourite AS isFavourite, t.firstSeen AS firstSeen
        FROM plays p JOIN tracks t ON t.id = p.trackId
        ORDER BY p.startedAt DESC
        """,
    )
    fun observePlays(): Flow<List<PlayRow>>

    @Query(
        """
        SELECT p.id AS playId, p.trackId AS trackId, p.startedAt AS startedAt, p.playedMs AS playedMs,
               p.sourceApp AS sourceApp, p.detector AS detector,
               t.title AS title, t.artist AS artist, t.language AS language,
               t.isFavourite AS isFavourite, t.firstSeen AS firstSeen
        FROM plays p JOIN tracks t ON t.id = p.trackId
        ORDER BY p.startedAt DESC
        """,
    )
    suspend fun allPlayRows(): List<PlayRow>

    // Restore from a backup file

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertTracks(tracks: List<Track>)

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertPlays(plays: List<Play>)

    @Query("DELETE FROM plays")
    suspend fun clearPlays()

    @Query("DELETE FROM tracks")
    suspend fun clearTracks()

    // Unidentified inbox

    @Insert
    suspend fun insertUnidentified(item: Unidentified): Long

    @Query("SELECT * FROM unidentified ORDER BY capturedAt DESC")
    fun observeUnidentified(): Flow<List<Unidentified>>

    @Query("DELETE FROM unidentified WHERE id = :id")
    suspend fun deleteUnidentified(id: Long)

    @Query("DELETE FROM unidentified WHERE id NOT IN (SELECT id FROM unidentified ORDER BY capturedAt DESC LIMIT 500)")
    suspend fun trimUnidentified()

    @Query("SELECT title, artist FROM tracks")
    suspend fun allTitles(): List<TitleArtist>

    // Label lab

    @Insert
    suspend fun insertSamples(samples: List<LabelSample>)

    @Query("SELECT * FROM label_samples ORDER BY id DESC LIMIT 400")
    fun observeSamples(): Flow<List<LabelSample>>

    @Query("SELECT * FROM label_samples ORDER BY id ASC")
    suspend fun allSamples(): List<LabelSample>

    @Query("DELETE FROM label_samples WHERE id NOT IN (SELECT id FROM label_samples ORDER BY id DESC LIMIT 3000)")
    suspend fun trimSamples()

    @Query("DELETE FROM label_samples")
    suspend fun clearSamples()
}
