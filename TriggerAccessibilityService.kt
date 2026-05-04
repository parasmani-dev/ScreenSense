package com.triggerchain.service

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.AccessibilityServiceInfo
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import com.triggerchain.data.db.TriggerChainDatabase
import com.triggerchain.data.model.AppEventType
import com.triggerchain.data.model.AppUsageEvent
import com.triggerchain.engine.TriggerEngine
import com.triggerchain.util.AppFilter
import com.triggerchain.util.RollingBuffer
import com.triggerchain.util.TimeUtils
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.asSharedFlow

/**
 * Real-time sensor for app transitions.
 *
 * Responsibilities:
 *  1. Detect TYPE_WINDOW_STATE_CHANGED events.
 *  2. Filter system/launcher packages and duplicate consecutive events.
 *  3. Write AppUsageEvent records to Room on Dispatchers.IO (never Main).
 *  4. Maintain a 5-slot rolling buffer; emit to [_liveAppBuffer] on every push.
 *  5. Match the buffer against high-severity LoopFingerprints; emit TriggerEvents.
 */
class TriggerAccessibilityService : AccessibilityService() {

    companion object {
        private const val TAG = "TriggerA11yService"
        private const val BUFFER_SIZE = 5

        /** Exposed so TriggerEngine can observe live app transitions. */
        private val _liveAppBuffer = MutableSharedFlow<List<String>>(
            replay = 1,
            extraBufferCapacity = 64
        )
        val liveAppBuffer = _liveAppBuffer.asSharedFlow()
    }

    // ── Coroutine scope tied to service lifecycle ────────────────────────────
    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)

    // ── Rolling buffer: last 5 foreground packages ───────────────────────────
    private val rollingBuffer = RollingBuffer<String>(BUFFER_SIZE)

    // ── Dedup: ignore consecutive identical packages ─────────────────────────
    private var lastPackage: String? = null
    private var lastEventStartTime: Long = 0L
    private var lastOpenEventId: Long = -1L          // DB row id of the last "open" event

    // ── DB & fingerprints ────────────────────────────────────────────────────
    private lateinit var db: TriggerChainDatabase

    // ─────────────────────────────────────────
    //  Lifecycle
    // ─────────────────────────────────────────

    override fun onServiceConnected() {
        super.onServiceConnected()
        db = TriggerChainDatabase.getInstance(applicationContext)

        serviceInfo = AccessibilityServiceInfo().apply {
            eventTypes = AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED
            feedbackType = AccessibilityServiceInfo.FEEDBACK_GENERIC
            flags = AccessibilityServiceInfo.FLAG_REPORT_VIEW_IDS
            notificationTimeout = 100
        }

        Log.i(TAG, "TriggerAccessibilityService connected")
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
        Log.i(TAG, "TriggerAccessibilityService destroyed")
    }

    override fun onInterrupt() {
        Log.w(TAG, "TriggerAccessibilityService interrupted")
    }

    // ─────────────────────────────────────────
    //  Event ingestion
    // ─────────────────────────────────────────

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {
        if (event?.eventType != AccessibilityEvent.TYPE_WINDOW_STATE_CHANGED) return

        val pkg = event.packageName?.toString() ?: return
        if (AppFilter.shouldIgnore(pkg)) return
        if (pkg == lastPackage) return                  // duplicate consecutive — skip

        val now = TimeUtils.nowMillis()

        // Close the previous event record off-thread
        closeLastEvent(now)

        // Open a new event record
        lastPackage = pkg
        lastEventStartTime = now

        serviceScope.launch {
            val newId = db.appUsageEventDao().insert(
                AppUsageEvent(
                    packageName = pkg,
                    startTime = now,
                    endTime = 0L,             // still active
                    type = AppEventType.FOREGROUND
                )
            )
            lastOpenEventId = newId
        }

        // Push to rolling buffer and attempt loop matching
        rollingBuffer.push(pkg)
        val snapshot = rollingBuffer.snapshot()

        serviceScope.launch {
            _liveAppBuffer.emit(snapshot)
            matchLoopFingerprints(snapshot)
        }
    }

    // ─────────────────────────────────────────
    //  Private helpers
    // ─────────────────────────────────────────

    /** Stamps endTime on the previously-open AppUsageEvent row. */
    private fun closeLastEvent(now: Long) {
        val idToClose = lastOpenEventId
        if (idToClose < 0) return

        serviceScope.launch {
            // Partial update via a targeted query avoids loading the full entity
            db.appUsageEventDao().let { dao ->
                val existing = dao.activeEvents().firstOrNull { it.id == idToClose }
                if (existing != null) {
                    dao.update(existing.copy(endTime = now, type = AppEventType.BACKGROUND))
                }
            }
        }
        lastOpenEventId = -1L
    }

    /**
     * Checks all sub-sequences in the current buffer snapshot against
     * persisted high-severity LoopFingerprints.
     * If a match is found, delegates to [TriggerEngine] to emit a TriggerEvent.
     */
    private suspend fun matchLoopFingerprints(snapshot: List<String>) {
        if (snapshot.size < 2) return

        val highSeverity = db.loopFingerprintDao().highSeverityFingerprints()
        if (highSeverity.isEmpty()) return

        // Check all n-gram windows (length 2..snapshot.size) against fingerprints
        for (len in 2..snapshot.size) {
            val subSeq = snapshot.takeLast(len)
            val hash = com.triggerchain.util.Hasher.sequenceHash(subSeq)
            val match = highSeverity.firstOrNull { it.sequenceHash == hash }
            if (match != null) {
                TriggerEngine.emitLoopDetected(match)
                return   // one emit per buffer update is sufficient
            }
        }
    }
}
