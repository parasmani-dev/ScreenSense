package com.triggerchain.ui.onboarding

import android.content.Intent
import android.net.Uri
import android.provider.Settings
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.compose.viewModel
import com.triggerchain.data.model.*
import com.triggerchain.engine.TriggerEngine
import com.triggerchain.ui.components.*
import com.triggerchain.ui.theme.*
import kotlinx.coroutines.launch

// ─── ViewModel ────────────────────────────────────────────────────────────────
class OnboardingViewModel : ViewModel() {

    var step by mutableStateOf(0)
        private set

    // Profile fields
    var ageGroup     by mutableStateOf(AgeGroup.YOUNG_ADULT)
    var profession   by mutableStateOf(Profession.STUDENT)
    var sleepHour    by mutableStateOf(23)
    var sleepMinute  by mutableStateOf(0)

    // Permission flags (polled on resume)
    var hasUsageStats     by mutableStateOf(false)
    var hasAccessibility  by mutableStateOf(false)
    var hasNotifications  by mutableStateOf(false)

    fun next() { step = (step + 1).coerceAtMost(3) }
    fun back() { step = (step - 1).coerceAtLeast(0) }

    fun saveProfileAndContinue() {
        viewModelScope.launch {
            TriggerEngine.updateUserProfile(
                UserProfile(
                    ageGroup              = ageGroup,
                    profession            = profession,
                    sleepTargetTimeMinutes = sleepHour * 60 + sleepMinute
                )
            )
            next()
        }
    }

    fun refreshPermissions(
        usageOk: Boolean,
        a11yOk: Boolean,
        notifOk: Boolean
    ) {
        hasUsageStats    = usageOk
        hasAccessibility = a11yOk
        hasNotifications = notifOk
    }

    val allGranted get() = hasUsageStats && hasAccessibility && hasNotifications
}

// ─── Root onboarding host ─────────────────────────────────────────────────────
@Composable
fun OnboardingScreen(
    onComplete: () -> Unit,
    vm: OnboardingViewModel = viewModel()
) {
    AnimatedContent(
        targetState = vm.step,
        transitionSpec = {
            if (targetState > initialState)
                slideInHorizontally { it } + fadeIn() togetherWith slideOutHorizontally { -it } + fadeOut()
            else
                slideInHorizontally { -it } + fadeIn() togetherWith slideOutHorizontally { it } + fadeOut()
        },
        label = "page"
    ) { page ->
        when (page) {
            0 -> HookScreen(onNext = vm::next)
            1 -> ProfileScreen(vm = vm)
            2 -> PermissionScreen(vm = vm)
            3 -> StatusScreen(vm = vm, onComplete = onComplete)
        }
    }
}

// ─── Step 0: Hook ─────────────────────────────────────────────────────────────
@Composable
private fun HookScreen(onNext: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "hookPulse")
    val glow by pulse.animateFloat(
        0.3f, 1f,
        infiniteRepeatable(tween(2000, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "glow"
    )

    Box(
        Modifier
            .fillMaxSize()
            .background(
                Brush.radialGradient(
                    listOf(NeonCyan.copy(alpha = 0.08f * glow), NightInk),
                    radius = 800f
                )
            )
            .padding(32.dp),
        contentAlignment = Alignment.Center
    ) {
        Column(
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(24.dp)
        ) {
            // Logo
            Box(
                Modifier
                    .size(100.dp)
                    .clip(CircleShape)
                    .background(NeonCyan.copy(alpha = 0.1f))
                    .border(2.dp, NeonCyan.copy(alpha = glow), CircleShape),
                contentAlignment = Alignment.Center
            ) {
                Text("⛓", style = MaterialTheme.typography.displayMedium)
            }

            Text(
                "TriggerChain",
                style = MaterialTheme.typography.displayMedium.copy(
                    color      = TextPrimary,
                    fontWeight = FontWeight.Black
                ),
                textAlign = TextAlign.Center
            )

            Text(
                "Your phone has patterns.\nMost apps hide them.\nWe surface them.",
                style     = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary),
                textAlign = TextAlign.Center
            )

            Spacer(Modifier.height(16.dp))

            FeatureRow("🔁", "Identify behavioral loops before they spiral")
            FeatureRow("🌙", "Track how late-night usage affects sleep quality")
            FeatureRow("🧠", "Get nudges — not judgement")

            Spacer(Modifier.height(8.dp))

            Button(
                onClick = onNext,
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(16.dp),
                colors = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NightInk)
            ) {
                Text("Get Started", fontWeight = FontWeight.Bold, fontSize = 16.sp,
                    modifier = Modifier.padding(vertical = 6.dp))
            }

            MedicalDisclaimer()
        }
    }
}

