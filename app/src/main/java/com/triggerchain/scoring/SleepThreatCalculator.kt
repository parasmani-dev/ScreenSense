package com.triggerchain.scoring

import android.content.Context
import com.triggerchain.data.db.TriggerChainDatabase
import com.triggerchain.data.model.UserProfile
import com.triggerchain.data.model.WellnessLog
import com.triggerchain.util.TimeUtils
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

/**
 * SleepThreatCalculator — computes the Sleep Threat Score every 5 minutes.
 *
 * Formula (all terms clamped to produce S ∈ [0, 100]):
 *
 *   S_score = (minutesPastSleepTarget × W_time)
 *           + (switchesInLast30Min × W_switch)
 *           + (activeLoopMultiplier × W_loop)
 *
 * Weights are tuned to balance sensitivity; values are empirically derived.
 *
 * Medical Safety: all language uses wellness-safe terminology.
 *   - "Potential Strain Trigger" (not "migraine cause")
 *   - "Sleep Pressure" (not "sleep deprivation")
 *   - "Focus Fatigue Risk" (not "cognitive impairment")
 */
object SleepThreatCalculator {

    // ── Weights ──────────────────────────────────────────────────────────────
    private const val W_TIME = 0.5f         // 1 min past target → 0.5 pts
    private const val W_SWITCH = 1.2f       // each switch in last 30 min → 1.2 pts
    private const val W_LOOP = 15.0f        // active loop adds flat multiplier

    // ── Active loop state (updated by TriggerEngine) ─────────────────────────
    @Volatile private var activeLoopMultiplier: Float = 0f

    fun setActiveLoop(severity: Float) {
        activeLoopMultiplier = severity     // 0.0 – 1.0
    }

    fun clearActiveLoop() {
        activeLoopMultiplier = 0f
    }

    /**
     * Computes the current Sleep Threat Score.
     *
     * @param context          Application context
     * @param profile          UserProfile (for sleepTargetTimeMinutes)
     * @return                 Score in range [0, 100]
     */
    suspend fun compute(context: Context, profile: UserProfile): Int =
        withContext(Dispatchers.IO) {
            val db = TriggerChainDatabase.getInstance(context)
            val now = TimeUtils.nowMillis()

            // ── Term 1: Time pressure ─────────────────────────────────────────
            val currentMoM = TimeUtils.minutesPastMidnight()
            val target = profile.sleepTargetTimeMinutes

            // Handle midnight wrap-around (e.g., target 22:30, current 23:15 → 45 min late)
            val minutesLate = when {
                currentMoM >= target -> currentMoM - target
                currentMoM < (target - 12 * 60) -> (24 * 60 - target) + currentMoM  // past midnight
                else -> 0
            }.coerceAtLeast(0)

            val timeTerm = (minutesLate * W_TIME).coerceAtMost(40f)

            // ── Term 2: Switch velocity (last 30 min) ─────────────────────────
            val thirtyMinAgo = now - 30 * 60 * 1_000L
            val recentEvents = db.appUsageEventDao().eventsInRange(thirtyMinAgo, now)
            val switchCount = recentEvents
                .zipWithNext()
                .count { (a, b) -> a.packageName != b.packageName }

            val switchTerm = (switchCount * W_SWITCH).coerceAtMost(40f)

            // ── Term 3: Active loop multiplier ────────────────────────────────
            val loopTerm = (activeLoopMultiplier * W_LOOP).coerceAtMost(20f)

            // ── Aggregate ─────────────────────────────────────────────────────
            val raw = timeTerm + switchTerm + loopTerm
            raw.toInt().coerceIn(0, 100)
        }

    /**
     * Classifies score with wellness-safe labels for display in Dev 2's UI.
     * NEVER exposes clinical diagnostic language.
     */
    fun classify(score: Int): SleepThreatLevel = when {
        score < 25 -> SleepThreatLevel.CALM
        score < 50 -> SleepThreatLevel.ELEVATED
        score < 75 -> SleepThreatLevel.HIGH_PRESSURE
        else       -> SleepThreatLevel.CRITICAL_REST_NEEDED
    }

    /**
     * Determines whether the current conditions meet the threshold for flagging
     * a "Potential Strain Trigger" entry in [WellnessLog].
     * Threshold: score ≥ 75 AND an active loop was detected.
     */
    fun isPotentialStrainTrigger(score: Int): Boolean =
        score >= 75 && activeLoopMultiplier > 0f
}

/**
 * Wellness-safe score classification levels.
 * Labels intentionally avoid clinical or diagnostic terminology.
 */
enum class SleepThreatLevel(val label: String, val emoji: String) {
    CALM("Resting Well", "🌙"),
    ELEVATED("Sleep Pressure Rising", "⚠️"),
    HIGH_PRESSURE("High Focus Fatigue Risk", "🔴"),
    CRITICAL_REST_NEEDED("Rest Strongly Recommended", "🚨")
}
