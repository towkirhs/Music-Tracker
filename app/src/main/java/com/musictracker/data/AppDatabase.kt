package com.musictracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase

@Database(entities = [Track::class, Play::class, LabelSample::class, Unidentified::class], version = 2, exportSchema = false)
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

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_tracker.db",
                ).addMigrations(MIGRATION_1_2).build().also { instance = it }
            }
    }
}
