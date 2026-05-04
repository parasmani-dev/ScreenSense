package com.triggerchain.data.repository

import android.content.Context
import com.triggerchain.data.dao.HourSwitchCount
import com.triggerchain.data.db.TriggerChainDatabase
import com.triggerchain.data.model.*
import com.triggerchain.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.withContext

/**
 * TriggerChainRepository — single source of truth.
 *
 * Dev 2 should interact exclusively with [TriggerEngine], which delegates
 * to this repository internally. Direct use is available for testing.
 */
class TriggerChainRepository(context: Context) {

    private val db = TriggerChainDatabase.getInstance(context)

    // ─────────────────────────────────────────
    //  Flows (observe from ViewModel / Engine)
    // ─────────────────────────────────────────

    /** Real-time stream of all loop fingerprints, ordered by occurrence count. */
    fun observeLoopFingerprints(): Flow<List<LoopFingerprint>> =
        db.loopFingerprintDao().allFingerprints()

    /** Real-time latest Sleep Threat Score. Emits null until first log entry. */
    fun observeLatestSleepScore(): Flow<Int?> =
        db.wellnessLogDao().latestSleepScore()

    /** Stream of recent wellness logs. */
    fun observeRecentWellnessLogs(limit: Int = 30): Flow<List<WellnessLog>> =
        db.wellnessLogDao().recentLogs(limit)

    /** Session stream for the past N days. */
    fun observeSessions(since: Long = TimeUtils.sevenDaysAgoMillis()): Flow<List<Session>> =
        db.sessionDao().sessionsFrom(since)

    /** UserProfile — emits once and on every profile update. */
    fun observeUserProfile(): Flow<UserProfile?> =
        db.userProfileDao().observeProfile()

    // ─────────────────────────────────────────
    //  One-shot suspend queries (Graph Exports)
    // ─────────────────────────────────────────

    /**
     * Graph Export 1: Switch Velocity by hour-of-day.
     * Returns a list of (hour 0-23, switchCount) pairs for the last 7 days.
     * Used by Dev 2 to render the Switch Velocity bar chart.
     */
    suspend fun getSwitchVelocityData(
        since: Long = TimeUtils.sevenDaysAgoMillis()
    ): List<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        db.appUsageEventDao()
            .switchVelocityByHour(since)
            .map { it.hour to it.switchCount }
    }

    /**
     * Graph Export 2: Loop Fingerprints ranked by highest frequency.
     * Used by Dev 2 to render the behavioural loop list.
     */
    suspend fun getRankedFingerprints(): List<LoopFingerprint> =
        withContext(Dispatchers.IO) {
            db.loopFingerprintDao().rankedFingerprints()
        }

    /**
     * Graph Export 3: Sleep score vs Mood score correlation dataset.
     * Returns paired (sleepScore, moodScore) for scatter/line chart.
     * Used by Dev 2 to render the wellness correlation graph.
     */
    suspend fun getSleepMoodCorrelation(
        since: Long = TimeUtils.sevenDaysAgoMillis()
    ): List<Pair<Int, Int>> = withContext(Dispatchers.IO) {
        db.wellnessLogDao()
            .logsForCorrelation(since)
            .map { it.sleepScore to it.moodScore }
    }

    // ─────────────────────────────────────────
    //  Writes
    // ─────────────────────────────────────────

    suspend fun insertWellnessLog(log: WellnessLog): Long = withContext(Dispatchers.IO) {
        db.wellnessLogDao().insert(log)
    }

    suspend fun upsertUserProfile(profile: UserProfile) = withContext(Dispatchers.IO) {
        db.userProfileDao().upsert(profile)
    }

    suspend fun getUserProfile(): UserProfile? = withContext(Dispatchers.IO) {
        db.userProfileDao().getProfile()
    }

    // ─────────────────────────────────────────
    //  Gateway App Analysis
    // ─────────────────────────────────────────

    /**
     * Identifies the "Gateway App" — the first app in the highest-severity loop.
     * This is the entry point that most reliably predicts a harmful usage chain.
     */
    suspend fun getGatewayApp(): String? = withContext(Dispatchers.IO) {
        db.loopFingerprintDao()
            .rankedFingerprints()
            .maxByOrNull { it.severityScore }
            ?.appNames
            ?.firstOrNull()
    }
}
