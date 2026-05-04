package com.triggerchain.ui.settings

import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.triggerchain.data.model.*
import com.triggerchain.engine.TriggerEngine
import com.triggerchain.ui.components.*
import com.triggerchain.ui.theme.*
import kotlinx.coroutines.launch

class SettingsViewModel : ViewModel() {
    var notifIntensity by mutableStateOf(NotifIntensity.BALANCED)
    var sleepAlertsOn  by mutableStateOf(true)
    var loopAlertsOn   by mutableStateOf(true)
    var nudgesOn       by mutableStateOf(true)

    fun save() {
        viewModelScope.launch {
            // Persist to SharedPreferences in production
        }
    }
}

enum class NotifIntensity(val label: String, val desc: String) {
    QUIET(    "Quiet",    "Sleep alerts only"),
    BALANCED( "Balanced", "Loops + sleep (default)"),
    ACTIVE(   "Active",   "All channels — maximum coaching")
}

@Composable
fun SettingsScreen(
    onBack: () -> Unit,
    vm: SettingsViewModel = viewModel()
) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NightInk)
            .padding(24.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            IconButton(onClick = onBack) {
                Text("←", style = MaterialTheme.typography.titleLarge.copy(color = NeonCyan))
            }
            Text("Settings", style = MaterialTheme.typography.headlineMedium.copy(color = TextPrimary))
        }

        // ── Notification Intensity ─────────────────────────────────────────
        GlassCard(accentColor = PulsePurple) {
            Text("Notification Intensity",
                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            Text("How often TriggerChain reaches out to you",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            Spacer(Modifier.height(12.dp))
            NotifIntensity.values().forEach { level ->
                val selected = vm.notifIntensity == level
                Surface(
                    modifier  = Modifier.fillMaxWidth().clickable { vm.notifIntensity = level },
                    shape     = RoundedCornerShape(10.dp),
                    color     = if (selected) PulsePurple.copy(0.15f) else Color.Transparent,
                    border    = BorderStroke(1.dp, if (selected) PulsePurple.copy(0.5f) else BorderGlass)
                ) {
                    Row(
                        Modifier.padding(12.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment     = Alignment.CenterVertically
                    ) {
                        Column {
                            Text(level.label, style = MaterialTheme.typography.titleMedium.copy(
                                color = if (selected) PulsePurple else TextPrimary))
                            Text(level.desc,  style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
                        }
                        RadioButton(
                            selected = selected,
                            onClick  = { vm.notifIntensity = level },
                            colors   = RadioButtonDefaults.colors(selectedColor = PulsePurple)
                        )
                    }
                }
                Spacer(Modifier.height(8.dp))
            }
        }

        // ── Channel toggles ────────────────────────────────────────────────
        GlassCard(accentColor = NeonCyan) {
            Text("Channel Toggles",
                style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            Spacer(Modifier.height(8.dp))

            ToggleRow("Sleep Alerts",      "Fires when sleep threat score > 70", vm.sleepAlertsOn) { vm.sleepAlertsOn = it }
            Divider(color = BorderGlass.copy(0.4f))
            ToggleRow("Loop Interventions","Fires on behavioral chain detection", vm.loopAlertsOn) { vm.loopAlertsOn = it }
            Divider(color = BorderGlass.copy(0.4f))
            ToggleRow("Wellness Nudges",   "Low-priority tips every 2 hours",    vm.nudgesOn) { vm.nudgesOn = it }
        }

        // ── Engine status ──────────────────────────────────────────────────
        GlassCard(accentColor = CalmGreen) {
            Text("Engine", style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            Spacer(Modifier.height(8.dp))
            StatusRow("Loop Miner",       "Every 15 min",  CalmGreen)
            StatusRow("Usage Sync",       "Every 15 min",  CalmGreen)
            StatusRow("Sleep Calculator", "Every 5 min",   CalmGreen)
            StatusRow("Data Retention",   "30 days on-device", CalmGreen)
        }

        Button(
            onClick  = { vm.save(); onBack() },
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(14.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NightInk)
        ) {
            Text("Save Settings", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        }

        MedicalDisclaimer(Modifier.fillMaxWidth())
    }
}

@Composable
private fun ToggleRow(title: String, subtitle: String, checked: Boolean, onToggle: (Boolean) -> Unit) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 8.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column(modifier = Modifier.weight(1f)) {
            Text(title,    style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
            Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        }
        Switch(
            checked         = checked,
            onCheckedChange = onToggle,
            colors          = SwitchDefaults.colors(checkedThumbColor = NeonCyan, checkedTrackColor = NeonCyan.copy(0.3f))
        )
    }
}

@Composable
private fun StatusRow(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = color))
    }
}
