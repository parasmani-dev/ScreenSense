package com.triggerchain.worker

import android.content.Context
import android.util.Log
import androidx.work.*
import com.triggerchain.data.db.TriggerChainDatabase
import com.triggerchain.data.model.AppUsageEvent
import com.triggerchain.data.model.LoopFingerprint
import com.triggerchain.data.model.Session
import com.triggerchain.util.Hasher
import com.triggerchain.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.util.concurrent.TimeUnit
import kotlin.math.ln
import kotlin.math.max

/**
 * LoopMiner — runs periodically to:
 *  1. Build [Session]s from raw [AppUsageEvent]s (30-min gap rule).
 *  2. Extract 2-, 3-, and 4-grams from every session.
 *  3. Fingerprint sequences appearing ≥ MIN_OCCURRENCES times in 7 days.
 *  4. Compute severityScore and identify the Gateway App.
 */
class LoopMinerWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        private const val TAG = "LoopMinerWorker"
        const val WORK_NAME = "loop_miner_periodic"

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<LoopMinerWorker>(
                TimeUtils.WORKER_INTERVAL_MINUTES,
                TimeUnit.MINUTES
            )
                .setConstraints(
                    Constraints.Builder()
                        .setRequiresBatteryNotLow(false)  // run even on low battery
                        .build()
                )
                .setBackoffCriteria(BackoffPolicy.EXPONENTIAL, 5, TimeUnit.MINUTES)
                .build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    private val db by lazy { TriggerChainDatabase.getInstance(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            Log.d(TAG, "LoopMiner starting...")
            val since = TimeUtils.sevenDaysAgoMillis()
            val until = TimeUtils.nowMillis()

            // Step 1: Fetch raw events
            val rawEvents = db.appUsageEventDao().eventsInRange(since, until)
            if (rawEvents.isEmpty()) return@withContext Result.success()

            // Step 2: Build sessions
            val sessions = buildSessions(rawEvents)
            sessions.forEach { db.sessionDao().insert(it) }
            Log.d(TAG, "Built ${sessions.size} sessions")

            // Step 3: Extract n-grams and count
            val ngramCounts = extractNgramCounts(sessions)

            // Step 4: Fingerprint frequent sequences
            fingerprintFrequentSequences(ngramCounts, sessions)

            Log.d(TAG, "LoopMiner finished. Fingerprinted ${ngramCounts.size} n-gram types.")
            Result.success()
        } catch (e: Exception) {
            Log.e(TAG, "LoopMiner failed", e)
            Result.retry()
        }
    }

    // ─────────────────────────────────────────
    //  Step 1: Session Builder
    // ─────────────────────────────────────────

    /**
     * Groups events into sessions using the 30-minute gap rule.
     * A new session starts whenever the gap between consecutive events exceeds SESSION_GAP_MS.
     */
    private fun buildSessions(events: List<AppUsageEvent>): List<Session> {
        if (events.isEmpty()) return emptyList()

        val sessions = mutableListOf<Session>()
        var currentBatch = mutableListOf(events[0])

        for (i in 1 until events.size) {
            val gap = events[i].startTime - events[i - 1].startTime
            if (gap > TimeUtils.SESSION_GAP_MS) {
                // Close current batch
                sessions += batchToSession(currentBatch)
                currentBatch = mutableListOf(events[i])
            } else {
                currentBatch.add(events[i])
            }
        }
        if (currentBatch.isNotEmpty()) sessions += batchToSession(currentBatch)

        return sessions
    }

    private fun batchToSession(events: List<AppUsageEvent>): Session {
        val appSequence = events.map { it.packageName }
        val uniqueTransitions = appSequence.zipWithNext().count { (a, b) -> a != b }
        return Session(
            startTime = events.first().startTime,
            endTime = events.last().let { if (it.endTime > 0) it.endTime else TimeUtils.nowMillis() },
            appSequence = appSequence,
            switchCount = uniqueTransitions
        )
    }

    // ─────────────────────────────────────────
    //  Step 2: N-Gram Extraction
    // ─────────────────────────────────────────

    /**
     * Extracts all 2-, 3-, 4-grams from session sequences.
     * Returns a map of (sequenceHash → NgramData).
     */
    private fun extractNgramCounts(sessions: List<Session>): Map<String, NgramData> {
        val counts = mutableMapOf<String, NgramData>()

        for (session in sessions) {
            val seq = session.appSequence
            for (n in 2..4) {
                for (start in 0..seq.size - n) {
                    val subSeq = seq.subList(start, start + n)
                    val hash = Hasher.sequenceHash(subSeq)

                    val existing = counts[hash]
                    if (existing == null) {
                        counts[hash] = NgramData(hash, subSeq, 1, session.startTime)
                    } else {
                        counts[hash] = existing.copy(
                            count = existing.count + 1,
                            lastSeen = maxOf(existing.lastSeen, session.startTime)
                        )
                    }
                }
            }
        }

        return counts
    }

    // ─────────────────────────────────────────
    //  Step 3 & 4: Fingerprinting + Severity
    // ─────────────────────────────────────────

    private suspend fun fingerprintFrequentSequences(
        ngramCounts: Map<String, NgramData>,
        sessions: List<Session>
    ) {
        val frequent = ngramCounts.values.filter { it.count >= TimeUtils.MIN_OCCURRENCES }

        for (ngram in frequent) {
            val existing = db.loopFingerprintDao().findByHash(ngram.hash)

            // Calculate average gap time for this sequence across sessions
            val avgGap = computeAvgGapTime(ngram.sequence, sessions)

            // Severity formula:
            //   - Frequency factor: ln(count) / ln(max_count + 1) normalised 0..1
            //   - Length bonus: longer chains = higher base severity
            //   - Recency boost: sequences seen very recently score higher
            val maxCount = ngramCounts.values.maxOf { it.count }.toFloat()
            val freqFactor = if (maxCount > 0) ln(ngram.count.toFloat()) / ln(maxCount + 1f) else 0f
            val lengthBonus = (ngram.sequence.size - 2) * 0.1f   // 0, 0.1, 0.2 for n=2,3,4
            val recencyBonus = if (ngram.lastSeen > TimeUtils.oneDayAgoMillis()) 0.15f else 0f
            val severity = (freqFactor + lengthBonus + recencyBonus).coerceIn(0f, 1f)

            val fingerprint = LoopFingerprint(
                sequenceHash = ngram.hash,
                appNames = ngram.sequence,
                occurrences = if (existing != null) max(existing.occurrences, ngram.count) else ngram.count,
                severityScore = severity,
                avgGapTime = avgGap,
                lastSeen = ngram.lastSeen
            )

            db.loopFingerprintDao().upsert(fingerprint)
        }
    }

    /**
     * Computes average milliseconds between each consecutive app in [sequence]
     * across all sessions that contain the sequence.
     */
    private fun computeAvgGapTime(sequence: List<String>, sessions: List<Session>): Long {
        val gaps = mutableListOf<Long>()

        for (session in sessions) {
            val seq = session.appSequence
            // Find all starting positions of the target sequence in this session
            for (i in 0..seq.size - sequence.size) {
                if (seq.subList(i, i + sequence.size) == sequence) {
                    // We don't have per-event timestamps here; approximate from session duration
                    val approxPerApp = if (seq.size > 1)
                        (session.endTime - session.startTime) / (seq.size - 1)
                    else 0L
                    gaps.add(approxPerApp)
                }
            }
        }

        return if (gaps.isEmpty()) 0L else gaps.average().toLong()
    }

    // ─────────────────────────────────────────
    //  Internal model
    // ─────────────────────────────────────────

    private data class NgramData(
        val hash: String,
        val sequence: List<String>,
        val count: Int,
        val lastSeen: Long
    )
}

