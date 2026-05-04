package com.triggerchain.ui.components

import androidx.compose.animation.*
import androidx.compose.animation.core.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.draw.*
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.*
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.*
import com.triggerchain.ui.theme.*
import kotlin.math.*

// ─── Sleep Threat Ring ────────────────────────────────────────────────────────
@Composable
fun SleepThreatRing(
    score: Int,
    modifier: Modifier = Modifier
) {
    val animatedScore by animateFloatAsState(
        targetValue = score.toFloat(),
        animationSpec = tween(1200, easing = FastOutSlowInEasing),
        label = "score"
    )

    val sweepAngle = (animatedScore / 100f) * 270f

    val ringColor = when {
        score < 40  -> CalmGreen
        score < 70  -> AlertAmber
        else        -> DangerRed
    }

    val pulseAnim = rememberInfiniteTransition(label = "pulse")
    val pulseScale by pulseAnim.animateFloat(
        initialValue = 1f,
        targetValue  = 1.04f,
        animationSpec = infiniteRepeatable(
            animation = tween(900, easing = FastOutSlowInEasing),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulseScale"
    )

    Box(
        modifier = modifier
            .size(180.dp)
            .scale(if (score > 70) pulseScale else 1f),
        contentAlignment = Alignment.Center
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            val strokeW = 16.dp.toPx()
            val inset   = strokeW / 2f
            val arcRect = androidx.compose.ui.geometry.Rect(inset, inset, size.width - inset, size.height - inset)

            // track
            drawArc(
                color       = BorderGlass,
                startAngle  = 135f,
                sweepAngle  = 270f,
                useCenter   = false,
                style       = Stroke(strokeW, cap = StrokeCap.Round),
                topLeft     = arcRect.topLeft,
                size        = arcRect.size
            )
            // fill
            if (sweepAngle > 0f) {
                drawArc(
                    brush       = Brush.sweepGradient(listOf(ringColor.copy(alpha = 0.6f), ringColor)),
                    startAngle  = 135f,
                    sweepAngle  = sweepAngle,
                    useCenter   = false,
                    style       = Stroke(strokeW, cap = StrokeCap.Round),
                    topLeft     = arcRect.topLeft,
                    size        = arcRect.size
                )
            }
        }

        Column(horizontalAlignment = Alignment.CenterHorizontally) {
            Text(
                text  = animatedScore.toInt().toString(),
                style = MaterialTheme.typography.displayMedium.copy(
                    color      = ringColor,
                    fontWeight = FontWeight.Black
                )
            )
            Text(
                text  = "/ 100",
                style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary)
            )
        }
    }
}

// ─── Glassy card ─────────────────────────────────────────────────────────────
@Composable
fun GlassCard(
    modifier: Modifier = Modifier,
    accentColor: Color = NeonCyan,
    content: @Composable ColumnScope.() -> Unit
) {
    Surface(
        modifier      = modifier,
        shape         = RoundedCornerShape(20.dp),
        color         = CardSurface.copy(alpha = 0.85f),
        border        = BorderStroke(1.dp, accentColor.copy(alpha = 0.25f)),
        tonalElevation = 2.dp,
        content       = { Column(modifier = Modifier.padding(20.dp), content = content) }
    )
}

// ─── App chain node row ───────────────────────────────────────────────────────
@Composable
fun ChainNodeRow(
    appNames: List<String>,
    gatewayApp: String?,
    modifier: Modifier = Modifier
) {
    Row(
        modifier            = modifier.fillMaxWidth(),
        verticalAlignment   = Alignment.CenterVertically,
        horizontalArrangement = Arrangement.Center
    ) {
        appNames.forEachIndexed { i, pkg ->
            val isGateway = pkg == gatewayApp
            val label = pkg.substringAfterLast(".").take(8)

            Box(
                contentAlignment = Alignment.Center,
                modifier = Modifier
                    .size(if (isGateway) 52.dp else 44.dp)
                    .clip(CircleShape)
                    .background(
                        if (isGateway) NeonCyan.copy(alpha = 0.15f) else SurfaceSlate
                    )
                    .border(
                        BorderStroke(
                            if (isGateway) 2.dp else 1.dp,
                            if (isGateway) NeonCyan else BorderGlass
                        ),
                        CircleShape
                    )
            ) {
                Text(
                    text  = label.take(2).uppercase(),
                    style = MaterialTheme.typography.labelSmall.copy(
                        color      = if (isGateway) NeonCyan else TextSecondary,
                        fontWeight = FontWeight.Bold
                    )
                )
            }

            if (i < appNames.lastIndex) {
                ChainArrow()
            }
        }
    }
}

@Composable
private fun ChainArrow() {
    val anim = rememberInfiniteTransition(label = "arrow")
    val offsetX by anim.animateFloat(
        initialValue = 0f,
        targetValue  = 4f,
        animationSpec = infiniteRepeatable(
            tween(700, easing = FastOutSlowInEasing),
            RepeatMode.Reverse
        ),
        label = "arrowX"
    )
    Box(
        modifier = Modifier
            .padding(horizontal = 4.dp)
            .offset(x = offsetX.dp),
        contentAlignment = Alignment.Center
    ) {
        Text("→", style = MaterialTheme.typography.bodyMedium.copy(color = NeonCyanDim))
    }
}

