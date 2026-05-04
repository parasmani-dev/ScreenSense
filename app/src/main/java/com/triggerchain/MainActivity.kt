package com.triggerchain

import android.content.SharedPreferences
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.material3.Surface
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.navigation.compose.NavHost
import androidx.navigation.compose.composable
import androidx.navigation.compose.rememberNavController
import com.triggerchain.notification.TriggerNotificationService
import com.triggerchain.notification.createNotificationChannels
import com.triggerchain.ui.dashboard.DashboardScreen
import com.triggerchain.ui.onboarding.OnboardingScreen
import com.triggerchain.ui.settings.SettingsScreen
import com.triggerchain.ui.theme.NightInk
import com.triggerchain.ui.theme.TriggerChainTheme

// ─── Nav routes ───────────────────────────────────────────────────────────────
object Routes {
    const val ONBOARDING = "onboarding"
    const val DASHBOARD  = "dashboard"
    const val SETTINGS   = "settings"
}

class MainActivity : ComponentActivity() {

    private val prefs: SharedPreferences by lazy {
        getSharedPreferences("tc_prefs", MODE_PRIVATE)
    }

    private val onboardingDone get() = prefs.getBoolean("onboarding_done", false)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        // Boot notification infrastructure
        createNotificationChannels(this)
        TriggerNotificationService.start(this)

        setContent {
            TriggerChainTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color    = NightInk
                ) {
                    val navController = rememberNavController()
                    val startDest = if (onboardingDone) Routes.DASHBOARD else Routes.ONBOARDING

                    NavHost(navController = navController, startDestination = startDest) {

                        composable(Routes.ONBOARDING) {
                            OnboardingScreen(
                                onComplete = {
                                    prefs.edit().putBoolean("onboarding_done", true).apply()
                                    navController.navigate(Routes.DASHBOARD) {
                                        popUpTo(Routes.ONBOARDING) { inclusive = true }
                                    }
                                }
                            )
                        }

                        composable(Routes.DASHBOARD) {
                            DashboardScreen(
                                onNavigateSettings = { navController.navigate(Routes.SETTINGS) }
                            )
                        }

                        composable(Routes.SETTINGS) {
                            SettingsScreen(
                                onBack = { navController.popBackStack() }
                            )
                        }
                    }
                }
            }
        }
    }
}
