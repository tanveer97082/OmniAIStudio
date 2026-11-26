package com.omniai.studio.ui.components

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Keyboard
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.AutoTypeSpeedMode
import com.omniai.studio.data.local.NextCommandEntity
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import kotlinx.coroutines.delay

/**
 * Reusable Auto-Typing Command Selector Bar for BOTH Tab 1 (Chat) and Tab 2 (Video Director).
 * - Displays clickable Next Command chips tagged by sourceType (Ep #N Sequel, From Chat, Deep Dive, Twist).
 * - Includes "Regenerate Ideas" action and "Auto-Type Mode" toggle.
 * - Uses cancellation-safe LaunchedEffect character-by-character typewriter animation with configurable speed,
 *   subtle haptic ticks, instant "Fill Now", and immediate cancel if the user edits manually or switches chips.
 * - Optional 5-second hands-free countdown bar with [Cancel / Edit] & [Run Now] when `autoNextCountdownEnabled` is true.
 */
@Composable
fun AutoTypePromptBar(
    title: String,
    commands: List<NextCommandEntity>,
    isRegenerating: Boolean,
    autoTypeEnabled: Boolean,
    speedMode: AutoTypeSpeedMode,
    autoNextCountdownEnabled: Boolean = false,
    externalManualEditToken: Int = 0,
    pendingExternalAutoTypeText: String? = null,
    onConsumeExternalAutoTypeText: (() -> Unit)? = null,
    onToggleAutoType: (Boolean) -> Unit,
    onRegenerate: () -> Unit,
    onPromptTextUpdate: (String) -> Unit,
    onCommandSelected: (NextCommandEntity) -> Unit,
    onRunNow: (() -> Unit)? = null,
    onRouteToVideoDirector: ((NextCommandEntity) -> Unit)? = null,
    modifier: Modifier = Modifier
) {
    val haptics = LocalHapticFeedback.current
    val primaryColor = MaterialTheme.colorScheme.primary
    var expanded by remember { mutableStateOf(true) }
    var typingTargetText by remember { mutableStateOf<String?>(null) }
    var typingSessionId by remember { mutableIntStateOf(0) }
    var isTypingActive by remember { mutableStateOf(false) }
    var countdownSeconds by remember { mutableIntStateOf(0) }
    var activeCountdownSession by remember { mutableIntStateOf(0) }

    // Cancel any active typewriter or countdown immediately if the user edits the TextField manually
    LaunchedEffect(externalManualEditToken) {
        if (externalManualEditToken > 0) {
            typingTargetText = null
            isTypingActive = false
            countdownSeconds = 0
        }
    }

    // Allow external callers (e.g., Series Planner "Load Next Episode") to trigger the typewriter cleanly
    LaunchedEffect(pendingExternalAutoTypeText) {
        val text = pendingExternalAutoTypeText
        if (!text.isNullOrBlank()) {
            onConsumeExternalAutoTypeText?.invoke()
            countdownSeconds = 0
            if (!autoTypeEnabled || speedMode == AutoTypeSpeedMode.INSTANT) {
                typingTargetText = null
                isTypingActive = false
                onPromptTextUpdate(text)
            } else {
                typingTargetText = text
                typingSessionId++
            }
        }
    }

    // Cancellation-safe character-by-character typewriter effect
    LaunchedEffect(typingSessionId, typingTargetText) {
        val target = typingTargetText ?: return@LaunchedEffect
        if (target.isBlank()) {
            isTypingActive = false
            return@LaunchedEffect
        }
        val stepDelay = resolveStepDelayMs(speedMode)
        if (!autoTypeEnabled || stepDelay <= 0L) {
            onPromptTextUpdate(target)
            isTypingActive = false
            typingTargetText = null
            if (autoNextCountdownEnabled && onRunNow != null) {
                countdownSeconds = 5
                activeCountdownSession++
            }
            return@LaunchedEffect
        }
        isTypingActive = true
        val stepChunk = if (target.length > 260) 2 else 1
        var cursor = 0
        while (cursor < target.length) {
            cursor = (cursor + stepChunk).coerceAtMost(target.length)
            onPromptTextUpdate(target.substring(0, cursor))
            if (cursor % 14 == 0) {
                haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
            }
            delay(stepDelay)
        }
        isTypingActive = false
        typingTargetText = null
        if (autoNextCountdownEnabled && onRunNow != null) {
            countdownSeconds = 5
            activeCountdownSession++
        }
    }

    // Optional 5-second hands-free countdown bar
    LaunchedEffect(activeCountdownSession) {
        if (countdownSeconds <= 0) return@LaunchedEffect
        while (countdownSeconds > 0) {
            delay(1000L)
            countdownSeconds--
            if (countdownSeconds == 0) {
                onRunNow?.invoke()
            }
        }
    }

    Card(
        modifier = modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(containerColor = AmoledSurface),
        border = BorderStroke(
            1.dp,
            if (isTypingActive || countdownSeconds > 0) primaryColor.copy(alpha = 0.8f) else AmoledBorder
        ),
        shape = RoundedCornerShape(12.dp)
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            // Header Row
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = Icons.Filled.AutoAwesome,
                        contentDescription = null,
                        tint = primaryColor,
                        modifier = Modifier.size(15.dp)
                    )
                    Text(
                        text = title,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = primaryColor,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(2.dp)
                ) {
                    Surface(
                        onClick = { onToggleAutoType(!autoTypeEnabled) },
                        shape = RoundedCornerShape(8.dp),
                        color = if (autoTypeEnabled) primaryColor.copy(alpha = 0.16f) else AmoledSurface,
                        border = BorderStroke(
                            1.dp,
                            if (autoTypeEnabled) primaryColor.copy(alpha = 0.7f) else AmoledBorder
                        )
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 7.dp, vertical = 3.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Keyboard,
                                contentDescription = "Toggle Auto-Type",
                                tint = if (autoTypeEnabled) primaryColor else TextSecondary,
                                modifier = Modifier.size(13.dp)
                            )
                            Text(
                                text = if (autoTypeEnabled) "Auto-Type" else "Instant",
                                style = MaterialTheme.typography.labelSmall,
                                color = if (autoTypeEnabled) primaryColor else TextSecondary
                            )
                        }
                    }

                    IconButton(
                        onClick = onRegenerate,
                        enabled = !isRegenerating,
                        modifier = Modifier.size(30.dp)
                    ) {
                        if (isRegenerating) {
                            CircularProgressIndicator(
                                modifier = Modifier.size(14.dp),
                                strokeWidth = 2.dp
                            )
                        } else {
                            Icon(
                                imageVector = Icons.Filled.Refresh,
                                contentDescription = "Regenerate Ideas",
                                tint = TextSecondary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }

                    IconButton(
                        onClick = { expanded = !expanded },
                        modifier = Modifier.size(28.dp)
                    ) {
                        Icon(
                            imageVector = if (expanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                            contentDescription = if (expanded) "Collapse" else "Expand",
                            tint = TextSecondary,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }
            }

            // Active Typewriter Status Bar with instant "Fill Now" & "Stop"
            AnimatedVisibility(visible = isTypingActive) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Auto-typing command (${speedMode.label})...",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldAccent
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        TextButton(
                            onClick = {
                                val remaining = typingTargetText
                                typingTargetText = null
                                isTypingActive = false
                                if (!remaining.isNullOrBlank()) {
                                    onPromptTextUpdate(remaining)
                                }
                            }
                        ) {
                            Icon(Icons.Filled.Bolt, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(modifier = Modifier.width(3.dp))
                            Text("Fill Now")
                        }
                        IconButton(
                            onClick = {
                                typingTargetText = null
                                isTypingActive = false
                            },
                            modifier = Modifier.size(28.dp)
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Stop Auto-Typing", modifier = Modifier.size(15.dp))
                        }
                    }
                }
            }

            // Optional 5-Second Hands-Free Countdown Bar
            AnimatedVisibility(visible = countdownSeconds > 0) {
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    LinearProgressIndicator(
                        progress = { countdownSeconds / 5f },
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(
                            text = "Auto-Next launching in ${countdownSeconds}s...",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldAccent,
                            fontWeight = FontWeight.SemiBold
                        )
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            OutlinedButton(onClick = { countdownSeconds = 0 }) {
                                Text("Cancel / Edit")
                            }
                            Button(
                                onClick = {
                                    countdownSeconds = 0
                                    onRunNow?.invoke()
                                }
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Run Now")
                            }
                        }
                    }
                }
            }

            // Collapsible Command Chips Row
            AnimatedVisibility(visible = expanded) {
                if (commands.isEmpty()) {
                    Text(
                        text = if (isRegenerating) {
                            "Generating niche-aware Auto-Next commands..."
                        } else {
                            "Tap Regenerate to generate context-aware next commands for this niche."
                        },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                } else {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        commands.take(6).forEach { cmd ->
                            val badge = sourceTypeBadge(cmd)
                            val isVideoHandoff = onRouteToVideoDirector != null &&
                                cmd.sourceType == "CHAT_FOLLOWUP" &&
                                cmd.shortLabel.contains("video", ignoreCase = true)

                            Surface(
                                onClick = {
                                    countdownSeconds = 0
                                    onCommandSelected(cmd)
                                    if (isVideoHandoff) {
                                        onRouteToVideoDirector?.invoke(cmd)
                                    } else if (!autoTypeEnabled || speedMode == AutoTypeSpeedMode.INSTANT) {
                                        typingTargetText = null
                                        isTypingActive = false
                                        onPromptTextUpdate(cmd.fullPromptCommand)
                                        if (autoNextCountdownEnabled && onRunNow != null) {
                                            countdownSeconds = 5
                                            activeCountdownSession++
                                        }
                                    } else {
                                        typingTargetText = cmd.fullPromptCommand
                                        typingSessionId++
                                    }
                                },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isVideoHandoff) EmeraldAccent.copy(alpha = 0.14f) else AmoledSurface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isVideoHandoff) EmeraldAccent.copy(alpha = 0.7f) else AmoledBorder
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                    verticalArrangement = Arrangement.spacedBy(2.dp)
                                ) {
                                    Text(
                                        text = badge,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (isVideoHandoff) EmeraldAccent else primaryColor,
                                        fontWeight = FontWeight.Bold
                                    )
                                    Text(
                                        text = cmd.shortLabel,
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

private fun resolveStepDelayMs(speedMode: AutoTypeSpeedMode): Long = when (speedMode.name) {
    "INSTANT" -> 0L
    "FAST" -> 10L
    "NORMAL", "BALANCED", "MEDIUM" -> 22L
    "SLOW", "CINEMATIC" -> 40L
    else -> 18L
}

private fun sourceTypeBadge(cmd: NextCommandEntity): String = when (cmd.sourceType) {
    "POST_VIDEO_SEQUEL" -> "Ep #${cmd.suggestedEpisodeNumber} Sequel"
    "CHAT_FOLLOWUP" -> "From Chat"
    "DEEP_DIVE" -> "Deep Dive"
    "VIRAL_TWIST" -> "Twist"
    else -> "Next Idea"
}
