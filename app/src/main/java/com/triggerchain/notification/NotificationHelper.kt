package com.triggerchain.notification

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import com.triggerchain.MainActivity
import com.triggerchain.engine.TriggerEvent
import com.triggerchain.scoring.SleepThreatLevel

// ─── Channel IDs ──────────────────────────────────────────────────────────────
object Channels {
    const val SLEEP_ALERTS   = "tc_sleep_alerts"
    const val LOOP_ALERTS    = "tc_loop_alerts"
    const val WELLNESS_NUDGES = "tc_wellness_nudges"
}

// ─── Cooldown manager ─────────────────────────────────────────────────────────
object CooldownManager {
    private const val PREF_KEY    = "tc_cooldown"
    private const val COOLDOWN_MS = 30 * 60 * 1000L   // 30 min

    fun canSend(ctx: Context, channelId: String): Boolean {
        if (channelId == Channels.SLEEP_ALERTS) return true   // sleep bypasses cooldown

        val prefs    = ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
        val lastSent = prefs.getLong(channelId, 0L)
        return (System.currentTimeMillis() - lastSent) >= COOLDOWN_MS
    }

    fun markSent(ctx: Context, channelId: String) {
        ctx.getSharedPreferences(PREF_KEY, Context.MODE_PRIVATE)
            .edit().putLong(channelId, System.currentTimeMillis()).apply()
    }
}

// ─── Channel setup (call once in Application.onCreate) ───────────────────────
fun createNotificationChannels(ctx: Context) {
    if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return

    val nm = ctx.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    nm.createNotificationChannels(listOf(
        NotificationChannel(
            Channels.SLEEP_ALERTS,
            "Sleep Alerts",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description       = "Alerts when sleep threat score is elevated"
            enableVibration(true)
            enableLights(true)
            lightColor        = 0xFFFF5252.toInt()
        },
        NotificationChannel(
            Channels.LOOP_ALERTS,
            "Loop Interventions",
            NotificationManager.IMPORTANCE_HIGH
        ).apply {
            description = "Notifies when a behavioral chain is detected"
            enableVibration(true)
        },
        NotificationChannel(
            Channels.WELLNESS_NUDGES,
            "Wellness Nudges",
            NotificationManager.IMPORTANCE_LOW
        ).apply {
            description = "Low-priority tips and reminders"
        }
    ))
}

// ─── Notification helper ──────────────────────────────────────────────────────
object NotificationHelper {

    private var sleepNotifId = 1001
    private var loopNotifId  = 2001
    private var nudgeNotifId = 3001

    // Channel A: Sleep Alert
    fun showSleepAlert(ctx: Context, score: Int, level: SleepThreatLevel) {
        if (!CooldownManager.canSend(ctx, Channels.SLEEP_ALERTS)) return

        val (title, body) = when (level) {
            SleepThreatLevel.ELEVATED -> Pair(
                "Sleep Pressure Rising ⚠️",
                "Your screen activity is starting to build sleep pressure. Consider winding down soon."
            )
            SleepThreatLevel.HIGH_PRESSURE -> Pair(
                "High Focus Fatigue Risk 🔴",
                "Extended screen time is impacting your rest window. A short break can help reset."
            )
            SleepThreatLevel.CRITICAL_REST_NEEDED -> Pair(
                "Rest Strongly Recommended 🚨",
                "Your usage pattern suggests significant sleep pressure (score $score/100). Time to rest."
            )
            else -> return   // CALM — no notification needed
        }

        post(ctx, Channels.SLEEP_ALERTS, sleepNotifId, title, body, highPriority = true)
        CooldownManager.markSent(ctx, Channels.SLEEP_ALERTS)
    }

    // Channel B: Loop Intervention
    fun showLoopAlert(ctx: Context, event: TriggerEvent) {
        if (!CooldownManager.canSend(ctx, Channels.LOOP_ALERTS)) return

        val apps  = event.fingerprint.appNames
            .map { it.substringAfterLast(".").replaceFirstChar { c -> c.uppercase() } }
        val chain = apps.joinToString(" → ")

        val title = "Engagement Loop Detected"
        val body  = "You've been in a $chain loop. Time to stretch and take a break? 🧘"

        post(ctx, Channels.LOOP_ALERTS, loopNotifId, title, body, highPriority = true)
        CooldownManager.markSent(ctx, Channels.LOOP_ALERTS)
        loopNotifId++
    }

    // Channel C: Wellness Nudge
    fun showWellnessNudge(ctx: Context, profession: String?) {
        if (!CooldownManager.canSend(ctx, Channels.WELLNESS_NUDGES)) return

        val body = nudgeForProfession(profession)
        post(ctx, Channels.WELLNESS_NUDGES, nudgeNotifId, "Wellness Check 🌿", body, highPriority = false)
        CooldownManager.markSent(ctx, Channels.WELLNESS_NUDGES)
        nudgeNotifId++
    }

    private fun nudgeForProfession(profession: String?): String = when (profession) {
        "STUDENT"    -> "Student mode: Pomodoro time — 25 min focus, 5 min off. Your loops suggest a drift."
        "EMPLOYED"   -> "Work mode: You've been switching apps frequently. Deep work session?"
        "FREELANCER" -> "Freelancer check-in: Behavioural chains detected. Protect your focus block."
        else         -> "You've been on your phone a while. Quick walk? 🚶"
    }

    private fun post(
        ctx:         Context,
        channelId:   String,
        notifId:     Int,
        title:       String,
        body:        String,
        highPriority: Boolean
    ) {
        val tapIntent = PendingIntent.getActivity(
            ctx, 0,
            Intent(ctx, MainActivity::class.java).apply { flags = Intent.FLAG_ACTIVITY_SINGLE_TOP },
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )

        val notif = NotificationCompat.Builder(ctx, channelId)
            .setSmallIcon(android.R.drawable.ic_lock_idle_alarm) // replace with real icon
            .setContentTitle(title)
            .setContentText(body)
            .setStyle(NotificationCompat.BigTextStyle().bigText(body))
            .setPriority(if (highPriority) NotificationCompat.PRIORITY_HIGH else NotificationCompat.PRIORITY_LOW)
            .setAutoCancel(true)
            .setContentIntent(tapIntent)
            .build()

        try {
            NotificationManagerCompat.from(ctx).notify(notifId, notif)
        } catch (e: SecurityException) {
            // Notification permission not granted — silently skip
        }
    }
}
