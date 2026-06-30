package com.example.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(
    entities = [
        User::class,
        Song::class,
        LikedSong::class,
        Playlist::class,
        PlaylistSong::class,
        PlayHistory::class
    ],
    version = 1,
    exportSchema = false
)
abstract class ZorifyDatabase : RoomDatabase() {
    abstract fun zorifyDao(): ZorifyDao

    companion object {
        @Volatile
        private var INSTANCE: ZorifyDatabase? = null

        fun getDatabase(context: Context): ZorifyDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    ZorifyDatabase::class.java,
                    "zorify_database"
                )
                .fallbackToDestructiveMigration()
                .build()
                INSTANCE = instance
                instance
            }
        }
    }
}
