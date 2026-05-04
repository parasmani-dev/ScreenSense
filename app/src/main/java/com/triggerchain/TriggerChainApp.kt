package com.triggerchain

import android.app.Application
import com.triggerchain.engine.TriggerEngine
import com.triggerchain.worker.UsageSyncWorker

/**
 * TriggerChainApp — initialises the engine at startup.
 * Register this in AndroidManifest: android:name=".TriggerChainApp"
 */
class TriggerChainApp : Application() {

    override fun onCreate() {
        super.onCreate()

        // Boot the entire data pipeline — one line for Dev 2's integration.
        TriggerEngine.init(this)

        // Also start the UsageStats sync worker (separate from LoopMiner)
        UsageSyncWorker.schedule(this)
    }
}