// ─── Switch velocity bar chart ────────────────────────────────────────────────
@Composable
fun SwitchVelocityChart(
    data: List<Pair<Int, Int>>,   // (hour, count)
    modifier: Modifier = Modifier
) {
    if (data.isEmpty()) return

    val maxCount = data.maxOf { it.second }.coerceAtLeast(1)

    val animProgress by animateFloatAsState(
        targetValue = 1f,
        animationSpec = tween(1000, easing = FastOutSlowInEasing),
        label = "bars"
    )

    Canvas(modifier = modifier
        .fillMaxWidth()
        .height(120.dp)
    ) {
        val barW   = size.width / 24f
        val padH   = 8.dp.toPx()
        val usableH = size.height - padH

        data.forEach { (hour, count) ->
            val barH  = (count.toFloat() / maxCount) * usableH * animProgress
            val x     = hour * barW
            val top   = size.height - barH

            val alpha = if (count > maxCount * 0.7f) 1f else 0.5f
            val color = when {
                count > maxCount * 0.7f -> DangerRed
                count > maxCount * 0.4f -> AlertAmber
                else                    -> NeonCyan
            }

            drawRoundRect(
                color        = color.copy(alpha = alpha),
                topLeft      = Offset(x + 2f, top),
                size         = androidx.compose.ui.geometry.Size(barW - 4f, barH),
                cornerRadius = androidx.compose.ui.geometry.CornerRadius(3f)
            )
        }
    }
}

// ─── Severity badge ───────────────────────────────────────────────────────────
@Composable
fun SeverityBadge(score: Float, modifier: Modifier = Modifier) {
    val (label, color) = when {
        score > 0.75f -> "HIGH" to DangerRed
        score > 0.5f  -> "MED"  to AlertAmber
        else          -> "LOW"  to CalmGreen
    }
    Surface(
        shape = RoundedCornerShape(6.dp),
        color = color.copy(alpha = 0.15f),
        modifier = modifier
    ) {
        Text(
            text     = label,
            style    = MaterialTheme.typography.labelSmall.copy(color = color, fontWeight = FontWeight.Bold),
            modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
        )
    }
}

// ─── Permission item row ──────────────────────────────────────────────────────
@Composable
fun PermissionRow(
    title: String,
    subtitle: String,
    granted: Boolean,
    onEnable: () -> Unit
) {
    GlassCard(accentColor = if (granted) CalmGreen else AlertAmber) {
        Row(
            modifier              = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment     = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(title, style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                Spacer(Modifier.height(2.dp))
                Text(subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            }
            Spacer(Modifier.width(12.dp))
            if (granted) {
                Text("✓ Active", style = MaterialTheme.typography.labelSmall.copy(color = CalmGreen))
            } else {
                Button(
                    onClick = onEnable,
                    shape   = RoundedCornerShape(10.dp),
                    colors  = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NightInk)
                ) { Text("Enable", fontWeight = FontWeight.Bold) }
            }
        }
    }
}

// ─── Mood star input ──────────────────────────────────────────────────────────
@Composable
fun MoodStarPicker(
    current: Int,
    onPick: (Int) -> Unit,
    modifier: Modifier = Modifier
) {
    Row(modifier = modifier, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
        (1..5).forEach { star ->
            val selected = star <= current
            Text(
                text     = if (selected) "★" else "☆",
                style    = MaterialTheme.typography.headlineLarge.copy(
                    color = if (selected) AlertAmber else TextMuted
                ),
                modifier = Modifier.clickable { onPick(star) }
            )
        }
    }
}

// ─── Disclaimer footer ────────────────────────────────────────────────────────
@Composable
fun MedicalDisclaimer(modifier: Modifier = Modifier) {
    Text(
        text     = "⚕ Not a medical diagnostic tool. Supports behavioral wellness only.",
        style    = MaterialTheme.typography.labelSmall.copy(color = TextMuted),
        modifier = modifier.padding(horizontal = 16.dp, vertical = 8.dp)
    )
}

// ─── Empty state ──────────────────────────────────────────────────────────────
@Composable
fun EmptyLoopState(modifier: Modifier = Modifier) {
    Column(
        modifier              = modifier.fillMaxWidth().padding(40.dp),
        horizontalAlignment   = Alignment.CenterHorizontally,
        verticalArrangement   = Arrangement.Center
    ) {
        val pulse = rememberInfiniteTransition(label = "emptyPulse")
        val alpha by pulse.animateFloat(
            initialValue = 0.4f, targetValue = 1f,
            animationSpec = infiniteRepeatable(tween(1500), RepeatMode.Reverse),
            label = "alpha"
        )
        Text("◎", style = MaterialTheme.typography.displayLarge.copy(
            color = NeonCyan.copy(alpha = alpha)
        ))
        Spacer(Modifier.height(16.dp))
        Text(
            "Collecting data to find your patterns…",
            style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary)
        )
        Text(
            "Check back after a few hours of normal phone use.",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextMuted)
        )
    }
}
