package com.triggerchain.ui.dashboard

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.triggerchain.data.model.LoopFingerprint
import com.triggerchain.scoring.SleepThreatLevel
import com.triggerchain.engine.TriggerEngine
import com.triggerchain.ui.components.*
import com.triggerchain.ui.theme.*
import com.triggerchain.ui.wellness.MoodCheckInDialog
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────────────
class DashboardViewModel : ViewModel() {

    val sleepScore = TriggerEngine.sleepScore

    val threatLevel = TriggerEngine.sleepThreatLevel
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), SleepThreatLevel.CALM)

    val liveChain = TriggerEngine.liveAppChain
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val allLoops = TriggerEngine.allLoops
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val wellnessLogs = TriggerEngine.wellnessLogs
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    var switchVelocity by mutableStateOf<List<Pair<Int, Int>>>(emptyList())
        private set

    var gatewayApp by mutableStateOf<String?>(null)
        private set

    var moodCorrelation by mutableStateOf<List<Pair<Int, Int>>>(emptyList())
        private set

    var showMoodDialog by mutableStateOf(false)

    var currentMood by mutableStateOf(0)

    init {
        viewModelScope.launch {
            switchVelocity  = TriggerEngine.getSwitchVelocityData()
            gatewayApp      = TriggerEngine.getGatewayApp()
            moodCorrelation = TriggerEngine.getSleepMoodCorrelation()
        }
    }

    fun submitMood(score: Int) {
        viewModelScope.launch {
            TriggerEngine.recordMoodRating(score)
            showMoodDialog = false
        }
    }

    fun dismissLoop() {
        viewModelScope.launch { TriggerEngine.dismissActiveLoop() }
    }
}

// ─── Dashboard host ───────────────────────────────────────────────────────────
@Composable
fun DashboardScreen(
    onNavigateSettings: () -> Unit,
    vm: DashboardViewModel = viewModel()
) {
    val score      by vm.sleepScore.collectAsState(initial = 0)
    val level      by vm.threatLevel.collectAsState()
    val chain      by vm.liveChain.collectAsState()
    val loops      by vm.allLoops.collectAsState()
    val logs       by vm.wellnessLogs.collectAsState()

    if (vm.showMoodDialog) {
        MoodCheckInDialog(
            current  = vm.currentMood,
            onPick   = { vm.currentMood = it },
            onSubmit = { vm.submitMood(vm.currentMood) },
            onDismiss = { vm.showMoodDialog = false }
        )
    }

    Box(Modifier.fillMaxSize().background(NightInk)) {
        LazyColumn(
            modifier                = Modifier.fillMaxSize(),
            contentPadding          = PaddingValues(bottom = 96.dp),
            verticalArrangement     = Arrangement.spacedBy(16.dp)
        ) {
            // Header
            item { DashHeader(score, level, onNavigateSettings) }

            // Sleep Threat Meter
            item { SleepSection(score, level) }

            // Live chain
            item { LiveChainSection(chain, vm.gatewayApp) }

            // Switch velocity
            item { VelocitySection(vm.switchVelocity) }

            // Loop map
            item { LoopMapSection(loops, vm.gatewayApp) }

            // Mood correlation
            if (vm.moodCorrelation.isNotEmpty()) {
                item { MoodCorrelationSection(vm.moodCorrelation) }
            }

            // Mood check-in button
            item {
                Box(Modifier.padding(horizontal = 16.dp)) {
                    OutlinedButton(
                        onClick = { vm.showMoodDialog = true },
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(14.dp),
                        border = BorderStroke(1.dp, PulsePurple.copy(alpha = 0.5f))
                    ) {
                        Text("How are you feeling? ★",
                            color = PulsePurple,
                            fontWeight = FontWeight.SemiBold,
                            modifier = Modifier.padding(vertical = 4.dp))
                    }
                }
            }

            item { MedicalDisclaimer(Modifier.fillMaxWidth()) }
        }
    }
}

