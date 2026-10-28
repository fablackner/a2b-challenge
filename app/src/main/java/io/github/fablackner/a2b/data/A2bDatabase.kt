package io.github.fablackner.a2b.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase

@Database(entities = [Track::class], version = 1, exportSchema = false)
abstract class A2bDatabase : RoomDatabase() {

    abstract fun trackDao(): TrackDao

    companion object {
        @Volatile
        private var instance: A2bDatabase? = null

        fun get(context: Context): A2bDatabase = instance ?: synchronized(this) {
            instance ?: Room.databaseBuilder(
                context.applicationContext, A2bDatabase::class.java, "a2b.db"
            ).build().also { instance = it }
        }
    }
}
