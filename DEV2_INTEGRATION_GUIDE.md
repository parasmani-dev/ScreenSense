# TriggerChain — Dev 2 Integration Guide
## TriggerEngine API Reference

> **You are Dev 2.** This document is your complete contract for consuming the
> data pipeline built by Dev 1. You should **never** touch Room DAOs or
> Workers directly — use `TriggerEngine` exclusively.

---

## 1. One-Time Setup

The engine is already booted in `TriggerChainApp.onCreate()`. Nothing extra required.
Just ensure `android:name=".TriggerChainApp"` is set in `AndroidManifest.xml`.

---

## 2. Observable Flows (collect in your ViewModel)

### 2.1 — Sleep Threat Score (real-time, updates every 5 min)

```kotlin
// In ViewModel:
val score: StateFlow<Int> = TriggerEngine.sleepScore

// In Fragment/Composable:
viewModel.score.collect { score ->
    updateScoreRing(score)   // 0-100
}
```

### 2.2 — Sleep Threat Level (wellness-safe label)

```kotlin
TriggerEngine.sleepThreatLevel.collect { level ->
    // level.label  → "Resting Well" | "Sleep Pressure Rising" | etc.
    // level.emoji  → 🌙 | ⚠️ | 🔴 | 🚨
    showBadge(level.emoji, level.label)
}
```

### 2.3 — Loop Detected Events (drive your notification system)

```kotlin
// Collect in a service or notification manager:
TriggerEngine.triggerEvents.collect { event ->
    // event.fingerprint.appNames   → ["com.instagram.android", "com.twitter.android"]
    // event.severityLabel          → "High Wellness Strain Risk"  (wellness-safe)
    // event.gatewayApp             → "com.instagram.android"
    // event.detectedAt             → epoch millis
    NotificationHelper.show(event)
}
```

### 2.4 — Live App Chain (5-slot real-time window)

```kotlin
TriggerEngine.liveAppChain.collect { chain ->
    // chain = ["com.instagram.android", "com.youtube.android", ...]
    updateLiveChainWidget(chain)
}
```

### 2.5 — All Fingerprinted Loops

```kotlin
TriggerEngine.allLoops.collect { loops ->
    // loops: List<LoopFingerprint>, ordered by occurrences DESC
    loopAdapter.submitList(loops)
}
```

### 2.6 — Wellness Log Stream

```kotlin
TriggerEngine.wellnessLogs.collect { logs ->
    updateWellnessCard(logs.first())
}
```

---

## 3. One-Shot Graph Data (call from ViewModel with viewModelScope)

### 3.1 — Switch Velocity Graph (Bar/Line Chart by hour)

```kotlin
val switchData: List<Pair<Int, Int>> = TriggerEngine.getSwitchVelocityData()
// → [(0, 3), (1, 1), (9, 12), (22, 47), ...]
//    hour    count
```

### 3.2 — Loop Frequency Ranking

```kotlin
val loops: List<LoopFingerprint> = TriggerEngine.getRankedLoops()
// → sorted by occurrences DESC, then severityScore DESC
// loops[0].appNames     → ["com.instagram.android", "com.tiktok.com"]
// loops[0].severityScore → 0.87f
// loops[0].occurrences  → 23
```

### 3.3 — Sleep × Mood Correlation Scatter Plot

```kotlin
val correlation: List<Pair<Int, Int>> = TriggerEngine.getSleepMoodCorrelation()
// → [(78, 2), (34, 4), (91, 1), ...]
//    sleepScore  moodScore
```

### 3.4 — Gateway App

```kotlin
val gatewayApp: String? = TriggerEngine.getGatewayApp()
// → "com.instagram.android"  (entry point of highest-severity loop)
```

---

## 4. User Input Methods

```kotlin
// Update profile from Settings screen:
TriggerEngine.updateUserProfile(
    UserProfile(
        ageGroup = AgeGroup.YOUNG_ADULT,
        profession = Profession.STUDENT,
        sleepTargetTimeMinutes = 23 * 60   // 23:00
    )
)

// Record mood from daily check-in card (1-5 stars):
TriggerEngine.recordMoodRating(moodScore = 4)

// Dismiss active loop alert (clears multiplier):
TriggerEngine.dismissActiveLoop()
```

---

## 5. Data Models You'll Reference

### LoopFingerprint
| Field | Type | Description |
|-------|------|-------------|
| `sequenceHash` | `String` | Unique SHA-256 ID for the chain |
| `appNames` | `List<String>` | Ordered package names |
| `occurrences` | `Int` | Times seen in last 7 days |
| `severityScore` | `Float` | 0.0–1.0 (>0.5 = high-severity) |
| `avgGapTime` | `Long` | Avg ms between apps in chain |
| `lastSeen` | `Long` | Epoch millis of last occurrence |

### TriggerEvent
| Field | Type | Description |
|-------|------|-------------|
| `fingerprint` | `LoopFingerprint` | Full loop data |
| `detectedAt` | `Long` | Epoch millis |
| `severityLabel` | `String` | Wellness-safe display string |
| `gatewayApp` | `String` | First/entry app in the chain |

### SleepThreatLevel (enum)
| Value | Label | Emoji |
|-------|-------|-------|
| `CALM` | Resting Well | 🌙 |
| `ELEVATED` | Sleep Pressure Rising | ⚠️ |
| `HIGH_PRESSURE` | High Focus Fatigue Risk | 🔴 |
| `CRITICAL_REST_NEEDED` | Rest Strongly Recommended | 🚨 |

---

## 6. Medical Safety — Terminology Reference

All labels from this engine use **wellness-safe language**. Never override these
labels with clinical terms in the UI.

| ❌ Never use | ✅ Use instead |
|-------------|----------------|
| Migraine Cause | Potential Strain Trigger |
| Sleep Deprivation | Sleep Pressure |
| Cognitive Impairment | Focus Fatigue Risk |
| Addiction Pattern | Elevated Engagement Loop |
| Compulsive Behaviour | Behavioural Chain |

---

## 7. Architecture Overview

```
AccessibilityService  ──▶  RollingBuffer (5 slots)
        │                        │
        │                   SharedFlow  ──▶  Dev 2: liveAppChain
        │                        │
        │               LoopFingerprint   ──▶  Dev 2: triggerEvents
        │                  Matcher
        ▼
     Room DB  ◀──  UsageSyncWorker (15 min, UsageStatsManager)
        │
        ├──  LoopMinerWorker (15 min)
        │       Session Builder (30-min gap rule)
        │       N-Gram Extractor (n=2,3,4)
        │       Fingerprinter (≥4 occurrences/week)
        │
        └──  SleepThreatCalculator (every 5 min)
                Formula: timeTerm + switchTerm + loopMultiplier
                         → WellnessLog persisted to Room
                         → TriggerEngine.sleepScore StateFlow
```
