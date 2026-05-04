package com.triggerchain.data.dao

import androidx.room.*
import com.triggerchain.data.model.*
import kotlinx.coroutines.flow.Flow

// ─────────────────────────────────────────
//  AppUsageEvent DAO
// ─────────────────────────────────────────

@Dao
interface AppUsageEventDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AppUsageEvent): Long

    @Update
    suspend fun update(event: AppUsageEvent)

    /** Fetch raw events in a time window — used by LoopMiner to build sessions. */
    @Query("SELECT * FROM app_usage_events WHERE startTime >= :from AND startTime <= :to ORDER BY startTime ASC")
    suspend fun eventsInRange(from: Long, to: Long): List<AppUsageEvent>

    /** All events not yet grouped into a session (endTime = 0 means still active). */
    @Query("SELECT * FROM app_usage_events WHERE endTime = 0")
    suspend fun activeEvents(): List<AppUsageEvent>

    @Query("DELETE FROM app_usage_events WHERE endTime != 0 AND endTime < :before")
    suspend fun deleteOlderThan(before: Long): Int

    // ── Graph Export: Switch Velocity ────────────────────────────────────────
    /**
     * Returns (hour-of-day, switchCount) pairs for the last [days] days,
     * ready for the Switch Velocity graph in Dev 2's UI.
     */
    @Query("""
        SELECT 
            CAST(strftime('%H', datetime(startTime / 1000, 'unixepoch', 'localtime')) AS INTEGER) AS hour,
            COUNT(*) AS switchCount
        FROM app_usage_events
        WHERE startTime >= :since AND type = 'FOREGROUND'
        GROUP BY hour
        ORDER BY hour ASC
    """)
    suspend fun switchVelocityByHour(since: Long): List<HourSwitchCount>
}

/** Lightweight projection for graph data — avoids loading full entities. */
data class HourSwitchCount(val hour: Int, val switchCount: Int)

// ─────────────────────────────────────────
//  Session DAO
// ─────────────────────────────────────────

@Dao
interface SessionDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(session: Session): Long

    @Query("SELECT * FROM sessions WHERE startTime >= :from ORDER BY startTime DESC")
    fun sessionsFrom(from: Long): Flow<List<Session>>

    @Query("SELECT * FROM sessions ORDER BY startTime DESC LIMIT :limit")
    suspend fun recentSessions(limit: Int = 50): List<Session>

    @Query("DELETE FROM sessions WHERE endTime < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

// ─────────────────────────────────────────
//  LoopFingerprint DAO
// ─────────────────────────────────────────

@Dao
interface LoopFingerprintDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(fingerprint: LoopFingerprint)

    @Query("SELECT * FROM loop_fingerprints ORDER BY occurrences DESC")
    fun allFingerprints(): Flow<List<LoopFingerprint>>

    /** For real-time matching in the Accessibility Service. */
    @Query("SELECT * FROM loop_fingerprints WHERE severityScore > 0.5 ORDER BY severityScore DESC")
    suspend fun highSeverityFingerprints(): List<LoopFingerprint>

    @Query("SELECT * FROM loop_fingerprints WHERE sequenceHash = :hash LIMIT 1")
    suspend fun findByHash(hash: String): LoopFingerprint?

    /** Graph Export: sorted by highest frequency. */
    @Query("SELECT * FROM loop_fingerprints ORDER BY occurrences DESC, severityScore DESC")
    suspend fun rankedFingerprints(): List<LoopFingerprint>

    @Query("UPDATE loop_fingerprints SET occurrences = occurrences + 1, lastSeen = :now WHERE sequenceHash = :hash")
    suspend fun incrementOccurrence(hash: String, now: Long = System.currentTimeMillis())
}

// ─────────────────────────────────────────
//  WellnessLog DAO
// ─────────────────────────────────────────

@Dao
interface WellnessLogDao {

    @Insert
    suspend fun insert(log: WellnessLog): Long

    @Query("SELECT * FROM wellness_logs ORDER BY timestamp DESC LIMIT :limit")
    fun recentLogs(limit: Int = 30): Flow<List<WellnessLog>>

    /** Graph Export: Sleep vs Mood correlation dataset. */
    @Query("""
        SELECT * FROM wellness_logs
        WHERE timestamp >= :since
        ORDER BY timestamp ASC
    """)
    suspend fun logsForCorrelation(since: Long): List<WellnessLog>

    /** Latest sleep score snapshot — used by TriggerEngine. */
    @Query("SELECT sleepScore FROM wellness_logs ORDER BY timestamp DESC LIMIT 1")
    fun latestSleepScore(): Flow<Int?>

    @Query("DELETE FROM wellness_logs WHERE timestamp < :before")
    suspend fun deleteOlderThan(before: Long): Int
}

// ─────────────────────────────────────────
//  UserProfile DAO
// ─────────────────────────────────────────

@Dao
interface UserProfileDao {

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(profile: UserProfile)

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    fun observeProfile(): Flow<UserProfile?>

    @Query("SELECT * FROM user_profile WHERE id = 1 LIMIT 1")
    suspend fun getProfile(): UserProfile?
}
