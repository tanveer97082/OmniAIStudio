package com.omniai.studio.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.ArrowForward
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.CreateNewFolder
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Description
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ErrorOutline
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.TaskAlt
import androidx.compose.material.icons.filled.Undo
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExtendedFloatingActionButton
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Tab
import androidx.compose.material3.TabRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.ProjectEntity
import com.omniai.studio.data.local.ProjectTaskEntity
import com.omniai.studio.media.StorageGalleryHelper
import com.omniai.studio.projects.AiMultiFileSplitter
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.OmniAccentBadge
import com.omniai.studio.ui.theme.OmniDesignTokens
import com.omniai.studio.ui.theme.OmniEmptyState
import com.omniai.studio.ui.theme.OmniOverflowAction
import com.omniai.studio.ui.theme.OmniOverflowMenuButton
import com.omniai.studio.ui.theme.OmniSectionHeader
import com.omniai.studio.ui.theme.OmniSurfaceCard
import com.omniai.studio.ui.theme.omniAccentBorder
import com.omniai.studio.ui.theme.omniAccentContainer
import com.omniai.studio.viewmodel.OmniAiViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val PROJECT_TYPES = listOf("ANDROID_APP", "VIDEO_SERIES", "AI_TOOL", "WEB_APP", "GENERAL")
private val PROJECT_STATUSES = listOf("PLANNING", "ACTIVE", "TESTING", "COMPLETED", "ARCHIVED")
private val KANBAN_COLUMNS = listOf("TO_DO", "IN_PROGRESS", "REVIEW", "DONE")
private val TASK_PRIORITIES = listOf("LOW", "MEDIUM", "HIGH", "URGENT")

