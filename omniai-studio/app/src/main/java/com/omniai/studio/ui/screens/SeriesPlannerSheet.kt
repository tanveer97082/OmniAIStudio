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
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.data.local.SeriesEpisodeEntity
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary

/**
 * 30-Day Niche Series & Episode Planner Sheet/Dialog accessible from Tab 2 (Video Director).
 * Lets creators pick 7 / 15 / 30 episodes, generate an ordered non-repeating AI roadmap,
 * inspect PLANNED / SCRIPTED / RENDERED status, and load any episode via the Auto-Typer.
 */
@Composable
fun SeriesPlannerSheet(
    activeNiche: NicheEntity?,
    episodes: List<SeriesEpisodeEntity>,
    isGenerating: Boolean,
    onGenerateRoadmap: (episodeCount: Int, seriesTheme: String) -> Unit,
    onLoadEpisodeIntoDirector: (SeriesEpisodeEntity) -> Unit,
    onDismiss: () -> Unit
) {
    val primaryColor = MaterialTheme.colorScheme.primary
    var selectedCount by remember { mutableIntStateOf(15) }
    var seriesTheme by remember { mutableStateOf("") }
    val nextPlanned = remember(episodes) { episodes.firstOrNull { it.status == "PLANNED" } }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Column {
                Text("Niche Series & Episode Planner")
                Text(
                    activeNiche?.let { "${it.emoji} ${it.name} · ${episodes.size} planned episode(s)" }
                        ?: "Select a niche to plan episodes",
                    style = MaterialTheme.typography.labelMedium,
                    color = TextSecondary
                )
            }
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Length:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    listOf(7, 15, 30).forEach { count ->
                        FilterChip(
                            selected = selectedCount == count,
                            onClick = { selectedCount = count },
                            label = { Text("$count Eps") }
                        )
                    }
                }

                OutlinedTextField(
                    value = seriesTheme,
                    onValueChange = { seriesTheme = it },
                    label = { Text("Series Arc / Theme (Optional)") },
                    placeholder = { Text("e.g., 30 Mysteries of the Deep Ocean") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = { onGenerateRoadmap(selectedCount, seriesTheme) },
                        enabled = !isGenerating && activeNiche != null,
                        modifier = Modifier.weight(1f)
                    ) {
                        if (isGenerating) {
                            CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Planning...")
                        } else {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Generate $selectedCount-Ep Plan")
                        }
                    }

                    if (nextPlanned != null) {
                        OutlinedButton(
                            onClick = {
                                onLoadEpisodeIntoDirector(nextPlanned)
                                onDismiss()
                            }
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Load Next")
                        }
                    }
                }

                if (episodes.isEmpty()) {
                    Card(
                        colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                        border = BorderStroke(1.dp, AmoledBorder),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(
                            text = "No series roadmap generated yet for this niche. Pick 7, 15, or 30 episodes and tap Generate to build a non-repeating continuity arc.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary,
                            modifier = Modifier.padding(12.dp)
                        )
                    }
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .weight(1f, fill = false),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(episodes, key = { it.id }) { ep ->
                            val statusColor = when (ep.status) {
                                "RENDERED" -> EmeraldAccent
                                "SCRIPTED" -> primaryColor
                                else -> TextSecondary
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                                border = BorderStroke(
                                    1.dp,
                                    if (ep.id == nextPlanned?.id) primaryColor.copy(alpha = 0.7f) else AmoledBorder
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            text = "Ep #${ep.episodeNumber} · ${ep.title}",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.SemiBold,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Surface(
                                            shape = RoundedCornerShape(6.dp),
                                            color = statusColor.copy(alpha = 0.16f),
                                            border = BorderStroke(1.dp, statusColor.copy(alpha = 0.5f))
                                        ) {
                                            Text(
                                                text = ep.status,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = statusColor,
                                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                                            )
                                        }
                                    }
                                    Text(
                                        text = "Hook: ${ep.hookIdea}",
                                        style = MaterialTheme.typography.bodySmall
                                    )
                                    if (ep.continuityNote.isNotBlank()) {
                                        Text(
                                            text = "Continuity: ${ep.continuityNote}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(
                                            onClick = {
                                                onLoadEpisodeIntoDirector(ep)
                                                onDismiss()
                                            }
                                        ) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null, modifier = Modifier.size(15.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Auto-Type into Director")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) {
                Text("Close")
            }
        }
    )
}
