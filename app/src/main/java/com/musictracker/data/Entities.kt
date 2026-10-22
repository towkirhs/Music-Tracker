package com.musictracker.data

import androidx.room.Entity
import androidx.room.Index
import androidx.room.PrimaryKey

/** One song. Plays from different apps with the same title + artist land on the same track. */
@Entity(tableName = "tracks", indices = [Index(value = ["normalizedKey"], unique = true)])
data class Track(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val title: String,
    val artist: String,
    /** Lower-case title|artist without brackets and punctuation, used to spot the same song. */
    val normalizedKey: String,
    /** "Bangla", "Hindi" or "English/other", from the title's script. Editable. */
    val language: String,
    /** The label exactly as it appeared on screen the first time. */
    val labelRaw: String,
    val firstSeen: Long,
    val isFavourite: Boolean = false,
)

/** One listen of a track that lasted at least 10 seconds. */
@Entity(tableName = "plays", indices = [Index("trackId"), Index("startedAt")])
data class Play(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val trackId: Long,
    val startedAt: Long,
    val playedMs: Long,
    /** "Instagram", "YouTube", "TikTok", "Spotify"… */
    val sourceApp: String,
    /** "label" (read from screen) or "now-playing" (music app info). */
    val detector: String,
)

/** Raw on-screen text saved by the Label lab, to tune the label reader for each app. */
@Entity(tableName = "label_samples")
data class LabelSample(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val capturedAt: Long,
    val sourceApp: String,
    val viewId: String,
    val content: String,
)

/** A play joined with its track, which is what every screen shows. */
data class PlayRow(
    val playId: Long,
    val trackId: Long,
    val startedAt: Long,
    val playedMs: Long,
    val sourceApp: String,
    val detector: String,
    val title: String,
    val artist: String,
    val language: String,
    val isFavourite: Boolean,
    val firstSeen: Long,
)
