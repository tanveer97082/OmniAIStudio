package com.omniai.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.omniai.studio.projects.AiMultiFileSplitter
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.viewmodel.OmniAiViewModel

/**
 * Chat-to-Project Bridge Dialog:
 * Parses `### FILE: ...` or fenced code blocks from any AI chat response, lets the user
 * pick a target Project (and optionally customize the relative path for a single snippet),
 * writes the files directly into `filesDir/projects/{slug}/`, and triggers background
 * Git Auto-Push if `autoPushOnSave` is enabled on the chosen project.
 */
@Composable
fun SaveToProjectDialog(
    rawAiMessage: String,
    viewModel: OmniAiViewModel,
    onDismiss: () -> Unit
) {
    val projects by viewModel.projects.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val parsedFiles = remember(rawAiMessage) { AiMultiFileSplitter.parseMultiFileOutput(rawAiMessage) }

    var selectedProjectId by remember(activeProject, projects) {
        mutableStateOf(activeProject?.id ?: projects.firstOrNull()?.id ?: -1L)
    }
    val selectedPathMap = remember(parsedFiles) {
        mutableStateMapOf<String, Boolean>().apply {
            parsedFiles.forEach { put(it.relativePath, true) }
        }
    }
    var singleFilePathOverride by remember(parsedFiles) {
        mutableStateOf(if (parsedFiles.size == 1) parsedFiles.first().relativePath else "")
    }

    val chosenProject = projects.firstOrNull { it.id == selectedProjectId } ?: projects.firstOrNull()

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.FolderSpecial, contentDescription = null, tint = EmeraldAccent)
                Spacer(modifier = Modifier.width(8.dp))
                Text("Save AI Output to Project")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.fillMaxWidth()) {
                if (projects.isEmpty()) {
                    Text(
                        "No projects exist yet. Open the Projects tab to create a project workspace first.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                } else {
                    Text("Target Project Workspace:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 120.dp),
                        verticalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        items(projects, key = { it.id }) { proj ->
                            FilterChip(
                                selected = proj.id == chosenProject?.id,
                                onClick = { selectedProjectId = proj.id },
                                label = {
                                    Text(
                                        text = "${proj.name} (${proj.slug})" +
                                            if (proj.autoPushOnSave) " · Auto-Push ON" else "",
                                        maxLines = 1
                                    )
                                },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (parsedFiles.size == 1) {
                        OutlinedTextField(
                            value = singleFilePathOverride,
                            onValueChange = { singleFilePathOverride = it },
                            label = { Text("Relative File Path inside Project") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    Text(
                        text = "Detected Files (${parsedFiles.size}):",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldAccent
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 200.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(parsedFiles, key = { it.relativePath }) { item ->
                            val isChecked = selectedPathMap[item.relativePath] != false
                            Surface(
                                color = AmoledBlack,
                                border = BorderStroke(1.dp, AmoledBorder),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { selectedPathMap[item.relativePath] = !isChecked }
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Checkbox(
                                        checked = isChecked,
                                        onCheckedChange = { selectedPathMap[item.relativePath] = it }
                                    )
                                    Icon(Icons.Filled.Code, contentDescription = null, tint = EmeraldAccent)
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (parsedFiles.size == 1 && singleFilePathOverride.isNotBlank()) {
                                                singleFilePathOverride
                                            } else {
                                                item.relativePath
                                            },
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = "${item.content.lines().size} lines · ${item.content.length} chars",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = TextSecondary
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
                onClick = {
                    val target = chosenProject ?: return@Button
                    val filesToWrite = parsedFiles
                        .filter { selectedPathMap[it.relativePath] != false }
                        .map { item ->
                            if (parsedFiles.size == 1 && singleFilePathOverride.isNotBlank()) {
                                item.copy(relativePath = singleFilePathOverride.trim())
                            } else {
                                item
                            }
                        }
                    viewModel.applyMultiFileSplitterOutput(
                        project = target,
                        rawAiOutput = rawAiMessage,
                        overrideFiles = filesToWrite
                    ) {
                        onDismiss()
                    }
                },
                enabled = chosenProject != null && parsedFiles.any { selectedPathMap[it.relativePath] != false }
            ) {
                Text("Write to Project")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) {
                Text("Cancel")
            }
        }
    )
}
