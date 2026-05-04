package com.triggerchain.data.model

import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.TypeConverter
import androidx.room.TypeConverters
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken

// ─────────────────────────────────────────
//  TYPE CONVERTERS
// ─────────────────────────────────────────

class Converters {
    private val gson = Gson()

    @TypeConverter fun fromStringList(value: List<String>): String = gson.toJson(value)
    @TypeConverter fun toStringList(value: String): List<String> =
        gson.fromJson(value, object : TypeToken<List<String>>() {}.type) ?: emptyList()
}

// ─────────────────────────────────────────
//  ENUMS
// ─────────────────────────────────────────

enum class AppEventType { FOREGROUND, BACKGROUND }

enum class AgeGroup { TEEN, YOUNG_ADULT, ADULT, SENIOR }

enum class Profession { STUDENT, KNOWLEDGE_WORKER, CREATIVE, HEALTHCARE, OTHER }

// ─────────────────────────────────────────
//  ENTITIES
// ─────────────────────────────────────────

/**
 * Raw event emitted by the AccessibilityService for every foreground/background transition.
 */
@Entity(tableName = "app_usage_events")
@TypeConverters(Converters::class)
data class AppUsageEvent(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val packageName: String,
    val startTime: Long,          // epoch millis
    val endTime: Long,            // 0 while still active
    val type: AppEventType
)

/**
 * A contiguous usage period; built by the LoopMiner worker.
 * A session ends when the gap between events exceeds SESSION_GAP_MS (30 min).
 */
@Entity(tableName = "sessions")
@TypeConverters(Converters::class)
data class Session(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val startTime: Long,
    val endTime: Long,
    val appSequence: List<String>, // ordered package names
    val switchCount: Int
)

/**
 * A behavioural fingerprint for a repeating app chain.
 * sequenceHash is SHA-256( sorted app sequence ) so duplicates self-deduplicate.
 */
@Entity(tableName = "loop_fingerprints", indices = [androidx.room.Index("sequenceHash", unique = true)])
@TypeConverters(Converters::class)
data class LoopFingerprint(
    @PrimaryKey val sequenceHash: String,
    val appNames: List<String>,    // human-readable package list
    val occurrences: Int,
    /** 0.0–1.0; derived from frequency × avg gap time × session depth */
    val severityScore: Float,
    /** Average milliseconds between consecutive apps in the chain */
    val avgGapTime: Long,
    val lastSeen: Long = System.currentTimeMillis()
)

/**
 * Daily user self-report or inferred wellness entry.
 * Medical Safety: labels use wellness-safe terminology throughout.
 */
@Entity(tableName = "wellness_logs")
data class WellnessLog(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val timestamp: Long,
    /** Computed Sleep Threat Score 0-100 (100 = highest threat) */
    val sleepScore: Int,
    /** Self-reported mood 1 (low) – 5 (high) */
    val moodScore: Int,
    /** User-flagged Potential Strain Trigger (never labelled "migraine cause") */
    val isPotentialStrainTrigger: Boolean
)

/**
 * Persisted once; updated when user changes profile.
 */
@Entity(tableName = "user_profile")
data class UserProfile(
    @PrimaryKey val id: Int = 1,                // singleton row
    val ageGroup: AgeGroup,
    val profession: Profession,
    /** Desired sleep time as minutes past midnight (e.g. 22 * 60 = 22:00) */
    val sleepTargetTimeMinutes: Int
)
