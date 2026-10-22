package com.musictracker.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Track::class, Play::class, LabelSample::class], version = 1, exportSchema = false)
abstract class AppDatabase : RoomDatabase() {
    abstract fun music(): MusicDao

    companion object {
        @Volatile
        private var instance: AppDatabase? = null

        fun get(context: Context): AppDatabase =
            instance ?: synchronized(this) {
                instance ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "music_tracker.db",
                ).build().also { instance = it }
            }
    }
}
