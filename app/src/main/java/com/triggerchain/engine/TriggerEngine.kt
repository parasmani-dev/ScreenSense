package com.triggerchain.engine

import android.content.Context
import com.triggerchain.data.model.LoopFingerprint
import com.triggerchain.data.model.UserProfile
import com.triggerchain.data.model.WellnessLog
import com.triggerchain.data.repository.TriggerChainRepository
import com.triggerchain.scoring.SleepThreatCalculator
import com.triggerchain.scoring.SleepThreatLevel
import com.triggerchain.service.TriggerAccessibilityService
import com.triggerchain.util.TimeUtils
import com.triggerchain.worker.CleanupWorker
import com.triggerchain.worker.LoopMinerWorker
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*

// ─────────────────────────────────────────
//  Public event model
// ─────────────────────────────────────────

/**
 * Emitted whenever a high-severity behavioural loop is detected in real time.
 * Dev 2 should collect [TriggerEngine.triggerEvents] to drive notification logic.
 *
 * Medical Safety: uses wellness-safe terminology throughout.
 */
data class TriggerEvent(
    val fingerprint: LoopFingerprint,
    val detectedAt: Long = TimeUtils.nowMillis(),
    /** Human-readable severity label — never uses diagnostic language. */
    val severityLabel: String = when {
        fingerprint.severityScore >= 0.8f -> "High Wellness Strain Risk"
        fingerprint.severityScore >= 0.6f -> "Moderate Focus Fatigue Pattern"
        else -> "Elevated Engagement Loop Detected"
    },
    /** The first app in the chain — the Gateway App for this loop. */
    val gatewayApp: String = fingerprint.appNames.firstOrNull() ?: "Unknown"
)

// ─────────────────────────────────────────
//  TriggerEngine — the singleton API surface
// ─────────────────────────────────────────

/**
 * # TriggerEngine
 *
 * The single interface point for Dev 2 (UI layer) to access all behavioural data.
 *
 * ## Usage
 * ```kotlin
 * // In Application.onCreate():
 * TriggerEngine.init(applicationContext)
 *
 * // In ViewModel:
 * TriggerEngine.sleepScore.collect { score -> updateUI(score) }
 * TriggerEngine.triggerEvents.collect { event -> showNotification(event) }
 * TriggerEngine.getSwitchVelocityData()   // for Switch Velocity graph
 * TriggerEngine.getRankedLoops()          // for Loop Frequency graph
 * TriggerEngine.getSleepMoodCorrelation() // for Wellness Correlation graph
 * ```
 */
object TriggerEngine {

    private lateinit var appContext: Context
    private lateinit var repository: TriggerChainRepository
    private val engineScope = CoroutineScope(SupervisorJob() + Dispatchers.Default)

    // ─────────────────────────────────────────
    //  Internal hot channels
    // ─────────────────────────────────────────

    private val _triggerEvents = MutableSharedFlow<TriggerEvent>(
        replay = 0,
        extraBufferCapacity = 32
    )

    private val _sleepScore = MutableStateFlow(0)

    private val _currentLevel = MutableStateFlow(SleepThreatLevel.CALM)

    // ─────────────────────────────────────────
    //  Public observable surfaces for Dev 2
    // ─────────────────────────────────────────

    /**
     * **Dev 2 — Observe this** to receive real-time Sleep Threat Score (0-100).
     * Score is recalculated every 5 minutes while the screen is active.
     */
    val sleepScore: StateFlow<Int> = _sleepScore.asStateFlow()

    /**
     * **Dev 2 — Observe this** for display-ready threat level labels.
     * Uses wellness-safe classification (never clinical terminology).
     */
    val sleepThreatLevel: StateFlow<SleepThreatLevel> = _currentLevel.asStateFlow()

    /**
     * **Dev 2 — Collect this** to fire notifications when a loop is detected.
     * Only emits when severity > 0.5 (enforced in AccessibilityService).
     */
    val triggerEvents: SharedFlow<TriggerEvent> = _triggerEvents.asSharedFlow()

    /**
     * Live stream of the rolling 5-app window from AccessibilityService.
     * Dev 2 can display this as a live "current chain" indicator.
     */
    val liveAppChain: SharedFlow<List<String>> =
        TriggerAccessibilityService.liveAppBuffer

    /**
     * All fingerprinted loops, updated in real-time from the database.
     */
    val allLoops: Flow<List<LoopFingerprint>>
        get() = repository.observeLoopFingerprints()

    /**
     * Real-time stream of recent wellness logs.
     */
    val wellnessLogs
        get() = repository.observeRecentWellnessLogs()

    /**
     * Real-time UserProfile stream.
     */
    val userProfile
        get() = repository.observeUserProfile()

