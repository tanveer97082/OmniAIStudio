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
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.remote.GitDiffResult
import com.omniai.studio.git.SecretScanReport
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.OmniAccentBadge
import com.omniai.studio.ui.theme.OmniDesignTokens
import com.omniai.studio.ui.theme.omniAccentBorder
import com.omniai.studio.ui.theme.omniAccentContainer

data class DiffPreviewFile(
    val relativePath: String,
    val changeType: String, // "ADDED", "MODIFIED", "DELETED"
    val previewLines: List<String> = emptyList()
)

/**
 * v5.0 Pre-Push Diff Review & Secret Leak Gate Dialog:
 * Displays added/modified/deleted files, expandable line-by-line hunks, and inline
 * SecretLeakScanner alerts that must be explicitly acknowledged before pushing to GitHub.
 */
@Composable
fun DiffReviewDialog(
    projectName: String,
    branchName: String,
    commitMessage: String,
    diff: GitDiffResult,
    previewFiles: List<DiffPreviewFile>,
    secretReport: SecretScanReport,
    isPushing: Boolean = false,
    onConfirmPush: () -> Unit,
    onDismiss: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    var acknowledgeSecretRisk by remember(secretReport) { mutableStateOf(false) }
    var expandedPath by remember { mutableStateOf<String?>(previewFiles.firstOrNull()?.relativePath) }

    val canPush = !isPushing && diff.hasChanges && (!secretReport.hasCriticalLeaks || acknowledgeSecretRisk)

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surfaceVariant,
        title = {
            Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                Text(
                    text = "Pre-Push Diff Review",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.Bold
                )
                Text(
                    text = "$projectName · branch '$branchName' · ${diff.totalPending} change(s)",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
            }
        },
        text = {
            Column(
                modifier = Modifier.fillMaxWidth(),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                // Summary Strip
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OmniAccentBadge(text = "+${diff.added.size} New", accent = EmeraldAccent)
                    OmniAccentBadge(text = "~${diff.modified.size} Mod", accent = AmberWarning)
                    OmniAccentBadge(text = "-${diff.deleted.size} Del", accent = CrimsonDanger)
                }

                // Commit message preview
                Surface(
                    color = colorScheme.surface,
                    border = BorderStroke(1.dp, colorScheme.outline),
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = commitMessage.ifBlank { "chore: atomic workspace push" },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurface,
                        modifier = Modifier.padding(10.dp)
                    )
                }

                // SecretLeakScanner Gate Banner
                if (secretReport.isClean) {
                    Surface(
                        color = EmeraldAccent.omniAccentContainer(),
                        border = BorderStroke(1.dp, EmeraldAccent.omniAccentBorder()),
                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(10.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Shield,
                                contentDescription = null,
                                tint = EmeraldAccent,
                                modifier = Modifier.size(18.dp)
                            )
                            Text(
                                text = "Secret Leak Scanner passed (${secretReport.scannedFileCount} file(s) clean).",
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldAccent
                            )
                        }
                    }
                } else {
                    Surface(
                        color = CrimsonDanger.omniAccentContainer(),
                        border = BorderStroke(1.dp, CrimsonDanger.omniAccentBorder()),
                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(
                            modifier = Modifier.padding(10.dp),
                            verticalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Warning,
                                    contentDescription = null,
                                    tint = CrimsonDanger,
                                    modifier = Modifier.size(18.dp)
                                )
                                Text(
                                    text = "${secretReport.findings.size} Potential Secret Leak(s) Detected",
                                    style = MaterialTheme.typography.labelMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = CrimsonDanger
                                )
                            }
                            secretReport.findings.take(4).forEach { finding ->
                                Text(
                                    text = "• ${finding.filePath}:${finding.lineNumber} [${finding.ruleName}] ${finding.maskedSnippet}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurface
                                )
                            }
                            if (secretReport.hasCriticalLeaks) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Checkbox(
                                        checked = acknowledgeSecretRisk,
                                        onCheckedChange = { acknowledgeSecretRisk = it }
                                    )
                                    Text(
                                        text = "I verified these are test placeholders and accept pushing anyway.",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurface
                                    )
                                }
                            }
                        }
                    }
                }

                // File Diff List
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 260.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(previewFiles, key = { "${it.changeType}:${it.relativePath}" }) { item ->
                        val badgeColor = when (item.changeType) {
                            "ADDED" -> EmeraldAccent
                            "DELETED" -> CrimsonDanger
                            else -> AmberWarning
                        }
                        val isExpanded = expandedPath == item.relativePath

                        Surface(
                            onClick = {
                                expandedPath = if (isExpanded) null else item.relativePath
                            },
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, colorScheme.outline),
                            shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = item.relativePath,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        color = badgeColor,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(6.dp))
                                    OmniAccentBadge(text = item.changeType, accent = badgeColor)
                                    Icon(
                                        imageVector = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = null,
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                if (isExpanded && item.previewLines.isNotEmpty()) {
                                    HorizontalDivider(
                                        color = colorScheme.outline,
                                        modifier = Modifier.padding(vertical = 6.dp)
                                    )
                                    item.previewLines.take(18).forEach { line ->
                                        val lineColor = when {
                                            line.startsWith("+") -> EmeraldAccent
                                            line.startsWith("-") -> CrimsonDanger
                                            else -> colorScheme.onSurfaceVariant
                                        }
                                        Text(
                                            text = line,
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = lineColor,
                                            maxLines = 2,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = onConfirmPush,
                enabled = canPush,
                colors = ButtonDefaults.buttonColors(
                    containerColor = if (secretReport.hasCriticalLeaks) AmberWarning else colorScheme.primary
                ),
                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
            ) {
                Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(modifier = Modifier.width(6.dp))
                Text(if (isPushing) "Pushing..." else "Confirm Push")
            }
        },
        dismissButton = {
            OutlinedButton(
                onClick = onDismiss,
                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
            ) {
                Text("Cancel")
            }
        }
    )
}
