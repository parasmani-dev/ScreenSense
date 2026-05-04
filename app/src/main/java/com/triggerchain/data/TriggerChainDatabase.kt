package com.triggerchain.data.db

import android.content.Context
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.TypeConverters
import com.triggerchain.data.dao.*
import com.triggerchain.data.model.*

@Database(
    entities = [
        AppUsageEvent::class,
        Session::class,
        LoopFingerprint::class,
        WellnessLog::class,
        UserProfile::class
    ],
    version = 1,
    exportSchema = true
)
@TypeConverters(Converters::class)
abstract class TriggerChainDatabase : RoomDatabase() {

    abstract fun appUsageEventDao(): AppUsageEventDao
    abstract fun sessionDao(): SessionDao
    abstract fun loopFingerprintDao(): LoopFingerprintDao
    abstract fun wellnessLogDao(): WellnessLogDao
    abstract fun userProfileDao(): UserProfileDao

    companion object {
        private const val DB_NAME = "triggerchain.db"

        @Volatile
        private var INSTANCE: TriggerChainDatabase? = null

        fun getInstance(context: Context): TriggerChainDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: buildDatabase(context).also { INSTANCE = it }
            }

        private fun buildDatabase(context: Context) =
            Room.databaseBuilder(
                context.applicationContext,
                TriggerChainDatabase::class.java,
                DB_NAME
            )
                .fallbackToDestructiveMigration()   // swap for proper migrations in production
                .build()
    }
}
