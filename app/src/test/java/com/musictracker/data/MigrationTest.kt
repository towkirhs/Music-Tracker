package com.musictracker.data

import android.app.Application
import android.database.sqlite.SQLiteDatabase
import androidx.room.Room
import androidx.test.core.app.ApplicationProvider
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

/** A library saved by the previous version (database 3) opens in this one with every song and play kept. */
@RunWith(RobolectricTestRunner::class)
@Config(sdk = [34])
class MigrationTest {

    @Test
    fun version3LibraryIsKept() = runBlocking {
        val app = ApplicationProvider.getApplicationContext<Application>()
        val name = "migration-test.db"
        app.deleteDatabase(name)
        SQLiteDatabase.openOrCreateDatabase(app.getDatabasePath(name).apply { parentFile?.mkdirs() }, null).use { db ->
            V3_SCHEMA.forEach(db::execSQL)
            db.execSQL(
                "INSERT INTO tracks (id, title, artist, normalizedKey, language, labelRaw, firstSeen, isFavourite) " +
                    "VALUES (1, 'Tumi Robe Nirobe', '', 'tumi robe nirobe|', 'English/other', 'Tumi Robe Nirobe', 1000, 1)",
            )
            db.execSQL("INSERT INTO plays (trackId, startedAt, playedMs, sourceApp, detector) VALUES (1, 2000, 60000, 'Instagram', 'label')")
            db.version = 3
        }

        val room = Room.databaseBuilder(app, AppDatabase::class.java, name)
            .addMigrations(AppDatabase.MIGRATION_3_4)
            .allowMainThreadQueries()
            .build()
        val tracks = room.music().allTracks()
        assertEquals(1, tracks.size)
        val t = tracks.first()
        assertEquals("Tumi Robe Nirobe", t.title)
        assertEquals(true, t.isFavourite)
        assertEquals("", t.coverUrl)
        assertEquals(0L, t.lookedUpAt)
        assertEquals(false, t.languageByUser)
        assertEquals(1, room.music().allPlays().size)
        room.close()
    }

    private companion object {
        val V3_SCHEMA = listOf(
            "CREATE TABLE IF NOT EXISTS `tracks` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `title` TEXT NOT NULL, " +
                "`artist` TEXT NOT NULL, `normalizedKey` TEXT NOT NULL, `language` TEXT NOT NULL, `labelRaw` TEXT NOT NULL, " +
                "`firstSeen` INTEGER NOT NULL, `isFavourite` INTEGER NOT NULL)",
            "CREATE UNIQUE INDEX IF NOT EXISTS `index_tracks_normalizedKey` ON `tracks` (`normalizedKey`)",
            "CREATE TABLE IF NOT EXISTS `plays` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `trackId` INTEGER NOT NULL, " +
                "`startedAt` INTEGER NOT NULL, `playedMs` INTEGER NOT NULL, `sourceApp` TEXT NOT NULL, `detector` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_plays_trackId` ON `plays` (`trackId`)",
            "CREATE INDEX IF NOT EXISTS `index_plays_startedAt` ON `plays` (`startedAt`)",
            "CREATE TABLE IF NOT EXISTS `label_samples` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`capturedAt` INTEGER NOT NULL, `sourceApp` TEXT NOT NULL, `viewId` TEXT NOT NULL, `content` TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `unidentified` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                "`capturedAt` INTEGER NOT NULL, `sourceApp` TEXT NOT NULL, `reelLabel` TEXT NOT NULL, " +
                "`lyrics` TEXT NOT NULL, `language` TEXT NOT NULL, `guessTitle` TEXT NOT NULL, `guessArtist` TEXT NOT NULL)",
            "CREATE TABLE IF NOT EXISTS `recognitions` (`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recognizedAt` INTEGER NOT NULL, " +
                "`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `coverUrl` TEXT NOT NULL, " +
                "`heardFrom` TEXT NOT NULL, `shazamUrl` TEXT NOT NULL, `spotifyUrl` TEXT NOT NULL, " +
                "`appleMusicUrl` TEXT NOT NULL, `youtubeVideoId` TEXT NOT NULL)",
            "CREATE INDEX IF NOT EXISTS `index_recognitions_recognizedAt` ON `recognitions` (`recognizedAt`)",
        )
    }
}
