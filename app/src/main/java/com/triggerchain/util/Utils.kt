package com.triggerchain.util

import java.security.MessageDigest
import java.util.LinkedList

// ─────────────────────────────────────────
//  On-device SHA-256 hashing
// ─────────────────────────────────────────

object Hasher {
    /**
     * Produces a deterministic SHA-256 hex string for a sequence of app package names.
     * Sorting ensures that A→B and B→A produce different hashes (order preserved)
     * while the same sequence always produces the same hash.
     */
    fun sequenceHash(sequence: List<String>): String {
        val input = sequence.joinToString("|")
        val digest = MessageDigest.getInstance("SHA-256")
        val bytes = digest.digest(input.toByteArray(Charsets.UTF_8))
        return bytes.joinToString("") { "%02x".format(it) }
    }
}

// ─────────────────────────────────────────
//  Fixed-size rolling buffer
// ─────────────────────────────────────────

/**
 * Thread-safe rolling buffer that keeps the last [capacity] elements.
 * Used by the Accessibility Service to maintain the live app sequence window.
 */
class RollingBuffer<T>(private val capacity: Int) {

    private val deque: ArrayDeque<T> = ArrayDeque(capacity)

    @Synchronized
    fun push(item: T) {
        if (deque.size >= capacity) deque.removeFirst()
        deque.addLast(item)
    }

    @Synchronized
    fun snapshot(): List<T> = deque.toList()

    @Synchronized
    fun size(): Int = deque.size

    @Synchronized
    fun clear() = deque.clear()
}

// ─────────────────────────────────────────
//  Time helpers
// ─────────────────────────────────────────

object TimeUtils {
    const val SESSION_GAP_MS = 30 * 60 * 1_000L       // 30 minutes
    const val SCORE_INTERVAL_MS = 5 * 60 * 1_000L      // 5 minutes
    const val WORKER_INTERVAL_MINUTES = 15L
    const val CLEANUP_INTERVAL_DAYS = 1L
    const val NGRAM_WINDOW_DAYS = 7
    const val MIN_OCCURRENCES = 4

    fun nowMillis(): Long = System.currentTimeMillis()

    fun minutesPastMidnight(): Int {
        val cal = java.util.Calendar.getInstance()
        return cal.get(java.util.Calendar.HOUR_OF_DAY) * 60 + cal.get(java.util.Calendar.MINUTE)
    }

    fun sevenDaysAgoMillis(): Long = nowMillis() - NGRAM_WINDOW_DAYS * 24 * 60 * 60 * 1_000L

    fun oneDayAgoMillis(): Long = nowMillis() - 24 * 60 * 60 * 1_000L
}

// ─────────────────────────────────────────
//  System app filter
// ─────────────────────────────────────────

object AppFilter {
    private val IGNORED_PACKAGES = setOf(
        "com.android.systemui",
        "com.android.launcher",
        "com.google.android.apps.nexuslauncher",
        "com.sec.android.app.launcher",
        "com.miui.home",
        "com.oneplus.launcher",
        "com.android.inputmethod.latin",
        "android",
        "com.android.settings",
        "com.android.phone"
    )

    fun shouldIgnore(packageName: String): Boolean =
        IGNORED_PACKAGES.any { packageName.startsWith(it) } ||
                packageName.contains("launcher", ignoreCase = true) ||
                packageName.contains("systemui", ignoreCase = true)
}
