package com.triggerchain.notification

import android.app.Notification
import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import androidx.core.app.NotificationCompat
import androidx.core.app.ServiceCompat
import com.triggerchain.MainActivity
import com.triggerchain.engine.TriggerEngine
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancel
import kotlinx.coroutines.flow.collectLatest
import kotlinx.coroutines.launch

/**
 * TriggerNotificationService — foreground service that listens to the TriggerEngine
 * and dispatches notifications via NotificationHelper.
 */
class TriggerNotificationService : Service() {

    private val serviceScope = CoroutineScope(SupervisorJob() + Dispatchers.Main)

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onCreate() {
        super.onCreate()
        
        // Start foreground immediately to comply with Android 14+ rules
        startAsForeground()

        // 1. Listen for Sleep Alerts
        serviceScope.launch {
            TriggerEngine.sleepThreatLevel.collectLatest { level ->
                NotificationHelper.showSleepAlert(this@TriggerNotificationService, TriggerEngine.sleepScore.value, level)
            }
        }

        // 2. Listen for Loop Detection Events
        serviceScope.launch {
            TriggerEngine.triggerEvents.collect { event ->
                NotificationHelper.showLoopAlert(this@TriggerNotificationService, event)
            }
        }
    }

    private fun startAsForeground() {
        val notification = NotificationCompat.Builder(this, Channels.WELLNESS_NUDGES)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle("ScreenSense Active")
            .setContentText("Monitoring behavioural patterns for wellness.")
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            ServiceCompat.startForeground(
                this,
                99,
                notification,
                if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
                    ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC
                } else {
                    0
                }
            )
        } else {
            startForeground(99, notification)
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        return START_STICKY
    }

    override fun onDestroy() {
        super.onDestroy()
        serviceScope.cancel()
    }

    companion object {
        fun start(context: Context) {
            val intent = Intent(context, TriggerNotificationService::class.java)
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                context.startForegroundService(intent)
            } else {
                context.startService(intent)
            }
        }
    }
}