// ─────────────────────────────────────────
//  Cleanup Worker
// ─────────────────────────────────────────

/**
 * Runs daily to prune records older than 30 days.
 */
class CleanupWorker(
    context: Context,
    params: WorkerParameters
) : CoroutineWorker(context, params) {

    companion object {
        const val WORK_NAME = "cleanup_daily"
        private const val RETENTION_DAYS = 30L

        fun schedule(context: Context) {
            val request = PeriodicWorkRequestBuilder<CleanupWorker>(
                TimeUtils.CLEANUP_INTERVAL_DAYS,
                TimeUnit.DAYS
            ).build()

            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }

    private val db by lazy { TriggerChainDatabase.getInstance(applicationContext) }

    override suspend fun doWork(): Result = withContext(Dispatchers.IO) {
        try {
            val cutoff = TimeUtils.nowMillis() - RETENTION_DAYS * 24 * 60 * 60 * 1_000L
            val deletedEvents = db.appUsageEventDao().deleteOlderThan(cutoff)
            val deletedSessions = db.sessionDao().deleteOlderThan(cutoff)
            val deletedLogs = db.wellnessLogDao().deleteOlderThan(cutoff)
            Log.d(
                "CleanupWorker",
                "Pruned: $deletedEvents events, $deletedSessions sessions, $deletedLogs logs"
            )
            Result.success()
        } catch (e: Exception) {
            Log.e("CleanupWorker", "Cleanup failed", e)
            Result.failure()
        }
    }
}
