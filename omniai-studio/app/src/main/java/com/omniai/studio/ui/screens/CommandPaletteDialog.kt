package com.omniai.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.omniai.studio.settings.StudioTabId
import com.omniai.studio.viewmodel.OmniAiViewModel

private data class PaletteActionItem(
    val id: String,
    val category: String,
    val title: String,
    val subtitle: String,
    val icon: ImageVector,
    val onExecute: () -> Unit
)

/**
 * Universal Command Palette (Spotlight / Ctrl+K modal) searching Settings items,
 * Saved Chat Threads, Saved Terminal Snippets, and Quick Actions.
 * Prefixing with `>` executes the command directly in the Hybrid Terminal.
 */
@Composable
fun CommandPaletteDialog(
    viewModel: OmniAiViewModel,
    onDismiss: () -> Unit,
    onNavigateTab: (StudioTabId) -> Unit,
    onOpenOverlay: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val threads by viewModel.chatThreads.collectAsState()
    val snippets by viewModel.terminalSnippets.collectAsState()
    val videoScripts by viewModel.videoScriptsWithRelations.collectAsState()
    val projects by viewModel.projects.collectAsState()
    var query by remember { mutableStateOf("") }

    val trimmed = query.trim()
    val isDirectTerminalExec = trimmed.startsWith(">")
    val directCommand = trimmed.removePrefix(">").trim()

    val quickActions = remember(onNavigateTab, onOpenOverlay, onDismiss) {
        listOf(
            PaletteActionItem(
                id = "act_home",
                category = "Quick Action",
                title = "Open Home Bento Dashboard",
                subtitle = "Hero Niche Next Episode, GPU Quota Ring, Active Project Push & Recent Activity",
                icon = Icons.Filled.Home,
                onExecute = { onOpenOverlay("NONE"); onNavigateTab(StudioTabId.HOME); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_projects",
                category = "Quick Action",
                title = "Open Projects & Git Auto-Push Hub",
                subtitle = "Kanban Board, Code Editor, AI ### FILE Splitter, Atomic Git Push & Actions Monitor",
                icon = Icons.Filled.FolderSpecial,
                onExecute = { onOpenOverlay("NONE"); onNavigateTab(StudioTabId.PROJECTS); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_content_hub",
                category = "Quick Action",
                title = "Open Content DB & Social SEO Hub",
                subtitle = "Chats Archive, Normalized Scripts/Scenes, YT/IG/FB SEO & CSV Export",
                icon = Icons.Filled.Storage,
                onExecute = { onOpenOverlay("NONE"); onNavigateTab(StudioTabId.CONTENT_HUB); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_backup",
                category = "Quick Action",
                title = "Open Backup & Sync Center",
                subtitle = "AES-256-GCM Export, GitHub Cloud Sync, Telegram & Rolling Snapshots",
                icon = Icons.Filled.CloudSync,
                onExecute = { onOpenOverlay("BACKUP_CENTER"); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_theme",
                category = "Settings",
                title = "UI Theme & Layout Studio",
                subtitle = "6 Presets, Custom Hex Accent, Wallpaper, Bottom Nav & Quick FAB",
                icon = Icons.Filled.Palette,
                onExecute = { onOpenOverlay("THEME_STUDIO"); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_terminal",
                category = "Quick Action",
                title = "Open Hybrid Terminal & OmniCLI",
                subtitle = "Run local shell commands, OmniCLI tools, or Termux Bridge",
                icon = Icons.Filled.Terminal,
                onExecute = { onOpenOverlay("NONE"); onNavigateTab(StudioTabId.TERMINAL); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_netlog",
                category = "Settings",
                title = "Toggle Live API Network Inspector (netlog)",
                subtitle = "Stream masked OkHttp3 requests inside Hybrid Terminal",
                icon = Icons.Filled.Terminal,
                onExecute = {
                    viewModel.toggleNetlogStreaming()
                    onOpenOverlay("NONE")
                    onNavigateTab(StudioTabId.TERMINAL)
                    onDismiss()
                }
            ),
            PaletteActionItem(
                id = "act_analytics",
                category = "Quick Action",
                title = "Open Analytics & GPU Telemetry",
                subtitle = "Token velocity, provider latency & Kaggle 30h quota charts",
                icon = Icons.Filled.BarChart,
                onExecute = { onOpenOverlay("ANALYTICS"); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_settings",
                category = "Settings",
                title = "Open Settings Hub",
                subtitle = "Appearance, AI Engine, RSS Agent, Duress PIN & Diagnostics",
                icon = Icons.Filled.Settings,
                onExecute = { onOpenOverlay("SETTINGS_HUB"); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_chains",
                category = "Quick Action",
                title = "Open Prompt Chains & Workflows",
                subtitle = "Run multi-step Viral Shorts or Deep Research workflows",
                icon = Icons.Filled.ChatBubble,
                onExecute = { onOpenOverlay("CHAIN_RUNNER"); onDismiss() }
            ),
            PaletteActionItem(
                id = "act_director",
                category = "Quick Action",
                title = "Go to Video Director & Storyboards",
                subtitle = "Generate Wan 2.1 scenes and launch Dual-GPU Kaggle renders",
                icon = Icons.Filled.Settings,
                onExecute = { onOpenOverlay("NONE"); onNavigateTab(StudioTabId.DIRECTOR); onDismiss() }
            )
        )
    }

    val allItems = buildList {
        if (isDirectTerminalExec && directCommand.isNotBlank()) {
            add(
                PaletteActionItem(
                    id = "exec_direct",
                    category = "Run in Terminal",
                    title = "Execute: $directCommand",
                    subtitle = "Dispatches directly to Hybrid Terminal & switches view",
                    icon = Icons.Filled.Terminal,
                    onExecute = {
                        onOpenOverlay("NONE")
                        onNavigateTab(StudioTabId.TERMINAL)
                        viewModel.executeTerminalCommand(directCommand)
                        onDismiss()
                    }
                )
            )
        }

        addAll(quickActions)

        projects.take(10).forEach { proj ->
            add(
                PaletteActionItem(
                    id = "proj_${proj.id}",
                    category = "Project",
                    title = "${proj.name} (${proj.status})",
                    subtitle = if (proj.githubRepo.isNotBlank()) "${proj.githubOwner}/${proj.githubRepo}@${proj.githubBranch}" else "Local workspace · ${proj.slug}",
                    icon = Icons.Filled.FolderSpecial,
                    onExecute = {
                        viewModel.selectProject(proj.id)
                        onOpenOverlay("NONE")
                        onNavigateTab(StudioTabId.PROJECTS)
                        onDismiss()
                    }
                )
            )
        }

        snippets.forEach { snippet ->
            add(
                PaletteActionItem(
                    id = "snip_${snippet.id}",
                    category = "Terminal Snippet",
                    title = snippet.name,
                    subtitle = snippet.command,
                    icon = Icons.Filled.Terminal,
                    onExecute = {
                        onOpenOverlay("NONE")
                        onNavigateTab(StudioTabId.TERMINAL)
                        viewModel.executeTerminalCommand(snippet.command, forceTermux = snippet.useTermuxBridge)
                        onDismiss()
                    }
                )
            )
        }

        videoScripts.take(10).forEach { bundle ->
            add(
                PaletteActionItem(
                    id = "script_${bundle.script.id}",
                    category = "Video Script",
                    title = "[${bundle.script.status}] ${bundle.script.title}",
                    subtitle = "${bundle.scenes.size} scenes · ${bundle.socialPosts.size} YT/IG/FB posts",
                    icon = Icons.Filled.Storage,
                    onExecute = {
                        onOpenOverlay("NONE")
                        onNavigateTab(StudioTabId.CONTENT_HUB)
                        onDismiss()
                    }
                )
            )
        }

        threads.take(12).forEach { thread ->
            add(
                PaletteActionItem(
                    id = "chat_${thread.id}",
                    category = "Chat Thread",
                    title = thread.title,
                    subtitle = "Resume conversation in AI Chat Studio",
                    icon = Icons.Filled.ChatBubble,
                    onExecute = {
                        viewModel.selectThread(thread.id)
                        onOpenOverlay("NONE")
                        onNavigateTab(StudioTabId.CHAT)
                        onDismiss()
                    }
                )
            )
        }
    }

    val filtered = if (trimmed.isBlank() || isDirectTerminalExec) {
        if (isDirectTerminalExec) {
            allItems.filter { it.id == "exec_direct" || it.category == "Terminal Snippet" }
        } else {
            allItems.take(14)
        }
    } else {
        allItems.filter {
            it.title.contains(trimmed, ignoreCase = true) ||
                it.subtitle.contains(trimmed, ignoreCase = true) ||
                it.category.contains(trimmed, ignoreCase = true)
        }
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = colorScheme.surface,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Icon(Icons.Filled.Search, contentDescription = null, tint = colorScheme.primary)
                Text("Universal Command Palette", style = MaterialTheme.typography.titleMedium)
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(
                    value = query,
                    onValueChange = { query = it },
                    placeholder = { Text("Search settings, chats, snippets or type '> sysinfo'...") },
                    singleLine = true,
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Go),
                    keyboardActions = KeyboardActions(onGo = {
                        filtered.firstOrNull()?.onExecute?.invoke()
                    }),
                    modifier = Modifier.fillMaxWidth()
                )

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(300.dp),
                    verticalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    items(filtered, key = { it.id }) { item ->
                        Surface(
                            color = colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, colorScheme.outline),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable { item.onExecute() }
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(item.icon, contentDescription = null, tint = colorScheme.primary)
                                Spacer(modifier = Modifier.width(10.dp))
                                Column(modifier = Modifier.weight(1f)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text(
                                            text = item.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            maxLines = 1,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = item.category,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.primary
                                        )
                                    }
                                    Text(
                                        text = item.subtitle,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontFamily = if (item.category.contains("Terminal")) FontFamily.Monospace else FontFamily.Default,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {},
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
