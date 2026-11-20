package com.musictracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(
    entities = [Track::class, Play::class, LabelSample::class, Unidentified::class, Recognition::class],
    version = 4,
    exportSchema = false,
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun music(): MusicDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        /** Version 2 adds the Unidentified inbox. Keeps the whole library. */
        private val MIGRATION_1_2 = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `unidentified` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, " +
                        "`capturedAt` INTEGER NOT NULL, `sourceApp` TEXT NOT NULL, `reelLabel` TEXT NOT NULL, " +
                        "`lyrics` TEXT NOT NULL, `language` TEXT NOT NULL, " +
                        "`guessTitle` TEXT NOT NULL, `guessArtist` TEXT NOT NULL)",
                )
            }
        }

        /** Version 3 adds the history of songs named by the Recognise button. Keeps the whole library. */
        private val MIGRATION_2_3 = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL(
                    "CREATE TABLE IF NOT EXISTS `recognitions` (" +
                        "`id` INTEGER PRIMARY KEY AUTOINCREMENT NOT NULL, `recognizedAt` INTEGER NOT NULL, " +
                        "`title` TEXT NOT NULL, `artist` TEXT NOT NULL, `album` TEXT NOT NULL, `coverUrl` TEXT NOT NULL, " +
                        "`heardFrom` TEXT NOT NULL, `shazamUrl` TEXT NOT NULL, `spotifyUrl` TEXT NOT NULL, " +
                        "`appleMusicUrl` TEXT NOT NULL, `youtubeVideoId` TEXT NOT NULL)",
                )
                db.execSQL("CREATE INDEX IF NOT EXISTS `index_recognitions_recognizedAt` ON `recognitions` (`recognizedAt`)")
            }
        }

        /** Version 4 adds cover, genre, the name found online and a "language set by you" flag. Keeps the whole library. */
        val MIGRATION_3_4 = object : Migration(3, 4) {
            override fun migrate(db: SupportSQLiteDatabase) {
                listOf("coverUrl", "genre", "onlineTitle", "onlineArtist").forEach {
                    db.execSQL("ALTER TABLE `tracks` ADD COLUMN `$it` TEXT NOT NULL DEFAULT ''")
                }
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `lookedUpAt` INTEGER NOT NULL DEFAULT 0")
                db.execSQL("ALTER TABLE `tracks` ADD COLUMN `languageByUser` INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_tracker.db",
                ).addMigrations(MIGRATION_1_2, MIGRATION_2_3, MIGRATION_3_4).build().also { instance = it }
            }
    }
}