@Composable
private fun FeatureRow(emoji: String, text: String) {
    Row(
        Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        Text(emoji, style = MaterialTheme.typography.titleLarge)
        Text(text, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
    }
}

// ─── Step 1: Profile ──────────────────────────────────────────────────────────
@Composable
private fun ProfileScreen(vm: OnboardingViewModel) {
    Column(
        Modifier
            .fillMaxSize()
            .background(NightInk)
            .padding(28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(20.dp)
    ) {
        StepHeader(2, "Tell us about yourself", "We personalise your wellness scores based on your lifestyle.")

        // Age group
        SectionLabel("Age Group")
        SegmentedPicker(
            options    = AgeGroup.values().map { it.displayName },
            selected   = vm.ageGroup.ordinal,
            onSelect   = { vm.ageGroup = AgeGroup.values()[it] }
        )

        // Profession
        SectionLabel("Profession")
        SegmentedPicker(
            options    = Profession.values().map { it.displayName },
            selected   = vm.profession.ordinal,
            onSelect   = { vm.profession = Profession.values()[it] }
        )

        // Sleep target time
        SectionLabel("Usual Bedtime")
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
            NumberPickerCard("Hour", vm.sleepHour, 0, 23, { vm.sleepHour = it }, Modifier.weight(1f))
            NumberPickerCard("Min",  vm.sleepMinute, 0, 55, onChange = { vm.sleepMinute = it }, modifier = Modifier.weight(1f), step = 5)
        }

        Spacer(Modifier.height(8.dp))

        Button(
            onClick  = vm::saveProfileAndContinue,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NightInk)
        ) {
            Text("Continue →", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun SectionLabel(text: String) {
    Text(text, style = MaterialTheme.typography.labelSmall.copy(
        color = TextSecondary, letterSpacing = 1.sp
    ))
}

@Composable
private fun SegmentedPicker(options: List<String>, selected: Int, onSelect: (Int) -> Unit) {
    Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        options.forEachIndexed { i, opt ->
            val active = i == selected
            Surface(
                modifier  = Modifier.weight(1f).clickable { onSelect(i) },
                shape     = RoundedCornerShape(10.dp),
                color     = if (active) NeonCyan else SurfaceSlate,
                border    = BorderStroke(1.dp, if (active) NeonCyan else BorderGlass)
            ) {
                Text(
                    opt,
                    style    = MaterialTheme.typography.labelSmall.copy(
                        color      = if (active) NightInk else TextSecondary,
                        fontWeight = if (active) FontWeight.Bold else FontWeight.Normal
                    ),
                    modifier  = Modifier.padding(10.dp),
                    textAlign = TextAlign.Center
                )
            }
        }
    }
}

@Composable
private fun NumberPickerCard(
    label: String, value: Int, min: Int, max: Int,
    onChange: (Int) -> Unit, modifier: Modifier = Modifier, step: Int = 1
) {
    GlassCard(modifier = modifier) {
        Text(label, style = MaterialTheme.typography.labelSmall.copy(color = TextSecondary))
        Spacer(Modifier.height(8.dp))
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = { if (value - step >= min) onChange(value - step) }) {
                Text("−", style = MaterialTheme.typography.titleLarge.copy(color = NeonCyan))
            }
            Text(
                value.toString().padStart(2, '0'),
                style = MaterialTheme.typography.headlineMedium.copy(color = TextPrimary, fontWeight = FontWeight.Bold)
            )
            IconButton(onClick = { if (value + step <= max) onChange(value + step) }) {
                Text("+", style = MaterialTheme.typography.titleLarge.copy(color = NeonCyan))
            }
        }
    }
}

// ─── Step 2: Permissions ──────────────────────────────────────────────────────
@Composable
private fun PermissionScreen(vm: OnboardingViewModel) {
    val ctx = LocalContext.current

    // Refresh permissions when screen is composed or regained
    LaunchedEffect(Unit) {
        // Poll on resume using real checks in production
        vm.refreshPermissions(
            usageOk = isUsageStatsGranted(ctx),
            a11yOk  = isAccessibilityGranted(ctx),
            notifOk = isNotificationGranted(ctx)
        )
    }

    Column(
        Modifier
            .fillMaxSize()
            .background(NightInk)
            .padding(28.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(16.dp)
    ) {
        StepHeader(3, "Grant Access", "TriggerChain needs these to work. All data stays on-device.")

        PermissionRow(
            title    = "Usage Statistics",
            subtitle = "Reads app switch history for loop detection",
            granted  = vm.hasUsageStats,
            onEnable = {
                ctx.startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            }
        )

        PermissionRow(
            title    = "Accessibility Service",
            subtitle = "Detects real-time app switches",
            granted  = vm.hasAccessibility,
            onEnable = {
                ctx.startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))
            }
        )

        PermissionRow(
            title    = "Notifications",
            subtitle = "Delivers wellness nudges and loop alerts",
            granted  = vm.hasNotifications,
            onEnable = {
                if (android.os.Build.VERSION.SDK_INT >= 33) {
                    ctx.startActivity(Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).apply {
                        putExtra(Settings.EXTRA_APP_PACKAGE, ctx.packageName)
                    })
                }
            }
        )

        Spacer(Modifier.height(8.dp))

        AnimatedVisibility(vm.allGranted) {
            Button(
                onClick  = vm::next,
                modifier = Modifier.fillMaxWidth(),
                shape    = RoundedCornerShape(16.dp),
                colors   = ButtonDefaults.buttonColors(containerColor = CalmGreen, contentColor = NightInk)
            ) {
                Text("All set — Let's go ✓", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
            }
        }

        if (!vm.allGranted) {
            TextButton(onClick = vm::next, modifier = Modifier.fillMaxWidth()) {
                Text("Skip for now (limited functionality)", color = TextMuted)
            }
        }
    }
}

