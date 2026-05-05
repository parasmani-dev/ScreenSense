# 📱 Screen Sense
**Intelligent Behavioral Intervention for Digital Wellness**

> Screen Sense moves beyond static screen-time tracking. By analyzing app usage sequences in real-time, it identifies dopamine loops, predicts digital fatigue, and delivers proactive wellness nudges — before migraines and eye strain take hold.

---

## 🏗️ System Pipeline

```
┌─────────────────────────────────────────────────────────┐
│                    LAYER 1 — SENSORS                    │
│                                                         │
│  UsageStatsManager          ObservationService          │
│  (Historical Analysis)  +   (Real-Time Monitoring)      │
│  Nightly batch jobs         AccessibilityService hook   │
└────────────────┬───────────────────┬────────────────────┘
                 │                   │
                 ▼                   ▼
┌─────────────────────────────────────────────────────────┐
│                  LAYER 2 — DATA PIPELINE                │
│                                                         │
│  Kotlin Coroutines + StateFlow (non-blocking reactive)  │
│  WorkManager (nightly N-Gram mining + session builder)  │
│                                                         │
│  Room SQLite ──► AppUsageEvents                         │
│   (on-device)  ──► LoopFingerprints                     │
│                ──► UserProfile                          │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│                LAYER 3 — SENSE ENGINE                   │
│                                                         │
│  ┌─────────────────┐     ┌──────────────────────────┐  │
│  │ N-Gram Analyzer │     │  Sleep Threat Scorer     │  │
│  │                 │     │                          │  │
│  │ • Gateway apps  │     │  Score: 0–100 (5min)     │  │
│  │ • Loop detect   │     │  Inputs:                 │  │
│  │   A→B→A chains  │     │  • Mins past sleep time  │  │
│  │ • Chain mining  │     │  • App-switch velocity   │  │
│  └────────┬────────┘     │  • Active loop flag      │  │
│           │              └─────────────┬────────────┘  │
│           └──────────────┬─────────────┘               │
└──────────────────────────┼─────────────────────────────┘
                           │
                           ▼
┌─────────────────────────────────────────────────────────┐
│             LAYER 4 — INTERVENTION MANAGER              │
│                                                         │
│  🔴 Sleep Alert      Score > threshold → High Priority  │
│  🔁 Loop Alert       Known chain triggered → Instant    │
│  💆 Wellness Nudge   Duration + profession → Suggest    │
│  📊 Stress Flag      Velocity spike → Context switch    │
└────────────────────────┬────────────────────────────────┘
                         │
                         ▼
┌─────────────────────────────────────────────────────────┐
│              LAYER 5 — VISUALIZATION                    │
│                                                         │
│  • Switching Velocity   Context-switch spike graphs     │
│  • Gateway Analysis     Trigger app heatmaps            │
│  • Mood Correlation     Loop → wellbeing tracking       │
│                                                         │
│  Stack: MPAndroidChart / Compose-Charts                 │
└─────────────────────────────────────────────────────────┘
```

---

## 🧠 Key Behaviors

### Behavioral Loop Detection
Tracks *sequences*, not just durations.

- **Gateway Apps** — detects apps (e.g. Slack) that consistently lead to unintended sessions (e.g. 30min Instagram scroll)
- **Loop Detection** — flags circular chains: `App A → App B → App A` using on-device N-Gram mining

### Predictive Sleep Threat Score
Dynamic 0–100 score, recomputed every 5 minutes during late-night hours.

| Input | Weight |
|---|---|
| Minutes past target sleep time | High |
| App-switching velocity | Medium |
| Active loop detected | Trigger |

Fires a **High Priority** alert before digital exhaustion sets in.

### Real-Time Intervention Engine
Built on `AccessibilityService` — monitors foreground transitions with zero perceptible lag.

---

## 📐 Component Responsibilities

| Component | Responsibility |
|---|---|
| `SenseEngine` | N-Gram mining, sequence matching, Sleep Threat scoring |
| `ObservationService` | Real-time app-switch detection at system level |
| `InterventionManager` | Multi-channel alerts: Sleep, Loop, Stress, Nudges |
| `Room Database` | On-device storage: `AppUsageEvents`, `LoopFingerprints`, `UserProfile` |
| `Visualization Layer` | Interactive charts via MPAndroidChart / Compose-Charts |

---

## ⚙️ Tech Stack

```
Language      Kotlin
Concurrency   Coroutines + StateFlow
Background    WorkManager
Database      Room SQLite
Monitoring    UsageStatsManager + AccessibilityService
Charts        MPAndroidChart / Compose-Charts
Storage       On-device only — zero cloud
```

---

## 📥 Setup

**Permissions required**
- Usage Access
- Accessibility Service
- Notifications

**Onboarding**
Enter profession (`Student` / `Professional`) and target sleep time → calibrates scoring engine.

---

## 🛡️ Privacy

| Principle | Detail |
|---|---|
| Zero-Cloud | All N-Gram mining, hashing, and scoring runs on-device |
| Minimal Data | Package names + timestamps only. No message content, passwords, or PII |
| Medical Disclaimer | Wellness support tool only — not a diagnostic tool for migraines or clinical illness |

---

## 👥 Team

## 👥 Team

| Member | GitHub |
|--------|--------|
| Nimish Sharma | (https://github.com/Nimish-Sharma-dev) |
| Parasmani Kushwaha | (https://github.com/parasmani-dev) |
| Manaswi Raj Dubey | ](https://github.com/manaswiraj) |
| Divyansh Singh | (https://github.com/Divyansh-Singh-1) |