    // ─────────────────────────────────────────
    //  Initialisation
    // ─────────────────────────────────────────

    /**
     * **Must be called from Application.onCreate()** before any other access.
     * Starts background workers and the sleep score polling loop.
     */
    fun init(context: Context) {
        appContext = context.applicationContext
        repository = TriggerChainRepository(appContext)

        // Schedule background workers
        LoopMinerWorker.schedule(appContext)
        CleanupWorker.schedule(appContext)

        // Start the 5-minute score polling loop
        startSleepScorePolling()
    }

    // ─────────────────────────────────────────
    //  Sleep Threat Score polling
    // ─────────────────────────────────────────

    private fun startSleepScorePolling() {
        engineScope.launch {
            while (isActive) {
                computeAndPersistScore()
                delay(TimeUtils.SCORE_INTERVAL_MS)
            }
        }
    }

    private suspend fun computeAndPersistScore() {
        val profile = repository.getUserProfile() ?: defaultProfile()
        val score = SleepThreatCalculator.compute(appContext, profile)
        val level = SleepThreatCalculator.classify(score)

        _sleepScore.value = score
        _currentLevel.value = level

        // Persist to WellnessLog
        repository.insertWellnessLog(
            WellnessLog(
                timestamp = TimeUtils.nowMillis(),
                sleepScore = score,
                moodScore = 3,              // default neutral; user overrides via Dev 2 UI
                isPotentialStrainTrigger = SleepThreatCalculator.isPotentialStrainTrigger(score)
            )
        )
    }

    // ─────────────────────────────────────────
    //  Internal: called by AccessibilityService
    // ─────────────────────────────────────────

    internal suspend fun emitLoopDetected(fingerprint: LoopFingerprint) {
        SleepThreatCalculator.setActiveLoop(fingerprint.severityScore)
        _triggerEvents.emit(TriggerEvent(fingerprint))
    }

    // ─────────────────────────────────────────
    //  Graph data exports — called by Dev 2
    // ─────────────────────────────────────────

    /**
     * **Graph Export 1** — Switch Velocity.
     * Returns `List<Pair<Hour (0-23), SwitchCount>>` for bar/line chart.
     */
    suspend fun getSwitchVelocityData(
        since: Long = TimeUtils.sevenDaysAgoMillis()
    ): List<Pair<Int, Int>> = repository.getSwitchVelocityData(since)

    /**
     * **Graph Export 2** — Ranked Loops.
     * Returns `List<LoopFingerprint>` sorted by highest frequency, then severity.
     */
    suspend fun getRankedLoops(): List<LoopFingerprint> =
        repository.getRankedFingerprints()

    /**
     * **Graph Export 3** — Sleep/Mood Correlation.
     * Returns `List<Pair<SleepScore, MoodScore>>` for scatter plot.
     */
    suspend fun getSleepMoodCorrelation(
        since: Long = TimeUtils.sevenDaysAgoMillis()
    ): List<Pair<Int, Int>> = repository.getSleepMoodCorrelation(since)

    /**
     * **Gateway App** — the first app in the highest-severity detected loop.
     * Returns null if no loops have been fingerprinted yet.
     */
    suspend fun getGatewayApp(): String? = repository.getGatewayApp()

    // ─────────────────────────────────────────
    //  User profile / mood input
    // ─────────────────────────────────────────

    /** Dev 2 calls this when the user updates their profile settings. */
    suspend fun updateUserProfile(profile: UserProfile) =
        repository.upsertUserProfile(profile)

    /**
     * Dev 2 calls this when user submits a mood rating from the UI.
     * Updates the most-recent WellnessLog entry with the given mood score.
     */
    suspend fun recordMoodRating(moodScore: Int) {
        require(moodScore in 1..5) { "Mood score must be 1-5" }
        val score = _sleepScore.value
        repository.insertWellnessLog(
            WellnessLog(
                timestamp = TimeUtils.nowMillis(),
                sleepScore = score,
                moodScore = moodScore,
                isPotentialStrainTrigger = SleepThreatCalculator.isPotentialStrainTrigger(score)
            )
        )
    }

    /** Clears the active loop multiplier — call when user dismisses a loop alert. */
    fun dismissActiveLoop() = SleepThreatCalculator.clearActiveLoop()

    // ─────────────────────────────────────────
    //  Fallback profile
    // ─────────────────────────────────────────

    private fun defaultProfile() = UserProfile(
        ageGroup = com.triggerchain.data.model.AgeGroup.ADULT,
        profession = com.triggerchain.data.model.Profession.OTHER,
        sleepTargetTimeMinutes = 22 * 60   // 22:00 default
    )
}