private fun isUsageStatsGranted(ctx: android.content.Context): Boolean {
    val appOps = ctx.getSystemService(android.content.Context.APP_OPS_SERVICE) as android.app.AppOpsManager
    val mode = appOps.checkOpNoThrow(
        android.app.AppOpsManager.OPSTR_GET_USAGE_STATS,
        android.os.Process.myUid(), ctx.packageName
    )
    return mode == android.app.AppOpsManager.MODE_ALLOWED
}

private fun isAccessibilityGranted(ctx: android.content.Context): Boolean {
    val enabledServices = Settings.Secure.getString(ctx.contentResolver, Settings.Secure.ENABLED_ACCESSIBILITY_SERVICES)
    return enabledServices?.contains(ctx.packageName) == true
}

private fun isNotificationGranted(ctx: android.content.Context): Boolean {
    return if (android.os.Build.VERSION.SDK_INT >= 33) {
        ctx.checkSelfPermission(android.Manifest.permission.POST_NOTIFICATIONS) ==
                android.content.pm.PackageManager.PERMISSION_GRANTED
    } else true
}

// ─── Step 3: Status ───────────────────────────────────────────────────────────
@Composable
private fun StatusScreen(vm: OnboardingViewModel, onComplete: () -> Unit) {
    val pulse = rememberInfiniteTransition(label = "statusPulse")
    val ring by pulse.animateFloat(
        0.6f, 1f,
        infiniteRepeatable(tween(1500, easing = FastOutSlowInEasing), RepeatMode.Reverse),
        "ring"
    )

    Column(
        Modifier
            .fillMaxSize()
            .background(NightInk)
            .padding(32.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        Box(
            Modifier
                .size(120.dp)
                .clip(CircleShape)
                .background(CalmGreen.copy(alpha = 0.1f))
                .border(2.dp, CalmGreen.copy(alpha = ring), CircleShape),
            contentAlignment = Alignment.Center
        ) {
            Text("✓", style = MaterialTheme.typography.displayMedium.copy(color = CalmGreen))
        }

        Spacer(Modifier.height(28.dp))

        Text(
            "You're all set.",
            style     = MaterialTheme.typography.displayMedium.copy(color = TextPrimary, fontWeight = FontWeight.Black),
            textAlign = TextAlign.Center
        )
        Text(
            "TriggerChain is now learning your patterns.\nCheck back in a few hours.",
            style     = MaterialTheme.typography.bodyLarge.copy(color = TextSecondary),
            textAlign = TextAlign.Center
        )

        Spacer(Modifier.height(40.dp))

        StatusItem("Engine",           "Active", CalmGreen)
        StatusItem("Loop Miner",       "Scheduled — 15 min", CalmGreen)
        StatusItem("Sleep Monitoring", "Watching", CalmGreen)
        StatusItem("Notifications",    if (vm.hasNotifications) "Enabled" else "Limited", if (vm.hasNotifications) CalmGreen else AlertAmber)

        Spacer(Modifier.height(40.dp))

        Button(
            onClick  = onComplete,
            modifier = Modifier.fillMaxWidth(),
            shape    = RoundedCornerShape(16.dp),
            colors   = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NightInk)
        ) {
            Text("Open Dashboard →", fontWeight = FontWeight.Bold, modifier = Modifier.padding(vertical = 6.dp))
        }
    }
}

@Composable
private fun StatusItem(label: String, value: String, color: Color) {
    Row(
        Modifier.fillMaxWidth().padding(vertical = 6.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment     = Alignment.CenterVertically
    ) {
        Text(label, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
            Box(Modifier.size(6.dp).clip(CircleShape).background(color))
            Text(value, style = MaterialTheme.typography.bodyMedium.copy(color = color))
        }
    }
    Divider(color = BorderGlass.copy(alpha = 0.4f))
}

// ─── Shared step header ───────────────────────────────────────────────────────
@Composable
private fun StepHeader(step: Int, title: String, subtitle: String) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text("Step $step of 4", style = MaterialTheme.typography.labelSmall.copy(color = NeonCyan, letterSpacing = 1.sp))
        Text(title,    style = MaterialTheme.typography.headlineLarge.copy(color = TextPrimary))
        Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
    }
}

// ─── Display name extensions (add to data models if missing) ──────────────────
val AgeGroup.displayName: String get() = when (this) {
    AgeGroup.TEEN        -> "Teen"
    AgeGroup.YOUNG_ADULT -> "18–30"
    AgeGroup.ADULT       -> "31–50"
    AgeGroup.SENIOR      -> "50+"
}

val Profession.displayName: String get() = when (this) {
    Profession.STUDENT    -> "Student"
    Profession.EMPLOYED   -> "Employed"
    Profession.FREELANCER -> "Freelancer"
    Profession.OTHER      -> "Other"
}
