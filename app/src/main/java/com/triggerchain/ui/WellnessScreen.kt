package com.triggerchain.ui.wellness

import androidx.compose.animation.*
import androidx.compose.foundation.*
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.*
import androidx.compose.ui.graphics.*
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.*
import androidx.compose.ui.window.Dialog
import com.triggerchain.ui.components.*
import com.triggerchain.ui.theme.*

// ─── Break suggestions data ───────────────────────────────────────────────────
data class BreakIdea(val emoji: String, val title: String, val subtitle: String)

val breakIdeas = listOf(
    BreakIdea("🚶", "Walk",       "5-minute walk resets focus and reduces tension"),
    BreakIdea("💧", "Hydrate",    "Drink a full glass of water. Screen time dehydrates"),
    BreakIdea("📖", "Read",       "Physical page. No notifications. 10 minutes"),
    BreakIdea("🧘", "Breathe",    "4-7-8 breath: inhale 4s, hold 7s, exhale 8s"),
    BreakIdea("👁", "Eye Reset",  "20-20-20: look 20 ft away for 20 sec"),
    BreakIdea("✍️", "Journal",    "Write 3 sentences about your day so far"),
)

// ─── Break suggestions section ────────────────────────────────────────────────
@Composable
fun BreakIdeasSection(modifier: Modifier = Modifier) {
    Column(modifier = modifier) {
        Text(
            "Break Ideas",
            style = MaterialTheme.typography.titleLarge.copy(color = TextPrimary),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Text(
            "Loop detected — try one of these:",
            style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
            modifier = Modifier.padding(horizontal = 16.dp)
        )
        Spacer(Modifier.height(12.dp))

        breakIdeas.forEach { idea ->
            BreakCard(idea = idea, modifier = Modifier.padding(horizontal = 16.dp))
            Spacer(Modifier.height(8.dp))
        }
    }
}

@Composable
private fun BreakCard(idea: BreakIdea, modifier: Modifier = Modifier) {
    Surface(
        modifier  = modifier.fillMaxWidth(),
        shape     = RoundedCornerShape(14.dp),
        color     = SurfaceSlate,
        border    = BorderStroke(1.dp, BorderGlass)
    ) {
        Row(
            Modifier.padding(14.dp),
            verticalAlignment     = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            Text(idea.emoji, style = MaterialTheme.typography.headlineMedium)
            Column {
                Text(idea.title,    style = MaterialTheme.typography.titleMedium.copy(color = TextPrimary))
                Text(idea.subtitle, style = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary))
            }
        }
    }
}

// ─── Mood check-in dialog ─────────────────────────────────────────────────────
@Composable
fun MoodCheckInDialog(
    current:   Int,
    onPick:    (Int) -> Unit,
    onSubmit:  () -> Unit,
    onDismiss: () -> Unit
) {
    Dialog(onDismissRequest = onDismiss) {
        Surface(
            shape  = RoundedCornerShape(24.dp),
            color  = CardSurface,
            border = BorderStroke(1.dp, NeonCyan.copy(alpha = 0.3f)),
            tonalElevation = 8.dp
        ) {
            Column(
                Modifier.padding(28.dp),
                horizontalAlignment   = Alignment.CenterHorizontally,
                verticalArrangement   = Arrangement.spacedBy(16.dp)
            ) {
                Text("How are you feeling?",
                    style     = MaterialTheme.typography.headlineMedium.copy(color = TextPrimary),
                    textAlign = TextAlign.Center
                )
                Text("After that session — rate your current mood",
                    style     = MaterialTheme.typography.bodyMedium.copy(color = TextSecondary),
                    textAlign = TextAlign.Center
                )

                MoodStarPicker(current = current, onPick = onPick)

                val moodLabel = when (current) {
                    1 -> "Drained 😔"
                    2 -> "A bit low 😐"
                    3 -> "Okay 🙂"
                    4 -> "Good 😊"
                    5 -> "Great! 🌟"
                    else -> "Tap a star"
                }
                Text(moodLabel,
                    style = MaterialTheme.typography.titleMedium.copy(color = TextSecondary))

                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                    TextButton(onClick = onDismiss, modifier = Modifier.weight(1f)) {
                        Text("Skip", color = TextMuted)
                    }
                    Button(
                        onClick  = onSubmit,
                        enabled  = current > 0,
                        modifier = Modifier.weight(1f),
                        shape    = RoundedCornerShape(12.dp),
                        colors   = ButtonDefaults.buttonColors(containerColor = NeonCyan, contentColor = NightInk)
                    ) {
                        Text("Log", fontWeight = FontWeight.Bold)
                    }
                }

                MedicalDisclaimer()
            }
        }
    }
}
