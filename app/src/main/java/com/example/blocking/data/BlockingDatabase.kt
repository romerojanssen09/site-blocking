package com.example.blocking.data

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters

@Database(entities = [BlockedSite::class], version = 1, exportSchema = false)
@TypeConverters(Converters::class)
abstract class BlockingDatabase : RoomDatabase() {
    abstract fun blockedSiteDao(): BlockedSiteDao
    
    companion object {
        @Volatile
        private var INSTANCE: BlockingDatabase? = null
        
        fun getDatabase(context: Context): BlockingDatabase {
            return INSTANCE ?: synchronized(this) {
                val instance = Room.databaseBuilder(
                    context.applicationContext,
                    BlockingDatabase::class.java,
                    "blocking_database"
                ).build()
                INSTANCE = instance
                instance
            }
        }
    }
}