// ─── Header ───────────────────────────────────────────────────────────────────
@Composable
private fun DashHeader(score: Int, level: SleepThreatLevel, onSettings: () -> Unit) {
    Row(
        Modifier
            .fillMaxWidth()
            .padding(horizontal = 20.dp, vertical = 16.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Column {
            Text("TriggerChain", style = MaterialTheme.typography.titleLarge.copy(
                color = TextPrimary, fontWeight = FontWeight.Black
            ))
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                Text(level.emoji)
                Text(level.label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
            }
        }
        IconButton(onClick = onSettings) {
            Text("⚙", style = MaterialTheme.typography.titleLarge.copy(color = TextSecondary))
        }
    }
}

// ─── Sleep threat section ─────────────────────────────────────────────────────
@Composable
private fun SleepSection(score: Int, level: SleepThreatLevel) {
    GlassCard(
        modifier     = Modifier.padding(horizontal = 16.dp),
        accentColor  = when (level) {
            SleepThreatLevel.CALM             -> CalmGreen
            SleepThreatLevel.ELEVATED         -> NeonCyan
            SleepThreatLevel.HIGH_PRESSURE    -> AlertAmber
            SleepThreatLevel.CRITICAL_REST_NEEDED -> DangerRed
        }
    ) {
        Text("Sleep Threat Meter",
            style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
        Spacer(Modifier.height(20.dp))
        Row(
            Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceEvenly,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            SleepThreatRing(score)
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                LegendRow("< 40", "Resting Well",           CalmGreen)
                LegendRow("40–70", "Pressure Rising",       AlertAmber)
                LegendRow("> 70", "Rest Recommended",       DangerRed)
            }
        }
        Spacer(Modifier.height(12.dp))
        Text(
            level.label,
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
        )
    }
}

@Composable
private fun LegendRow(range: String, label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
        Box(Modifier.size(8.dp).clip(CircleShape).background(color))
        Text("$range — $label", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
    }
}

// ─── Live chain section ───────────────────────────────────────────────────────
@Composable
private fun LiveChainSection(chain: List<String>, gateway: String?) {
    GlassCard(
        modifier    = Modifier.padding(horizontal = 16.dp),
        accentColor = NeonCyan
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text("Live App Chain", style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
            LiveBadge()
        }
        Spacer(Modifier.height(16.dp))
        if (chain.isEmpty()) {
            Text("Waiting for app switches…",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted))
        } else {
            ChainNodeRow(appNames = chain, gatewayApp = gateway)
        }
    }
}

@Composable
private fun LiveBadge() {
    val pulse = rememberInfiniteTransition(label = "livePulse")
    val alpha by pulse.animateFloat(0.4f, 1f,
        infiniteRepeatable(tween(800), RepeatMode.Reverse), "liveAlpha")
    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
        Box(Modifier.size(7.dp).clip(CircleShape).background(DangerRed.copy(alpha = alpha)))
        Text("LIVE", style = MaterialTheme.typography.labelSmall.copy(color = DangerRed, letterSpacing = 1.sp))
    }
}

// ─── Switch velocity section ──────────────────────────────────────────────────
@Composable
private fun VelocitySection(data: List<Pair<Int, Int>>) {
    GlassCard(
        modifier    = Modifier.padding(horizontal = 16.dp),
        accentColor = PulsePurple
    ) {
        Text("Switch Velocity — Last 24h",
            style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
        Text("App switches per hour",
            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
        Spacer(Modifier.height(16.dp))
        if (data.isEmpty()) {
            EmptyLoopState()
        } else {
            SwitchVelocityChart(data = data)
            Spacer(Modifier.height(4.dp))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                Text("12 AM", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                Text("12 PM", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
                Text("11 PM", style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
            }
        }
    }
}

// ─── Loop map section ─────────────────────────────────────────────────────────
@Composable
private fun LoopMapSection(loops: List<LoopFingerprint>, gateway: String?) {
    GlassCard(
        modifier    = Modifier.padding(horizontal = 16.dp),
        accentColor = AlertAmber
    ) {
        Text("Chain Map", style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
        Text("Detected behavioral loops (7-day window)",
            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
        Spacer(Modifier.height(16.dp))

        if (loops.isEmpty()) {
            EmptyLoopState()
        } else {
            loops.take(5).forEach { loop ->
                LoopCard(loop = loop, gateway = gateway)
                Spacer(Modifier.height(12.dp))
            }
        }
    }
}

@Composable
private fun LoopCard(loop: LoopFingerprint, gateway: String?) {
    Surface(
        shape  = RoundedCornerShape(12.dp),
        color  = SurfaceSlate,
        border = BorderStroke(1.dp, BorderGlass)
    ) {
        Column(Modifier.padding(14.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(
                Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment     = Alignment.CenterVertically
            ) {
                Text("${loop.occurrences}× this week",
                    style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
                SeverityBadge(loop.severityScore)
            }
            ChainNodeRow(appNames = loop.appNames, gatewayApp = gateway)
        }
    }
}

// ─── Mood correlation section ─────────────────────────────────────────────────
@Composable
private fun MoodCorrelationSection(data: List<Pair<Int, Int>>) {
    GlassCard(
        modifier    = Modifier.padding(horizontal = 16.dp),
        accentColor = CalmGreen
    ) {
        Text("Mood × Usage Correlation",
            style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))
        Text("Sleep threat score vs. logged mood (1–5)",
            style = MaterialTheme.typography.labelSmall.copy(color = TextMuted))
        Spacer(Modifier.height(16.dp))
        ScatterPlot(data = data, modifier = Modifier.fillMaxWidth().height(100.dp))
    }
}

@Composable
private fun ScatterPlot(data: List<Pair<Int, Int>>, modifier: Modifier = Modifier) {
    androidx.compose.foundation.Canvas(modifier = modifier) {
        data.forEach { (sleep, mood) ->
            val x = (sleep / 100f) * size.width
            val y = size.height - (mood / 5f) * size.height
            drawCircle(
                color  = PulsePurple.copy(alpha = 0.7f),
                radius = 6f,
                center = androidx.compose.ui.geometry.Offset(x, y)
            )
        }
    }
}