@OptIn(ExperimentalLayoutApi::class)
@Composable
fun ProjectDashboardScreen(
    viewModel: OmniAiViewModel,
    onOpenChat: () -> Unit = {},
    onOpenDirector: () -> Unit = {},
    onFullscreenEditorChanged: (Boolean) -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary

    val projects by viewModel.projects.collectAsState()
    val allTasks by viewModel.allProjectTasks.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val activeTasks by viewModel.activeProjectTasks.collectAsState()
    val commitLogs by viewModel.activeProjectCommitLogs.collectAsState()
    val diffByProject by viewModel.projectDiffByProjectId.collectAsState()
    val activeFiles by viewModel.activeProjectFiles.collectAsState()
    val activeDiff by viewModel.activeProjectDiff.collectAsState()
    val gitProgressMessage by viewModel.gitProgressMessage.collectAsState()
    val isGitBusy by viewModel.isGitBusy.collectAsState()
    val isPlanningTasks by viewModel.isPlanningProjectTasks.collectAsState()
    val isGeneratingCommitMsg by viewModel.isGeneratingCommitMsg.collectAsState()
    val actionsState by viewModel.actionsMonitorState.collectAsState()
    val isDownloadingActionsApk by viewModel.isDownloadingActionsApk.collectAsState()
    val niches by viewModel.niches.collectAsState()
    val videoScripts by viewModel.videoScriptsWithRelations.collectAsState()
    val chatThreads by viewModel.allWorkspaceChatThreads.collectAsState()
    val settings by viewModel.appSettings.collectAsState()

    var viewingProjectDetail by remember { mutableStateOf(false) }
    var selectedSubTab by remember { mutableIntStateOf(0) } // 0=Kanban, 1=Files, 2=Git/CI, 3=Notes
    var showProjectDialog by remember { mutableStateOf(false) }
    var editingProject by remember { mutableStateOf<ProjectEntity?>(null) }

    // Kanban dialogs
    var selectedKanbanColumn by remember { mutableStateOf("ALL") }
    var showTaskDialog by remember { mutableStateOf(false) }
    var editingTask by remember { mutableStateOf<ProjectTaskEntity?>(null) }

    // Files & Distraction-Free Full-Screen Code Editor state
    var selectedFilePath by remember { mutableStateOf<String?>(null) }
    var editorContent by remember { mutableStateOf("") }
    var editorUndoSnapshot by remember { mutableStateOf("") }
    var showNewFileDialog by remember { mutableStateOf(false) }
    var newFileIsFolder by remember { mutableStateOf(false) }
    var newFilePathInput by remember { mutableStateOf("") }
    var showSplitterDialog by remember { mutableStateOf(false) }
    var splitterInputText by remember { mutableStateOf("") }

    // Git & Private Repo state
    var commitMessageInput by remember { mutableStateOf("") }
    var showCreateRepoDialog by remember { mutableStateOf(false) }
    var newRepoNameInput by remember { mutableStateOf("") }
    var newRepoDescInput by remember { mutableStateOf("") }

    // Notes editor state
    var notesDraft by remember(activeProject?.id) { mutableStateOf(activeProject?.notes.orEmpty()) }

    val isFullscreenCodeEditor = viewingProjectDetail && activeProject != null && selectedSubTab == 1 && selectedFilePath != null

    LaunchedEffect(isFullscreenCodeEditor) {
        onFullscreenEditorChanged(isFullscreenCodeEditor)
    }

    DisposableEffect(Unit) {
        onDispose {
            onFullscreenEditorChanged(false)
        }
    }

    LaunchedEffect(activeProject?.id) {
        activeProject?.let {
            viewModel.refreshProjectWorkspace(it.id)
            notesDraft = it.notes
        }
    }

    LaunchedEffect(Unit) {
        viewModel.refreshAllProjectDiffs()
    }

    // Compute sticky Git Auto-Push target project & change count
    val dirtyOverviewProject = remember(projects, diffByProject) {
        projects.firstOrNull { (diffByProject[it.id]?.totalPendingCount ?: 0) > 0 }
    }
    val stickyPushProject = if (viewingProjectDetail && activeProject != null) activeProject else dirtyOverviewProject
    val stickyPendingCount = if (viewingProjectDetail && activeProject != null) {
        activeDiff.totalPendingCount
    } else {
        dirtyOverviewProject?.let { diffByProject[it.id]?.totalPendingCount ?: 0 } ?: 0
    }
    val showStickyPushPill = !isFullscreenCodeEditor && stickyPushProject != null && stickyPendingCount > 0

    // =========================================================================
    // FULL-SCREEN DISTRACTION-FREE CODE EDITOR MODE
    // =========================================================================
    if (isFullscreenCodeEditor) {
        val proj = activeProject!!
        val currentPath = selectedFilePath!!
        val hasUnsavedChanges = editorContent != editorUndoSnapshot

        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(colorScheme.background)
        ) {
            // Slim Top Save / Undo / Diff Bar
            Surface(
                color = colorScheme.surface,
                modifier = Modifier
                    .fillMaxWidth()
                    .height(OmniDesignTokens.TopBarHeight)
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 8.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(
                        modifier = Modifier.weight(1f),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        IconButton(
                            onClick = { selectedFilePath = null },
                            modifier = Modifier.defaultMinSize(
                                minWidth = OmniDesignTokens.MinTouchTarget,
                                minHeight = OmniDesignTokens.MinTouchTarget
                            )
                        ) {
                            Icon(Icons.Filled.Close, contentDescription = "Exit Full-Screen Editor")
                        }
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = currentPath,
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = EmeraldAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (hasUnsavedChanges) {
                                    "Unsaved · +${activeDiff.added.size} ~${activeDiff.modified.size} -${activeDiff.deleted.size}"
                                } else {
                                    "Saved · +${activeDiff.added.size} ~${activeDiff.modified.size} -${activeDiff.deleted.size}"
                                },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = if (hasUnsavedChanges) AmberWarning else colorScheme.onSurfaceVariant,
                                maxLines = 1
                            )
                        }
                    }

                    Row(verticalAlignment = Alignment.CenterVertically) {
                        IconButton(
                            onClick = { editorContent = editorUndoSnapshot },
                            enabled = hasUnsavedChanges
                        ) {
                            Icon(
                                Icons.Filled.Undo,
                                contentDescription = "Undo Edits",
                                tint = if (hasUnsavedChanges) AmberWarning else colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(
                            onClick = {
                                viewModel.saveProjectFile(proj, currentPath, editorContent)
                                editorUndoSnapshot = editorContent
                            }
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = "Save File", tint = EmeraldAccent)
                        }
                    }
                }
            }
            HorizontalDivider(color = colorScheme.outline, thickness = OmniDesignTokens.BorderWidth)

            OutlinedTextField(
                value = editorContent,
                onValueChange = { editorContent = it },
                textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
            )
        }
        return
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OmniDesignTokens.ScreenHorizontalPadding, vertical = 12.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (!viewingProjectDetail || activeProject == null) {
                // =============================================================
                // PROJECT DASHBOARD OVERVIEW
                // =============================================================
                OmniSectionHeader(
                    title = "Projects & Git Hub",
                    subtitle = "${projects.size} workspace(s) · Atomic GitHub Push",
                    trailing = {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                            IconButton(onClick = { viewModel.refreshAllProjectDiffs() }) {
                                Icon(Icons.Filled.Refresh, contentDescription = "Scan Local Diffs", tint = primaryColor)
                            }
                            Button(
                                onClick = {
                                    editingProject = null
                                    showProjectDialog = true
                                },
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("New")
                            }
                        }
                    }
                )

                if (gitProgressMessage != null) {
                    Surface(
                        color = colorScheme.surfaceVariant,
                        border = BorderStroke(OmniDesignTokens.BorderWidth, primaryColor.omniAccentBorder()),
                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (isGitBusy) {
                                CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                            }
                            Text(
                                text = gitProgressMessage.orEmpty(),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurface
                            )
                        }
                    }
                }

                if (projects.isEmpty()) {
                    OmniEmptyState(
                        icon = Icons.Filled.FolderSpecial,
                        title = "No project workspaces yet",
                        helperText = "Track Kanban tasks, split AI code outputs, and push atomic commits to GitHub.",
                        actionLabel = "Create First Project",
                        onActionClick = {
                            editingProject = null
                            showProjectDialog = true
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(bottom = 84.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        items(projects, key = { it.id }) { proj ->
                            val projTasks = allTasks.filter { it.projectId == proj.id }
                            val doneCount = projTasks.count { it.columnStatus == "DONE" }
                            val totalCount = projTasks.size
                            val progress = if (totalCount == 0) 0f else doneCount.toFloat() / totalCount.toFloat()
                            val diff = diffByProject[proj.id]
                            val pendingCount = diff?.totalPendingCount ?: 0
                            val repoLabel = if (proj.githubOwner.isNotBlank() && proj.githubRepo.isNotBlank()) {
                                "${proj.githubOwner}/${proj.githubRepo}@${proj.githubBranch}"
                            } else {
                                "Local only · Link GitHub repo"
                            }

                            OmniSurfaceCard(
                                borderColor = if (proj.id == activeProject?.id) primaryColor.omniAccentBorder() else colorScheme.outline,
                                onClick = {
                                    viewModel.selectProject(proj.id)
                                    viewingProjectDetail = true
                                }
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = proj.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${proj.projectType} · ${proj.status}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primaryColor
                                        )
                                    }

                                    // Max 2 visible controls: 1-Tap Push + Overflow Menu
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.pushProjectAtomically(proj, "") },
                                            enabled = !isGitBusy && proj.githubOwner.isNotBlank() && proj.githubRepo.isNotBlank()
                                        ) {
                                            Icon(
                                                Icons.Filled.CloudUpload,
                                                contentDescription = "1-Tap Git Auto-Push",
                                                tint = if (pendingCount > 0) EmeraldAccent else colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OmniOverflowMenuButton(
                                            actions = listOf(
                                                OmniOverflowAction("Edit Project Settings", Icons.Filled.Edit) {
                                                    editingProject = proj
                                                    showProjectDialog = true
                                                },
                                                OmniOverflowAction("Pull from GitHub", Icons.Filled.CloudDownload, enabled = !isGitBusy && proj.githubRepo.isNotBlank()) {
                                                    viewModel.pullProjectFromGitHub(proj)
                                                },
                                                OmniOverflowAction("Delete Project", Icons.Filled.Delete, isDestructive = true) {
                                                    viewModel.deleteProject(proj)
                                                }
                                            )
                                        )
                                    }
                                }

                                if (proj.description.isNotBlank()) {
                                    Text(
                                        text = proj.description,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 2,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = repoLabel,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelSmall,
                                        color = if (proj.githubRepo.isNotBlank()) EmeraldAccent else colorScheme.onSurfaceVariant,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    Spacer(modifier = Modifier.width(8.dp))
                                    OmniAccentBadge(
                                        text = if (pendingCount > 0) "$pendingCount modified" else "Synced",
                                        accent = if (pendingCount > 0) AmberWarning else EmeraldAccent
                                    )
                                }

                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween
                                    ) {
                                        Text("Kanban Progress", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                                        Text("$doneCount / $totalCount done", style = MaterialTheme.typography.labelSmall, color = primaryColor)
                                    }
                                    LinearProgressIndicator(
                                        progress = { progress },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clip(RoundedCornerShape(4.dp)),
                                        color = EmeraldAccent,
                                        trackColor = colorScheme.surfaceVariant
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                // =============================================================
                // PROJECT WORKSPACE DETAIL (4 SUB-TABS)
                // =============================================================
                val proj = activeProject!!
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        IconButton(onClick = { viewingProjectDetail = false }) {
                            Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Projects")
                        }
                        Column {
                            Text(
                                text = proj.name,
                                style = MaterialTheme.typography.titleMedium,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (proj.githubRepo.isNotBlank()) {
                                    "${proj.githubOwner}/${proj.githubRepo}@${proj.githubBranch}"
                                } else {
                                    "filesDir/projects/${proj.slug}"
                                },
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = EmeraldAccent,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(onClick = {
                        editingProject = proj
                        showProjectDialog = true
                    }) {
                        Icon(Icons.Filled.Edit, contentDescription = "Edit Project Settings", tint = primaryColor)
                    }
                }

                TabRow(
                    selectedTabIndex = selectedSubTab,
                    containerColor = colorScheme.surface,
                    contentColor = primaryColor
                ) {
                    Tab(selected = selectedSubTab == 0, onClick = { selectedSubTab = 0 }, text = { Text("Kanban") })
                    Tab(selected = selectedSubTab == 1, onClick = { selectedSubTab = 1 }, text = { Text("Files") })
                    Tab(selected = selectedSubTab == 2, onClick = { selectedSubTab = 2 }, text = { Text("Git & CI") })
                    Tab(selected = selectedSubTab == 3, onClick = { selectedSubTab = 3 }, text = { Text("Notes") })
                }

                when (selectedSubTab) {
                    // ---------------------------------------------------------
                    // SUB-TAB 1: KANBAN TASKS
                    // ---------------------------------------------------------
                    0 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            Button(
                                onClick = {
                                    editingTask = null
                                    showTaskDialog = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Add Task")
                            }
                            OutlinedButton(
                                onClick = { viewModel.aiAutoPlanProjectTasks(proj) },
                                enabled = !isPlanningTasks,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                if (isPlanningTasks) {
                                    CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                                } else {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = EmeraldAccent)
                                }
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("AI Auto-Plan")
                            }
                        }

                        FlowRow(
                            horizontalArrangement = Arrangement.spacedBy(6.dp),
                            verticalArrangement = Arrangement.spacedBy(4.dp)
                        ) {
                            FilterChip(
                                selected = selectedKanbanColumn == "ALL",
                                onClick = { selectedKanbanColumn = "ALL" },
                                label = { Text("All (${activeTasks.size})") }
                            )
                            KANBAN_COLUMNS.forEach { col ->
                                val count = activeTasks.count { it.columnStatus == col }
                                FilterChip(
                                    selected = selectedKanbanColumn == col,
                                    onClick = { selectedKanbanColumn = col },
                                    label = { Text("${col.replace('_', ' ')} ($count)") }
                                )
                            }
                        }

                        val displayedTasks = if (selectedKanbanColumn == "ALL") {
                            activeTasks
                        } else {
                            activeTasks.filter { it.columnStatus == selectedKanbanColumn }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            if (displayedTasks.isEmpty()) {
                                item {
                                    OmniEmptyState(
                                        icon = Icons.Filled.TaskAlt,
                                        title = "No tasks in this column",
                                        helperText = "Add a milestone task manually or let AI Auto-Plan generate your sprint backlog.",
                                        actionLabel = "Add Task",
                                        onActionClick = {
                                            editingTask = null
                                            showTaskDialog = true
                                        }
                                    )
                                }
                            } else {
                                items(displayedTasks, key = { it.id }) { task ->
                                    val colIdx = KANBAN_COLUMNS.indexOf(task.columnStatus).coerceAtLeast(0)
                                    val priorityColor = when (task.priority) {
                                        "URGENT" -> CrimsonDanger
                                        "HIGH" -> AmberWarning
                                        "MEDIUM" -> primaryColor
                                        else -> EmeraldAccent
                                    }
                                    OmniSurfaceCard {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = task.title,
                                                style = MaterialTheme.typography.bodyLarge,
                                                fontWeight = FontWeight.Medium,
                                                modifier = Modifier.weight(1f)
                                            )
                                            OmniAccentBadge(text = task.priority, accent = priorityColor)
                                        }
                                        if (task.details.isNotBlank()) {
                                            Text(
                                                text = task.details,
                                                style = MaterialTheme.typography.bodySmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = task.columnStatus.replace('_', ' '),
                                                style = MaterialTheme.typography.labelMedium,
                                                color = EmeraldAccent
                                            )
                                            // Max 2 visible controls: Advance Stage + Overflow Menu
                                            Row(verticalAlignment = Alignment.CenterVertically) {
                                                if (colIdx < KANBAN_COLUMNS.lastIndex) {
                                                    OutlinedButton(
                                                        onClick = {
                                                            viewModel.moveProjectTaskColumn(task, KANBAN_COLUMNS[colIdx + 1])
                                                        }
                                                    ) {
                                                        Text("Advance")
                                                        Spacer(modifier = Modifier.width(4.dp))
                                                        Icon(Icons.AutoMirrored.Filled.ArrowForward, contentDescription = null, modifier = Modifier.size(16.dp))
                                                    }
                                                }
                                                OmniOverflowMenuButton(
                                                    actions = listOf(
                                                        OmniOverflowAction(
                                                            label = "Move Back Stage",
                                                            icon = Icons.AutoMirrored.Filled.ArrowBack,
                                                            enabled = colIdx > 0
                                                        ) {
                                                            viewModel.moveProjectTaskColumn(task, KANBAN_COLUMNS[colIdx - 1])
                                                        },
                                                        OmniOverflowAction("Edit Task", Icons.Filled.Edit) {
                                                            editingTask = task
                                                            showTaskDialog = true
                                                        },
                                                        OmniOverflowAction("Delete Task", Icons.Filled.Delete, isDestructive = true) {
                                                            viewModel.deleteProjectTask(task)
                                                        }
                                                    )
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // SUB-TAB 2: FILES & AI MULTI-FILE SPLITTER
                    // ---------------------------------------------------------
                    1 -> {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Button(
                                onClick = {
                                    splitterInputText = ""
                                    showSplitterDialog = true
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                Icon(Icons.Filled.Code, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("AI ### FILE Splitter")
                            }
                            OutlinedButton(
                                onClick = {
                                    newFileIsFolder = false
                                    newFilePathInput = ""
                                    showNewFileDialog = true
                                },
                                modifier = Modifier.defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                Icon(Icons.Filled.Description, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("File")
                            }
                            IconButton(
                                onClick = {
                                    newFileIsFolder = true
                                    newFilePathInput = ""
                                    showNewFileDialog = true
                                }
                            ) {
                                Icon(Icons.Filled.CreateNewFolder, contentDescription = "New Folder", tint = primaryColor)
                            }
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            if (activeFiles.isEmpty()) {
                                item {
                                    OmniEmptyState(
                                        icon = Icons.Filled.Code,
                                        title = "Workspace directory is empty",
                                        helperText = "Create a file, pull from GitHub, or paste AI ### FILE: output to populate files.",
                                        actionLabel = "AI ### FILE Splitter",
                                        onActionClick = {
                                            splitterInputText = ""
                                            showSplitterDialog = true
                                        }
                                    )
                                }
                            } else {
                                items(activeFiles, key = { it }) { relPath ->
                                    val badge = when {
                                        relPath in activeDiff.added -> "NEW" to EmeraldAccent
                                        relPath in activeDiff.modified -> "MOD" to AmberWarning
                                        else -> "SYNCED" to colorScheme.onSurfaceVariant
                                    }
                                    Surface(
                                        color = colorScheme.surface,
                                        border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline),
                                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .clickable {
                                                viewModel.readProjectFile(proj, relPath) { loaded ->
                                                    editorContent = loaded
                                                    editorUndoSnapshot = loaded
                                                    selectedFilePath = relPath
                                                }
                                            }
                                    ) {
                                        Row(
                                            modifier = Modifier
                                                .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget)
                                                .padding(horizontal = 14.dp, vertical = 10.dp),
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                                Text(
                                                    text = relPath,
                                                    fontFamily = FontFamily.Monospace,
                                                    style = MaterialTheme.typography.bodyMedium,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis
                                                )
                                                OmniAccentBadge(text = badge.first, accent = badge.second)
                                            }
                                            IconButton(onClick = { viewModel.deleteProjectFile(proj, relPath) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete File", tint = CrimsonDanger)
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // SUB-TAB 3: GIT & CI/CD CONTROL + LIVE ACTIONS MONITOR
                    // ---------------------------------------------------------
                    2 -> {
                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(14.dp)
                        ) {
                            // Pending Diff Card
                            item {
                                OmniSurfaceCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Pending Local Diff", style = MaterialTheme.typography.titleSmall, color = primaryColor)
                                        IconButton(onClick = { viewModel.refreshProjectWorkspace(proj.id) }) {
                                            Icon(Icons.Filled.Refresh, contentDescription = "Rescan Diff", tint = primaryColor)
                                        }
                                    }
                                    Text(
                                        text = "+${activeDiff.added.size} added  ~${activeDiff.modified.size} modified  -${activeDiff.deleted.size} deleted  (${activeDiff.synced.size} synced)",
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (activeDiff.hasChanges) AmberWarning else EmeraldAccent
                                    )
                                    val previewItems = buildList {
                                        activeDiff.added.take(6).forEach { add("+ $it") }
                                        activeDiff.modified.take(6).forEach { add("~ $it") }
                                        activeDiff.deleted.take(6).forEach { add("- $it") }
                                    }
                                    if (previewItems.isNotEmpty()) {
                                        Surface(
                                            color = colorScheme.background,
                                            border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline),
                                            shape = RoundedCornerShape(8.dp),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Column(modifier = Modifier.padding(10.dp)) {
                                                previewItems.forEach { line ->
                                                    Text(
                                                        text = line,
                                                        fontFamily = FontFamily.Monospace,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = when {
                                                            line.startsWith("+") -> EmeraldAccent
                                                            line.startsWith("-") -> CrimsonDanger
                                                            else -> AmberWarning
                                                        }
                                                    )
                                                }
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = commitMessageInput,
                                            onValueChange = { commitMessageInput = it },
                                            label = { Text("Commit Message (blank for AI)") },
                                            singleLine = true,
                                            modifier = Modifier.weight(1f)
                                        )
                                        IconButton(
                                            onClick = {
                                                viewModel.generateAiCommitMessageForProject(proj) { generated ->
                                                    commitMessageInput = generated
                                                }
                                            },
                                            enabled = !isGeneratingCommitMsg && activeDiff.hasChanges
                                        ) {
                                            if (isGeneratingCommitMsg) {
                                                CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                                            } else {
                                                Icon(Icons.Filled.AutoAwesome, contentDescription = "AI Auto-Generate Commit Msg", tint = EmeraldAccent)
                                            }
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Button(
                                            onClick = { viewModel.pushProjectAtomically(proj, commitMessageInput) },
                                            enabled = !isGitBusy,
                                            modifier = Modifier
                                                .weight(1f)
                                                .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                            shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                                        ) {
                                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Git Auto-Push Now")
                                        }
                                        OutlinedButton(
                                            onClick = { viewModel.pullProjectFromGitHub(proj) },
                                            enabled = !isGitBusy,
                                            modifier = Modifier.defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                            shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                                        ) {
                                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Pull")
                                        }
                                    }

                                    OutlinedButton(
                                        onClick = {
                                            newRepoNameInput = proj.slug
                                            newRepoDescInput = proj.description
                                            showCreateRepoDialog = true
                                        },
                                        enabled = !isGitBusy,
                                        modifier = Modifier.fillMaxWidth(),
                                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                                    ) {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text("Create New Private GitHub Repo")
                                    }

                                    if (gitProgressMessage != null) {
                                        Text(
                                            text = gitProgressMessage.orEmpty(),
                                            fontFamily = FontFamily.Monospace,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = primaryColor
                                        )
                                    }
                                }
                            }

                            // Live GitHub Actions Build Monitor Card
                            item {
                                OmniSurfaceCard(
                                    borderColor = when (actionsState.status) {
                                        "SUCCESS" -> EmeraldAccent
                                        "FAILURE" -> CrimsonDanger
                                        "IN_PROGRESS", "QUEUED" -> AmberWarning
                                        else -> colorScheme.outline
                                    }
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Icon(
                                                imageVector = when (actionsState.status) {
                                                    "SUCCESS" -> Icons.Filled.CheckCircle
                                                    "FAILURE" -> Icons.Filled.ErrorOutline
                                                    else -> Icons.Filled.PlayArrow
                                                },
                                                contentDescription = null,
                                                tint = when (actionsState.status) {
                                                    "SUCCESS" -> EmeraldAccent
                                                    "FAILURE" -> CrimsonDanger
                                                    else -> AmberWarning
                                                }
                                            )
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("GitHub Actions Build Monitor", style = MaterialTheme.typography.titleSmall)
                                        }
                                        if (actionsState.isWatching) {
                                            IconButton(onClick = { viewModel.stopWatchingActions() }) {
                                                Icon(Icons.Filled.Stop, contentDescription = "Stop Watching", tint = AmberWarning)
                                            }
                                        } else {
                                            TextButton(onClick = { viewModel.watchProjectActions(proj) }) {
                                                Text("Watch Run")
                                            }
                                        }
                                    }

                                    Text(
                                        text = "Status: ${actionsState.status}" +
                                            (if (actionsState.runNumber > 0) " · Run #${actionsState.runNumber}" else "") +
                                            (if (actionsState.elapsedSec > 0L) " · ${actionsState.elapsedSec}s" else ""),
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = when (actionsState.status) {
                                            "SUCCESS" -> EmeraldAccent
                                            "FAILURE" -> CrimsonDanger
                                            else -> primaryColor
                                        }
                                    )
                                    if (actionsState.workflowName.isNotBlank()) {
                                        Text(
                                            text = actionsState.workflowName,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (actionsState.isWatching) {
                                        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                                    }
                                    if (actionsState.status == "SUCCESS" && actionsState.releaseApkAsset != null) {
                                        val apkAsset = actionsState.releaseApkAsset!!
                                        Button(
                                            onClick = {
                                                viewModel.downloadActionsReleaseApk(apkAsset) { apkFile ->
                                                    val intent = StorageGalleryHelper.createInstallApkIntent(context, apkFile)
                                                    context.startActivity(intent)
                                                }
                                            },
                                            enabled = !isDownloadingActionsApk,
                                            colors = ButtonDefaults.buttonColors(containerColor = EmeraldAccent),
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text(
                                                if (isDownloadingActionsApk) "Downloading APK..."
                                                else "Install Updated APK (${actionsState.releaseTagName.ifBlank { apkAsset.name }})"
                                            )
                                        }
                                    }
                                    if (actionsState.status == "FAILURE") {
                                        if (actionsState.failureSummary.isNotBlank()) {
                                            Surface(
                                                color = colorScheme.background,
                                                border = BorderStroke(1.dp, CrimsonDanger.copy(alpha = 0.6f)),
                                                shape = RoundedCornerShape(6.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Text(
                                                    text = actionsState.failureSummary,
                                                    fontFamily = FontFamily.Monospace,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = CrimsonDanger,
                                                    modifier = Modifier.padding(8.dp)
                                                )
                                            }
                                        }
                                        if (actionsState.htmlUrl.isNotBlank()) {
                                            OutlinedButton(
                                                onClick = {
                                                    val report = buildString {
                                                        appendLine("Build Run URL: ${actionsState.htmlUrl}")
                                                        if (actionsState.failureSummary.isNotBlank()) {
                                                            appendLine(actionsState.failureSummary)
                                                        }
                                                    }
                                                    clipboard.setText(AnnotatedString(report))
                                                    viewModel.postToast("Copied failed build URL & summary.")
                                                },
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                                Spacer(modifier = Modifier.width(6.dp))
                                                Text("Copy Build Run URL & Failure Summary")
                                            }
                                        }
                                    }
                                }
                            }

                            // Commit History
                            item {
                                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Icon(Icons.Filled.History, contentDescription = null, tint = primaryColor)
                                    Text("Commit History (${commitLogs.size})", style = MaterialTheme.typography.titleSmall)
                                }
                            }
                            if (commitLogs.isEmpty()) {
                                item {
                                    Text(
                                        "No atomic commits pushed yet for this project.",
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                            } else {
                                items(commitLogs, key = { it.id }) { log ->
                                    val timeStr = remember(log.timestamp) {
                                        SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(log.timestamp))
                                    }
                                    OmniSurfaceCard {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween
                                        ) {
                                            Text(
                                                text = "${log.commitSha.take(7).ifBlank { "FAILED" }} · ${log.status}",
                                                fontFamily = FontFamily.Monospace,
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (log.status == "SUCCESS") EmeraldAccent else CrimsonDanger
                                            )
                                            Text(timeStr, style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                                        }
                                        Text(log.commitMessage, style = MaterialTheme.typography.bodySmall)
                                        Text(
                                            text = "${log.filesChanged} files · ${log.branch}" +
                                                if (log.errorLog.isNotBlank()) " · ${log.errorLog}" else "",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // ---------------------------------------------------------
                    // SUB-TAB 4: PROJECT NOTES & LINKED CONTENT
                    // ---------------------------------------------------------
                    3 -> {
                        val linkedNiche = niches.firstOrNull { it.id == proj.linkedNicheId }
                        val relatedScripts = if (proj.linkedNicheId != null) {
                            videoScripts.filter { it.script.nicheId == proj.linkedNicheId }
                        } else {
                            videoScripts.take(5)
                        }
                        val relatedChats = if (proj.linkedNicheId != null) {
                            chatThreads.filter { it.nicheId == proj.linkedNicheId }.take(6)
                        } else {
                            chatThreads.take(6)
                        }

                        LazyColumn(
                            modifier = Modifier.weight(1f),
                            contentPadding = PaddingValues(bottom = 84.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            item {
                                OmniSurfaceCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Project Architecture & Notes", style = MaterialTheme.typography.titleSmall, color = primaryColor)
                                        Button(onClick = { viewModel.saveProjectNotes(proj, notesDraft) }) {
                                            Icon(Icons.Filled.Save, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Save")
                                        }
                                    }
                                    OutlinedTextField(
                                        value = notesDraft,
                                        onValueChange = { notesDraft = it },
                                        placeholder = { Text("Architecture notes, API contracts, release checklist...") },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .heightIn(min = 130.dp)
                                    )
                                }
                            }

                            item {
                                Text(
                                    text = "Linked Niche: ${linkedNiche?.let { "${it.emoji} ${it.name}" } ?: "All Workspaces"}",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldAccent
                                )
                            }

                            item {
                                Text("Linked Video Scripts (${relatedScripts.size})", style = MaterialTheme.typography.labelLarge, color = primaryColor)
                            }
                            if (relatedScripts.isEmpty()) {
                                item {
                                    Text("No linked video scripts yet.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                                }
                            } else {
                                items(relatedScripts.take(6), key = { it.script.id }) { item ->
                                    OmniSurfaceCard(
                                        onClick = {
                                            viewModel.sendNormalizedScriptToKaggleDirector(item.script.id)
                                            onOpenDirector()
                                        }
                                    ) {
                                        Text(item.script.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${item.scenes.size} scenes · ${item.script.status}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }

                            item {
                                HorizontalDivider(color = colorScheme.outline)
                                Spacer(modifier = Modifier.height(4.dp))
                                Text("Linked Chat Threads (${relatedChats.size})", style = MaterialTheme.typography.labelLarge, color = primaryColor)
                            }
                            if (relatedChats.isEmpty()) {
                                item {
                                    Text("No linked chat threads yet.", style = MaterialTheme.typography.bodySmall, color = colorScheme.onSurfaceVariant)
                                }
                            } else {
                                items(relatedChats, key = { it.id }) { thread ->
                                    OmniSurfaceCard(
                                        onClick = {
                                            viewModel.selectThread(thread.id)
                                            onOpenChat()
                                        }
                                    ) {
                                        Text(thread.title, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            "${thread.messageCount} msgs · ${thread.providerUsed.ifBlank { "OmniAI" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // =====================================================================
        // STICKY PROMINENT "Git Auto-Push (N changes)" FLOATING ACTION PILL
        // =====================================================================
        AnimatedVisibility(
            visible = showStickyPushPill,
            enter = fadeIn(),
            exit = fadeOut(),
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(16.dp)
        ) {
            val targetProj = stickyPushProject
            ExtendedFloatingActionButton(
                onClick = {
                    if (targetProj != null && !isGitBusy) {
                        if (targetProj.githubOwner.isNotBlank() && targetProj.githubRepo.isNotBlank()) {
                            viewModel.pushProjectAtomically(targetProj, commitMessageInput)
                        } else {
                            viewModel.selectProject(targetProj.id)
                            viewingProjectDetail = true
                            selectedSubTab = 2
                        }
                    }
                },
                containerColor = EmeraldAccent,
                contentColor = colorScheme.background,
                shape = RoundedCornerShape(OmniDesignTokens.CapsuleRadius),
                icon = {
                    if (isGitBusy) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = colorScheme.background
                        )
                    } else {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null)
                    }
                },
                text = {
                    Text(
                        text = if (isGitBusy) "Pushing..." else "Git Auto-Push ($stickyPendingCount changes)",
                        fontWeight = FontWeight.SemiBold
                    )
                }
            )
        }
    }

    // =========================================================================
    // DIALOGS: CREATE/EDIT PROJECT, TASK, NEW FILE, AI SPLITTER, PRIVATE REPO
    // =========================================================================
    if (showProjectDialog) {
        val prev = editingProject
        var name by remember(prev) { mutableStateOf(prev?.name.orEmpty()) }
        var desc by remember(prev) { mutableStateOf(prev?.description.orEmpty()) }
        var type by remember(prev) { mutableStateOf(prev?.projectType ?: "ANDROID_APP") }
        var status by remember(prev) { mutableStateOf(prev?.status ?: "ACTIVE") }
        var owner by remember(prev) { mutableStateOf(prev?.githubOwner ?: settings.defaultGithubOwner) }
        var repo by remember(prev) { mutableStateOf(prev?.githubRepo.orEmpty()) }
        var branch by remember(prev) { mutableStateOf(prev?.githubBranch ?: settings.defaultGithubBranch) }
        var autoPush by remember(prev) { mutableStateOf(prev?.autoPushOnSave ?: false) }
        var linkedNicheId by remember(prev) { mutableStateOf(prev?.linkedNicheId ?: settings.activeNicheId) }

        AlertDialog(
            onDismissRequest = { showProjectDialog = false },
            containerColor = colorScheme.surface,
            title = { Text(if (prev == null) "New Project Workspace" else "Edit Project") },
            text = {
                LazyColumn(
                    modifier = Modifier.heightIn(max = 420.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    item {
                        OutlinedTextField(
                            value = name,
                            onValueChange = { name = it },
                            label = { Text("Project Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = desc,
                            onValueChange = { desc = it },
                            label = { Text("Description") },
                            maxLines = 3,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Text("Project Type", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PROJECT_TYPES.forEach { t ->
                                FilterChip(selected = type == t, onClick = { type = t }, label = { Text(t) })
                            }
                        }
                    }
                    item {
                        Text("Status", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            PROJECT_STATUSES.forEach { st ->
                                FilterChip(selected = status == st, onClick = { status = st }, label = { Text(st) })
                            }
                        }
                    }
                    item {
                        OutlinedTextField(
                            value = owner,
                            onValueChange = { owner = it },
                            label = { Text("GitHub Owner / Username") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = repo,
                            onValueChange = { repo = it },
                            label = { Text("GitHub Repo Name") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        OutlinedTextField(
                            value = branch,
                            onValueChange = { branch = it },
                            label = { Text("Branch (default: main)") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Auto-Push on Save", style = MaterialTheme.typography.bodyMedium)
                                Text(
                                    "Trigger WorkManager atomic push after Code Editor or AI Splitter writes",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            Switch(checked = autoPush, onCheckedChange = { autoPush = it })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createOrUpdateProject(
                            existingId = prev?.id ?: 0L,
                            name = name,
                            description = desc,
                            projectType = type,
                            linkedNicheId = linkedNicheId,
                            githubOwner = owner,
                            githubRepo = repo,
                            githubBranch = branch,
                            status = status,
                            autoPushOnSave = autoPush
                        )
                        showProjectDialog = false
                    },
                    enabled = name.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showProjectDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showTaskDialog && activeProject != null) {
        val prevTask = editingTask
        var title by remember(prevTask) { mutableStateOf(prevTask?.title.orEmpty()) }
        var details by remember(prevTask) { mutableStateOf(prevTask?.details.orEmpty()) }
        var col by remember(prevTask) { mutableStateOf(prevTask?.columnStatus ?: "TO_DO") }
        var prio by remember(prevTask) { mutableStateOf(prevTask?.priority ?: "MEDIUM") }

        AlertDialog(
            onDismissRequest = { showTaskDialog = false },
            containerColor = colorScheme.surface,
            title = { Text(if (prevTask == null) "Add Kanban Task" else "Edit Kanban Task") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = title,
                        onValueChange = { title = it },
                        label = { Text("Task Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = details,
                        onValueChange = { details = it },
                        label = { Text("Details") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Column", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        KANBAN_COLUMNS.forEach { c ->
                            FilterChip(selected = col == c, onClick = { col = c }, label = { Text(c.replace('_', ' ')) })
                        }
                    }
                    Text("Priority", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    FlowRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        TASK_PRIORITIES.forEach { p ->
                            FilterChip(selected = prio == p, onClick = { prio = p }, label = { Text(p) })
                        }
                    }
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addOrUpdateProjectTask(
                            projectId = activeProject!!.id,
                            taskId = prevTask?.id ?: 0L,
                            title = title,
                            details = details,
                            columnStatus = col,
                            priority = prio
                        )
                        showTaskDialog = false
                    },
                    enabled = title.isNotBlank()
                ) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showTaskDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNewFileDialog && activeProject != null) {
        AlertDialog(
            onDismissRequest = { showNewFileDialog = false },
            containerColor = colorScheme.surface,
            title = { Text(if (newFileIsFolder) "Create New Folder" else "Create New File") },
            text = {
                OutlinedTextField(
                    value = newFilePathInput,
                    onValueChange = { newFilePathInput = it },
                    label = { Text(if (newFileIsFolder) "Folder Path (e.g. src/utils)" else "Relative Path (e.g. app/src/Main.kt)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createProjectFileOrFolder(activeProject!!, newFilePathInput, newFileIsFolder)
                        showNewFileDialog = false
                    },
                    enabled = newFilePathInput.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewFileDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSplitterDialog && activeProject != null) {
        val parsedPreview = remember(splitterInputText) { AiMultiFileSplitter.parseMultiFileOutput(splitterInputText) }
        AlertDialog(
            onDismissRequest = { showSplitterDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Paste AI Multi-File Output (### FILE: ...)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste an AI response containing `### FILE: path/to/File.kt` headers (or fenced code blocks). All files will be safely written inside filesDir/projects/${activeProject!!.slug}/.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = splitterInputText,
                        onValueChange = { splitterInputText = it },
                        placeholder = { Text("### FILE: src/App.kt\n```kotlin\nfun main() {}\n```") },
                        textStyle = MaterialTheme.typography.bodySmall.copy(fontFamily = FontFamily.Monospace),
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(min = 160.dp, max = 260.dp)
                    )
                    Text(
                        text = "Detected ${parsedPreview.size} file(s): " +
                            parsedPreview.take(5).joinToString(", ") { it.relativePath },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (parsedPreview.isNotEmpty()) EmeraldAccent else colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.applyMultiFileSplitterOutput(activeProject!!, splitterInputText) {
                            showSplitterDialog = false
                        }
                    },
                    enabled = parsedPreview.isNotEmpty()
                ) { Text("Split & Write (${parsedPreview.size})") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showSplitterDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showCreateRepoDialog && activeProject != null) {
        AlertDialog(
            onDismissRequest = { showCreateRepoDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Create New Private GitHub Repo") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Calls POST /user/repos with private=true and auto_init=true using your Vault GitHub PAT.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = newRepoNameInput,
                        onValueChange = { newRepoNameInput = it },
                        label = { Text("Repository Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newRepoDescInput,
                        onValueChange = { newRepoDescInput = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createPrivateGitHubRepoForProject(activeProject!!, newRepoNameInput, newRepoDescInput)
                        showCreateRepoDialog = false
                    },
                    enabled = newRepoNameInput.isNotBlank()
                ) { Text("Create Private Repo") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showCreateRepoDialog = false }) { Text("Cancel") }
            }
        )
    }
}
