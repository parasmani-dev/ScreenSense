package com.triggerchain.worker

import android.app.usage.UsageEvents
import android.app.usage.UsageStatsManager
import android.content.Context
import android.util.Log
import androidx.work.*
import com.triggerchain.data.db.TriggerChainDatabase
import com.triggerchain.data.model.AppEventType
import com.triggerchain.data.model.AppUsageEvent
import com.triggerchain.util.AppFilter
import com.triggerchain.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit

/**
 * UsageSyncWorker — bridges Android's [UsageStatsManager] with Room DB.
 *
 * Runs every 15 minutes to:
 *  1. Query UsageStatsManager for events since the last sync.
 *  2. Map ACTIVITY_RESUMED/PAUSED → AppUsageEvent(FOREGROUND/BACKGROUND).
 *  3. Insert only new events (idempotent via startTime dedup).
 *  4. Ensures historical graphs match real-time AccessibilityService observations.
 *
 * Requires: android.permission.PACKAGE_USAGE_STATS (granted via Settings)
 */
class UsageSyncWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "usage_sync_periodic"
        private const val TAG = "UsageSyncWorker"
        private const val PREFS_NAME = "triggerchain_prefs"
        private const val PREF_LAST_SYNC = "last_usage_sync_time"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<UsageSyncWorker>(
                TimeUtils.WORKER_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresDeviceIdle(false)
                        .build()
                )
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    private val db by lazy { TriggerChainDatabase.getInstance(applicationContext) }
    private val prefs by lazy {
        applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
    }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val usageManager = applicationContext.getSystemService(
                Context.USAGE_STATS_SERVICE
            ) as? UsageStatsManager ?: return@withContext Result.failure()

            val lastSync = prefs.getLong(PREF_LAST_SYNC, TimeUtils.oneDayAgoMillis())
            val now = TimeUtils.nowMillis()

            Log.d(TAG, "Syncing usage events from ${lastSync} to ${now}")

            val events = usageManager.queryEvents(lastSync, now)
            val toInsert = mutableListOf<AppUsageEvent>()

            val event = UsageEvents.Event()
            while (events.hasNextEvent()) {
                events.getNextEvent(event)

                val pkg = event.packageName ?: continue
                if (AppFilter.shouldIgnore(pkg)) continue

                val type = when (event.eventType) {
                    UsageEvents.Event.ACTIVITY_RESUMED -> AppEventType.FOREGROUND
                    UsageEvents.Event.ACTIVITY_PAUSED  -> AppEventType.BACKGROUND
                    else -> continue
                }

                toInsert.add(
                    AppUsageEvent(
                        packageName = pkg,
                        startTime = event.timeStamp,
                        endTime = if (type == AppEventType.BACKGROUND) event.timeStamp else 0L,
                        type = type
                    )
                )
            }

            if (toInsert.isNotEmpty()) {
                toInsert.forEach { db.appUsageEventDao().insert(it) }
                Log.d(TAG, "Inserted ${toInsert.size} usage events")
            }

            prefs.edit().putLong(PREF_LAST_SYNC, now).apply()
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "UsageSyncWorker failed", e)
            Result.retry()
        }
    }
}
