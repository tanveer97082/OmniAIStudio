package com.omniai.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoFixHigh
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.Icon
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.remote.ScriptAuditResult
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary

/**
 * Pre-Render Viral Hook & Retention Auditor dialog inside Tab 2 (Video Director).
 * Displays Hook / Pacing / Visual Clarity / Seamless Loop scores (1-10),
 * actionable critiques, weak scene tags, and 1-tap "Auto-Optimize Weak Scenes".
 */
@Composable
fun ScriptAuditorDialog(
    auditResult: ScriptAuditResult?,
    isAuditing: Boolean,
    isOptimizing: Boolean,
    lockedCharacterName: String?,
    onAutoOptimizeWeakScenes: () -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Icon(Icons.Filled.Verified, contentDescription = null, tint = primaryColor)
                Text("Viral Hook & Retention Auditor")
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 460.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                if (isAuditing || auditResult == null) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.padding(vertical = 16.dp)
                    ) {
                        CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                        Text("Auditing hook strength, pacing, Wan 2.1 visual clarity & loop flow...")
                    }
                } else {
                    if (auditResult.summaryVerdict.isNotBlank()) {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                            border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.55f))
                        ) {
                            Text(
                                text = auditResult.summaryVerdict,
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.Medium,
                                modifier = Modifier.padding(10.dp)
                            )
                        }
                    }

                    AuditScoreRow("3-Sec Viral Hook", auditResult.hookScore)
                    AuditScoreRow("Retention Pacing", auditResult.pacingScore)
                    AuditScoreRow("Wan 2.1 Visual Clarity", auditResult.visualClarityScore)
                    AuditScoreRow("Seamless Loop Flow", auditResult.loopScore)

                    if (auditResult.weakSceneIndices.isNotEmpty()) {
                        Row(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Flagged Scenes:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            auditResult.weakSceneIndices.forEach { idx ->
                                Surface(
                                    shape = RoundedCornerShape(6.dp),
                                    color = MaterialTheme.colorScheme.error.copy(alpha = 0.16f),
                                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.error.copy(alpha = 0.5f))
                                ) {
                                    Text(
                                        text = "Scene #$idx",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = MaterialTheme.colorScheme.error,
                                        modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                    )
                                }
                            }
                        }
                    }

                    if (auditResult.critiques.isNotEmpty()) {
                        Text("Actionable Critiques", style = MaterialTheme.typography.labelLarge, color = primaryColor)
                        auditResult.critiques.forEachIndexed { idx, critique ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                                border = BorderStroke(1.dp, AmoledBorder)
                            ) {
                                Text(
                                    text = "${idx + 1}. $critique",
                                    style = MaterialTheme.typography.bodySmall,
                                    modifier = Modifier.padding(9.dp)
                                )
                            }
                        }
                    }

                    if (!lockedCharacterName.isNullOrBlank()) {
                        Text(
                            text = "Character DNA Lock Active: $lockedCharacterName will be preserved verbatim during auto-optimization.",
                            style = MaterialTheme.typography.labelSmall,
                            color = EmeraldAccent
                        )
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onAutoOptimizeWeakScenes,
                enabled = !isAuditing && !isOptimizing && auditResult != null
            ) {
                if (isOptimizing) {
                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Optimizing...")
                } else {
                    Icon(Icons.Filled.AutoFixHigh, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Auto-Optimize Weak Scenes")
                }
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}

@Composable
private fun AuditScoreRow(label: String, score: Int) {
    val clamped = score.coerceIn(1, 10)
    val accent = when {
        clamped >= 8 -> EmeraldAccent
        clamped >= 6 -> MaterialTheme.colorScheme.primary
        else -> MaterialTheme.colorScheme.error
    }
    Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            Text(label, style = MaterialTheme.typography.labelMedium)
            Text(
                text = "$clamped / 10",
                style = MaterialTheme.typography.labelMedium,
                color = accent,
                fontWeight = FontWeight.Bold
            )
        }
        LinearProgressIndicator(
            progress = { clamped / 10f },
            color = accent,
            modifier = Modifier.fillMaxWidth()
        )
    }
}
