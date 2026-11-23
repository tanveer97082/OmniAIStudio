package com.omniai.studio.ui.screens

import android.content.Intent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.RocketLaunch
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarBorder
import androidx.compose.material.icons.filled.TableChart
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.ChatThreadEntity
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.data.local.ScriptWithScenesAndSocial
import com.omniai.studio.data.local.SocialPostEntity
import com.omniai.studio.ui.components.NicheEditorDialog
import com.omniai.studio.ui.components.NicheSelectorPill
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.LocalUiConfig
import com.omniai.studio.viewmodel.OmniAiViewModel
import com.omniai.studio.youtube.CommentAssistantSheet
import kotlinx.coroutines.launch
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

private val SCRIPT_STATUSES = listOf("DRAFT", "SCRIPTED", "RENDERING", "RENDERED", "PUBLISHED")
private val SOCIAL_PLATFORMS = listOf("YOUTUBE", "INSTAGRAM", "FACEBOOK")

/**
 * OmniAI-Studio v3.5 — Unified Content DB & Multi-Platform Social SEO Hub:
 * 1) Chats Archive Tab (Full-text LIKE search, Folders, Pinned/Archived, Bookmarked Replies, .md/.json export)
 * 2) Scripts & Scenes Tab (Normalized VideoScript/Scene DB, Revisions & Restore, Clone, Favorite Scene, Send to Kaggle GPU)
 * 3) YT / IG / FB Metadata Tab (Auto-Generate All 3 Platforms AI, 3 A/B Titles, Live Platform Limit Counters,
 *    Hashtag Packs, 1-Tap Copy, Mark Posted + Live URL, and WorkManager PublishReminderWorker Scheduler)
 */
@Composable
fun ContentHubScreen(
    viewModel: OmniAiViewModel,
    onResumeChat: () -> Unit,
    onOpenDirector: () -> Unit
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val uiConfig = LocalUiConfig.current

    val rawWorkspaceThreads by viewModel.allWorkspaceChatThreads.collectAsState()
    val chatSearchResults by viewModel.chatArchiveSearchResults.collectAsState()
    val bookmarkedMessages by viewModel.bookmarkedMessages.collectAsState()
    val rawScriptsWithRelations by viewModel.videoScriptsWithRelations.collectAsState()
    val rawSocialPosts by viewModel.socialPosts.collectAsState()
    val hashtagPacks by viewModel.hashtagPacks.collectAsState()
    val isGeneratingSocialSeo by viewModel.isGeneratingSocialSeo.collectAsState()
    val pendingOpenSocialPostId by viewModel.pendingOpenSocialPostId.collectAsState()
    val pendingOpenSocialPlatform by viewModel.pendingOpenSocialPlatform.collectAsState()
    val niches by viewModel.niches.collectAsState()
    val activeNiche by viewModel.activeNiche.collectAsState()
    val filterAllNiches by viewModel.filterAllNiches.collectAsState()
    val isAutoFillingNiche by viewModel.isAutoFillingNiche.collectAsState()
    val characterProfiles by viewModel.characterProfiles.collectAsState()
    val youTubeProfiles by viewModel.youTubeProfiles.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val isUploadingToYouTube by viewModel.isUploadingToYouTube.collectAsState()
    val youTubeUploadProgress by viewModel.youTubeUploadProgress.collectAsState()
    val youTubeUploadStatus by viewModel.youTubeUploadStatus.collectAsState()
    val youTubeComments by viewModel.youTubeComments.collectAsState()
    val isLoadingYouTubeComments by viewModel.isLoadingYouTubeComments.collectAsState()
    val youTubeCommentStatus by viewModel.youTubeCommentStatus.collectAsState()

    val allWorkspaceThreads = remember(rawWorkspaceThreads, activeNiche?.id, filterAllNiches) {
        if (filterAllNiches || activeNiche == null) rawWorkspaceThreads
        else rawWorkspaceThreads.filter { it.nicheId == activeNiche?.id }
    }
    val scriptsWithRelations = remember(rawScriptsWithRelations, activeNiche?.id, filterAllNiches) {
        if (filterAllNiches || activeNiche == null) rawScriptsWithRelations
        else rawScriptsWithRelations.filter { it.script.nicheId == activeNiche?.id }
    }
    val allSocialPosts = remember(rawSocialPosts, activeNiche?.id, filterAllNiches) {
        if (filterAllNiches || activeNiche == null) rawSocialPosts
        else rawSocialPosts.filter { it.nicheId == activeNiche?.id }
    }

    var activeSection by remember { mutableIntStateOf(0) } // 0 = Chats, 1 = Scripts, 2 = YT/IG/FB SEO
    var selectedScriptIdForSeo by remember { mutableStateOf<Long?>(null) }
    var selectedPlatform by remember { mutableStateOf("YOUTUBE") }
    var showNicheEditorDialog by remember { mutableStateOf(false) }
    var editingNiche by remember { mutableStateOf<NicheEntity?>(null) }

    // Chat Archive filters
    var chatSearchQuery by remember { mutableStateOf("") }
    var chatFilterMode by remember { mutableStateOf("ALL") } // ALL | PINNED | ARCHIVED | BOOKMARKS | FOLDER:<name>
    var editingThreadOrg by remember { mutableStateOf<ChatThreadEntity?>(null) }
    var pendingExportThreadId by remember { mutableStateOf<Long?>(null) }
    var pendingExportFormat by remember { mutableStateOf("MD") } // MD | JSON

    // Scripts & Scenes state
    var scriptSearchQuery by remember { mutableStateOf("") }
    var scriptStatusFilter by remember { mutableStateOf("ALL") }
    var expandedScriptIds by remember { mutableStateOf<Set<Long>>(emptySet()) }
    var historyDialogScript by remember { mutableStateOf<ScriptWithScenesAndSocial?>(null) }
    var showNewScriptDialog by remember { mutableStateOf(false) }
    var addSceneForScriptId by remember { mutableStateOf<Long?>(null) }

    // Hashtag pack & v5.3 Comment Assistant dialogs
    var showNewPackDialog by remember { mutableStateOf(false) }
    var showScheduleDialog by remember { mutableStateOf(false) }
    var showCommentAssistantSheet by remember { mutableStateOf(false) }
    var commentAssistantVideoId by remember { mutableStateOf("") }

    // Handle deep link from PublishReminderWorker notification
    LaunchedEffect(pendingOpenSocialPostId, allSocialPosts) {
        val targetId = pendingOpenSocialPostId
        if (targetId != null) {
            val matched = allSocialPosts.firstOrNull { it.id == targetId }
            activeSection = 2
            if (matched != null) {
                selectedScriptIdForSeo = matched.scriptId
                selectedPlatform = matched.platform.uppercase(Locale.US)
            } else if (!pendingOpenSocialPlatform.isNullOrBlank()) {
                selectedPlatform = pendingOpenSocialPlatform!!.uppercase(Locale.US)
            }
            viewModel.consumePendingOpenSocialPost()
        }
    }

    // SAF Launchers
    val exportCsvLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/csv")
    ) { uri ->
        if (uri != null) {
            viewModel.exportContentDbToCsvUri(uri)
        }
    }

    val exportBulkMdLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            viewModel.exportContentDbToMarkdownUri(uri)
        }
    }

    val exportSingleChatLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("*/*")
    ) { uri ->
        val tid = pendingExportThreadId
        if (uri != null && tid != null) {
            scope.launch {
                val payload = if (pendingExportFormat == "JSON") {
                    viewModel.buildChatThreadJson(tid)
                } else {
                    viewModel.buildChatThreadMarkdown(tid)
                }
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(payload.toByteArray(Charsets.UTF_8))
                }
                viewModel.postToast("Exported chat thread as .${pendingExportFormat.lowercase(Locale.US)}.")
            }
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(uiConfig.uiDensity.cardPaddingDp.dp)
    ) {
        // Header + 1-Tap Bulk CSV / Markdown Exporters
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text("Content DB & Social Hub", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${allWorkspaceThreads.size} chats · ${scriptsWithRelations.size} scripts · ${allSocialPosts.size} YT/IG/FB posts",
                    style = MaterialTheme.typography.labelMedium,
                    color = colorScheme.onSurfaceVariant
                )
            }
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                OutlinedButton(
                    onClick = {
                        exportCsvLauncher.launch("OmniAI_ContentDB_${System.currentTimeMillis()}.csv")
                    }
                ) {
                    Icon(Icons.Filled.TableChart, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("CSV")
                }
                IconButton(onClick = {
                    exportBulkMdLauncher.launch("OmniAI_ContentVault_${System.currentTimeMillis()}.md")
                }) {
                    Icon(Icons.Filled.Download, contentDescription = "Export Vault Markdown")
                }
            }
        }

        // v4.0 Master Niche Filter Pill
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(top = 6.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.SpaceBetween
        ) {
            NicheSelectorPill(
                niches = niches,
                activeNiche = activeNiche,
                filterAllNiches = filterAllNiches,
                showAllNichesToggle = true,
                onSelectNiche = { viewModel.selectNiche(it) },
                onToggleAllNiches = { viewModel.setFilterAllNiches(it) },
                onCreateNicheClick = {
                    editingNiche = null
                    showNicheEditorDialog = true
                },
                onEditActiveNicheClick = { niche ->
                    editingNiche = niche
                    showNicheEditorDialog = true
                }
            )
            Text(
                text = if (filterAllNiches) "Showing All Niches" else "Filtered by Niche",
                style = MaterialTheme.typography.labelSmall,
                color = colorScheme.onSurfaceVariant
            )
        }

        // 3-Section Unified Database Explorer Tabs
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(vertical = 8.dp),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            FilterChip(
                selected = activeSection == 0,
                onClick = { activeSection = 0 },
                label = { Text("Chats (${allWorkspaceThreads.size})") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = activeSection == 1,
                onClick = { activeSection = 1 },
                label = { Text("Scripts (${scriptsWithRelations.size})") },
                modifier = Modifier.weight(1f)
            )
            FilterChip(
                selected = activeSection == 2,
                onClick = { activeSection = 2 },
                label = { Text("YT/IG/FB SEO") },
                modifier = Modifier.weight(1f)
            )
        }

        HorizontalDivider(color = colorScheme.outline)
        Spacer(modifier = Modifier.height(8.dp))

        when (activeSection) {
            // =================================================================
            // SECTION 0: CHATS ARCHIVE TAB
            // =================================================================
            0 -> {
                val folders = remember(allWorkspaceThreads) {
                    allWorkspaceThreads.map { it.folderName.ifBlank { "General" } }.distinct().sorted()
                }
                val baseThreads = if (chatSearchQuery.isNotBlank()) chatSearchResults else allWorkspaceThreads
                val filteredThreads = remember(baseThreads, chatFilterMode) {
                    when {
                        chatFilterMode == "PINNED" -> baseThreads.filter { it.isPinned && !it.isArchived }
                        chatFilterMode == "ARCHIVED" -> baseThreads.filter { it.isArchived }
                        chatFilterMode.startsWith("FOLDER:") -> {
                            val folder = chatFilterMode.removePrefix("FOLDER:")
                            baseThreads.filter { it.folderName.equals(folder, ignoreCase = true) && !it.isArchived }
                        }
                        else -> baseThreads.filter { !it.isArchived }
                    }
                }

                OutlinedTextField(
                    value = chatSearchQuery,
                    onValueChange = { q ->
                        chatSearchQuery = q
                        viewModel.searchChatArchive(q)
                    },
                    leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                    placeholder = { Text("Full-text LIKE search across chat titles & messages...") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = chatFilterMode == "ALL",
                            onClick = { chatFilterMode = "ALL" },
                            label = { Text("All Active") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = chatFilterMode == "PINNED",
                            onClick = { chatFilterMode = "PINNED" },
                            label = { Text("Pinned") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = chatFilterMode == "ARCHIVED",
                            onClick = { chatFilterMode = "ARCHIVED" },
                            label = { Text("Archived") }
                        )
                    }
                    item {
                        FilterChip(
                            selected = chatFilterMode == "BOOKMARKS",
                            onClick = { chatFilterMode = "BOOKMARKS" },
                            leadingIcon = { Icon(Icons.Filled.Bookmark, contentDescription = null) },
                            label = { Text("Bookmarked (${bookmarkedMessages.size})") }
                        )
                    }
                    items(folders) { folder ->
                        val key = "FOLDER:$folder"
                        FilterChip(
                            selected = chatFilterMode == key,
                            onClick = { chatFilterMode = key },
                            label = { Text(folder) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    if (chatFilterMode == "BOOKMARKS") {
                        if (bookmarkedMessages.isEmpty()) {
                            item {
                                Text(
                                    "No bookmarked answers yet. Tap the bookmark icon on any AI reply in Chat Studio.",
                                    style = MaterialTheme.typography.bodyMedium,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        } else {
                            items(bookmarkedMessages, key = { it.id }) { msg ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                    border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f))
                                ) {
                                    Column(
                                        modifier = Modifier.padding(10.dp),
                                        verticalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(
                                                text = "${msg.role.uppercase(Locale.US)} · ${msg.modelName.ifBlank { msg.providerTag.ifBlank { "AI" } }} · ~${msg.tokenEstimate} tok",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = EmeraldAccent
                                            )
                                            Row {
                                                IconButton(onClick = {
                                                    clipboard.setText(AnnotatedString(msg.content))
                                                    viewModel.postToast("Copied bookmarked reply.")
                                                }) {
                                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy")
                                                }
                                                IconButton(onClick = { viewModel.toggleMessageBookmark(msg.id, false) }) {
                                                    Icon(Icons.Filled.Bookmark, contentDescription = "Remove Bookmark", tint = AmberWarning)
                                                }
                                            }
                                        }
                                        Text(msg.content.take(360), style = MaterialTheme.typography.bodyMedium)
                                        TextButton(onClick = {
                                            viewModel.selectThread(msg.threadId)
                                            onResumeChat()
                                        }) {
                                            Text("Jump to Thread #${msg.threadId}")
                                        }
                                    }
                                }
                            }
                        }
                    } else if (filteredThreads.isEmpty()) {
                        item {
                            Text(
                                "No matching chats found in this filter.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filteredThreads, key = { it.id }) { thread ->
                            val dateFmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.US) }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                border = BorderStroke(
                                    1.dp,
                                    if (thread.isPinned) colorScheme.primary.copy(alpha = 0.7f) else colorScheme.outline
                                )
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(
                                                text = (if (thread.isPinned) "📌 " else "") + thread.title,
                                                style = MaterialTheme.typography.titleSmall
                                            )
                                            Text(
                                                text = "Folder: ${thread.folderName} · ${thread.messageCount} msgs · ${thread.providerUsed.ifBlank { "OmniAI" }} · ${dateFmt.format(Date(thread.updatedAt))}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                            if (thread.tagsCsv.isNotBlank()) {
                                                Text(
                                                    text = "Tags: ${thread.tagsCsv}",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.primary
                                                )
                                            }
                                        }
                                        Row {
                                            IconButton(onClick = { viewModel.toggleThreadPinned(thread) }) {
                                                Icon(
                                                    Icons.Filled.PushPin,
                                                    contentDescription = "Toggle Pin",
                                                    tint = if (thread.isPinned) EmeraldAccent else colorScheme.onSurfaceVariant
                                                )
                                            }
                                            IconButton(onClick = { viewModel.toggleThreadArchived(thread) }) {
                                                Icon(
                                                    Icons.Filled.Archive,
                                                    contentDescription = "Toggle Archive",
                                                    tint = if (thread.isArchived) AmberWarning else colorScheme.onSurfaceVariant
                                                )
                                            }
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.selectThread(thread.id)
                                                onResumeChat()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Resume")
                                        }
                                        OutlinedButton(onClick = { editingThreadOrg = thread }) {
                                            Icon(Icons.Filled.Edit, contentDescription = null)
                                        }
                                        OutlinedButton(onClick = {
                                            pendingExportThreadId = thread.id
                                            pendingExportFormat = "MD"
                                            exportSingleChatLauncher.launch("OmniAI_Chat_${thread.id}.md")
                                        }) {
                                            Text(".md")
                                        }
                                        OutlinedButton(onClick = {
                                            pendingExportThreadId = thread.id
                                            pendingExportFormat = "JSON"
                                            exportSingleChatLauncher.launch("OmniAI_Chat_${thread.id}.json")
                                        }) {
                                            Text(".json")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // SECTION 1: SCRIPTS & SCENES TAB
            // =================================================================
            1 -> {
                val filteredScripts = remember(scriptsWithRelations, scriptSearchQuery, scriptStatusFilter) {
                    scriptsWithRelations.filter { item ->
                        val statusOk = scriptStatusFilter == "ALL" || item.script.status.equals(scriptStatusFilter, ignoreCase = true)
                        val q = scriptSearchQuery.trim()
                        val queryOk = q.isBlank() ||
                            item.script.title.contains(q, ignoreCase = true) ||
                            item.script.topic.contains(q, ignoreCase = true) ||
                            item.scenes.any { sc ->
                                sc.visualPrompt.contains(q, ignoreCase = true) ||
                                    sc.narration.contains(q, ignoreCase = true)
                            }
                        statusOk && queryOk
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = scriptSearchQuery,
                        onValueChange = { scriptSearchQuery = it },
                        leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                        placeholder = { Text("Search scripts, visual prompts, narrations...") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(onClick = { showNewScriptDialog = true }) {
                        Icon(Icons.Filled.Add, contentDescription = "New Video Script")
                    }
                }

                LazyRow(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 8.dp),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    item {
                        FilterChip(
                            selected = scriptStatusFilter == "ALL",
                            onClick = { scriptStatusFilter = "ALL" },
                            label = { Text("ALL") }
                        )
                    }
                    items(SCRIPT_STATUSES) { status ->
                        FilterChip(
                            selected = scriptStatusFilter == status,
                            onClick = { scriptStatusFilter = status },
                            label = { Text(status) }
                        )
                    }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (filteredScripts.isEmpty()) {
                        item {
                            Text(
                                "No normalized video scripts yet. Save a storyboard in Video Director or tap '+' to create one.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    } else {
                        items(filteredScripts, key = { it.script.id }) { bundle ->
                            val script = bundle.script
                            val orderedScenes = remember(bundle.scenes) { bundle.scenes.sortedBy { it.sceneOrder } }
                            val isExpanded = expandedScriptIds.contains(script.id)
                            val favScene = orderedScenes.firstOrNull { it.isFavoriteScene }

                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                border = BorderStroke(1.dp, colorScheme.outline)
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(script.title, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "${script.aspectRatio} · ${script.styleTag} · ${orderedScenes.size} scenes · ${bundle.socialPosts.size} SEO kits",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        Surface(
                                            color = when (script.status) {
                                                "PUBLISHED", "RENDERED" -> EmeraldAccent.copy(alpha = 0.18f)
                                                "RENDERING" -> AmberWarning.copy(alpha = 0.2f)
                                                else -> colorScheme.surfaceVariant
                                            },
                                            shape = RoundedCornerShape(6.dp)
                                        ) {
                                            TextButton(
                                                onClick = {
                                                    val idx = SCRIPT_STATUSES.indexOf(script.status).coerceAtLeast(0)
                                                    val next = SCRIPT_STATUSES[(idx + 1) % SCRIPT_STATUSES.size]
                                                    viewModel.updateVideoScriptStatus(script.id, next)
                                                }
                                            ) {
                                                Text(script.status, style = MaterialTheme.typography.labelMedium)
                                            }
                                        }
                                    }

                                    Text(
                                        text = script.topic,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                expandedScriptIds = if (isExpanded) {
                                                    expandedScriptIds - script.id
                                                } else {
                                                    expandedScriptIds + script.id
                                                }
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(
                                                if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                                contentDescription = null
                                            )
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Scenes")
                                        }
                                        OutlinedButton(onClick = { historyDialogScript = bundle }) {
                                            Icon(Icons.Filled.History, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("${bundle.revisions.size}")
                                        }
                                        OutlinedButton(onClick = { viewModel.cloneVideoScript(script.id) }) {
                                            Text("Clone")
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            onClick = {
                                                viewModel.sendNormalizedScriptToKaggleDirector(script.id)
                                                onOpenDirector()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.RocketLaunch, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Send to Kaggle GPU")
                                        }
                                        OutlinedButton(onClick = {
                                            selectedScriptIdForSeo = script.id
                                            activeSection = 2
                                        }) {
                                            Text("SEO Kit")
                                        }
                                        IconButton(onClick = { viewModel.deleteVideoScript(script) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Script", tint = CrimsonDanger)
                                        }
                                    }

                                    if (favScene != null) {
                                        OutlinedButton(
                                            onClick = {
                                                val clip = "Scene ${favScene.sceneOrder} (${favScene.cameraMovement}, ${favScene.durationSec}s)\nVisual: ${favScene.visualPrompt}\nNarration: ${favScene.narration}"
                                                clipboard.setText(AnnotatedString(clip))
                                                viewModel.postToast("Copied Favorite Scene #${favScene.sceneOrder}.")
                                            },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Icon(Icons.Filled.Star, contentDescription = null, tint = AmberWarning)
                                            Spacer(modifier = Modifier.width(6.dp))
                                            Text("Copy Favorite Scene (#${favScene.sceneOrder})")
                                        }
                                    }

                                    if (isExpanded) {
                                        HorizontalDivider(color = colorScheme.outline)
                                        orderedScenes.forEach { scene ->
                                            Surface(
                                                color = colorScheme.surfaceVariant,
                                                border = BorderStroke(1.dp, colorScheme.outline),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(8.dp),
                                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                                ) {
                                                    Row(
                                                        modifier = Modifier.fillMaxWidth(),
                                                        horizontalArrangement = Arrangement.SpaceBetween,
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        Text(
                                                            "Scene ${scene.sceneOrder} · ${scene.durationSec}s · ${scene.cameraMovement}",
                                                            style = MaterialTheme.typography.labelMedium,
                                                            color = colorScheme.primary
                                                        )
                                                        Row {
                                                            IconButton(onClick = {
                                                                viewModel.toggleSceneFavorite(scene.id, !scene.isFavoriteScene)
                                                            }) {
                                                                Icon(
                                                                    if (scene.isFavoriteScene) Icons.Filled.Star else Icons.Filled.StarBorder,
                                                                    contentDescription = "Favorite Scene",
                                                                    tint = if (scene.isFavoriteScene) AmberWarning else colorScheme.onSurfaceVariant
                                                                )
                                                            }
                                                            IconButton(onClick = {
                                                                clipboard.setText(
                                                                    AnnotatedString("Visual: ${scene.visualPrompt}\nNarration: ${scene.narration}")
                                                                )
                                                                viewModel.postToast("Copied Scene ${scene.sceneOrder}.")
                                                            }) {
                                                                Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Scene")
                                                            }
                                                        }
                                                    }
                                                    Text("Visual: ${scene.visualPrompt}", style = MaterialTheme.typography.bodySmall)
                                                    Text(
                                                        "VO: ${scene.narration}",
                                                        style = MaterialTheme.typography.bodySmall,
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        }
                                        TextButton(onClick = { addSceneForScriptId = script.id }) {
                                            Icon(Icons.Filled.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add Scene to Script (Auto-Saves Revision)")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }

            // =================================================================
            // SECTION 2: YT / IG / FB MULTI-PLATFORM SOCIAL SEO STUDIO
            // =================================================================
            else -> {
                val activeScriptBundle = remember(scriptsWithRelations, selectedScriptIdForSeo) {
                    scriptsWithRelations.firstOrNull { it.script.id == selectedScriptIdForSeo }
                }
                val currentPost: SocialPostEntity? = remember(allSocialPosts, selectedScriptIdForSeo, selectedPlatform) {
                    allSocialPosts.firstOrNull { post ->
                        post.scriptId == selectedScriptIdForSeo &&
                            post.platform.equals(selectedPlatform, ignoreCase = true)
                    }
                }

                var titleInput by remember(currentPost?.id, selectedScriptIdForSeo, selectedPlatform) {
                    mutableStateOf(currentPost?.title.orEmpty())
                }
                var hookInput by remember(currentPost?.id, selectedScriptIdForSeo, selectedPlatform) {
                    mutableStateOf(currentPost?.hookLine.orEmpty())
                }
                var descInput by remember(currentPost?.id, selectedScriptIdForSeo, selectedPlatform) {
                    mutableStateOf(currentPost?.description.orEmpty())
                }
                var tagsInput by remember(currentPost?.id, selectedScriptIdForSeo, selectedPlatform) {
                    mutableStateOf(currentPost?.hashtags.orEmpty())
                }
                var pinnedInput by remember(currentPost?.id, selectedScriptIdForSeo, selectedPlatform) {
                    mutableStateOf(currentPost?.pinnedComment.orEmpty())
                }
                var ctaInput by remember(currentPost?.id, selectedScriptIdForSeo, selectedPlatform) {
                    mutableStateOf(currentPost?.callToAction.orEmpty())
                }
                var publishedUrlInput by remember(currentPost?.id, selectedScriptIdForSeo, selectedPlatform) {
                    mutableStateOf(currentPost?.publishedUrl.orEmpty())
                }
                var publishStatus by remember(currentPost?.id, selectedScriptIdForSeo, selectedPlatform) {
                    mutableStateOf(currentPost?.publishStatus ?: "DRAFT")
                }

                val abTitles = remember(currentPost?.abTitlesJson) {
                    viewModel.parseAbTitles(currentPost?.abTitlesJson.orEmpty())
                }
                val hashtagCount = remember(tagsInput) {
                    tagsInput.split(Regex("\\s+")).count { it.startsWith("#") && it.length > 1 }
                }

                LazyColumn(
                    modifier = Modifier.fillMaxSize(),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    // Target Script Selector (or Standalone Post)
                    item {
                        Text("Target Script / Standalone Post", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        LazyRow(
                            modifier = Modifier
                                .fillMaxWidth()
                                .padding(top = 4.dp),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            item {
                                FilterChip(
                                    selected = selectedScriptIdForSeo == null,
                                    onClick = { selectedScriptIdForSeo = null },
                                    label = { Text("Standalone Post") }
                                )
                            }
                            items(scriptsWithRelations, key = { it.script.id }) { item ->
                                FilterChip(
                                    selected = selectedScriptIdForSeo == item.script.id,
                                    onClick = { selectedScriptIdForSeo = item.script.id },
                                    label = { Text(item.script.title.take(22), maxLines = 1) }
                                )
                            }
                        }
                    }

                    // Segmented Platform Switcher: [YouTube | Instagram | Facebook]
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            SOCIAL_PLATFORMS.forEach { platform ->
                                val label = when (platform) {
                                    "YOUTUBE" -> "YouTube"
                                    "INSTAGRAM" -> "Instagram"
                                    else -> "Facebook"
                                }
                                FilterChip(
                                    selected = selectedPlatform == platform,
                                    onClick = { selectedPlatform = platform },
                                    label = { Text(label) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    // Auto-Generate All 3 Platforms (AI)
                    item {
                        Button(
                            onClick = {
                                val targetTitle = activeScriptBundle?.script?.title ?: titleInput.ifBlank { "Creator AI Video" }
                                val targetTopic = activeScriptBundle?.script?.topic ?: descInput.ifBlank { targetTitle }
                                val scenes = activeScriptBundle?.scenes?.sortedBy { it.sceneOrder }?.map {
                                    "${it.visualPrompt} — VO: ${it.narration}"
                                }.orEmpty()
                                viewModel.generateAllThreePlatformsSeo(
                                    scriptId = selectedScriptIdForSeo,
                                    scriptTitle = targetTitle,
                                    topic = targetTopic,
                                    sceneSummaries = scenes
                                )
                            },
                            enabled = !isGeneratingSocialSeo,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AutoAwesome, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(
                                if (isGeneratingSocialSeo) "Generating YT + IG + FB SEO JSON..."
                                else "Auto-Generate All 3 Platforms (AI)"
                            )
                        }
                        if (isGeneratingSocialSeo) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth().padding(top = 6.dp))
                        }
                    }

                    // 3 A/B Viral Title Options
                    if (abTitles.isNotEmpty()) {
                        item {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f))
                            ) {
                                Column(
                                    modifier = Modifier.padding(10.dp),
                                    verticalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Text("A/B Viral Title / Hook Variants (Tap to Apply)", style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                                    abTitles.forEachIndexed { idx, variant ->
                                        OutlinedButton(
                                            onClick = { titleInput = variant },
                                            modifier = Modifier.fillMaxWidth()
                                        ) {
                                            Text("Option ${('A' + idx)}: $variant", maxLines = 2)
                                        }
                                    }
                                }
                            }
                        }
                    }

                    // Live Editable Metadata Fields with Real-Time Platform Limit Counters
                    item {
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                            border = BorderStroke(1.dp, colorScheme.outline)
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                val ytTitleOver = selectedPlatform == "YOUTUBE" && titleInput.length > 100
                                val ytDescOver = selectedPlatform == "YOUTUBE" && descInput.length > 5000
                                val igCapOver = selectedPlatform == "INSTAGRAM" && descInput.length > 2200
                                val igTagsOver = selectedPlatform == "INSTAGRAM" && hashtagCount > 30

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = when (selectedPlatform) {
                                            "YOUTUBE" -> "YouTube Limits: Title ${titleInput.length}/100 · Desc ${descInput.length}/5000"
                                            "INSTAGRAM" -> "Instagram Limits: Caption ${descInput.length}/2200 · Tags $hashtagCount/30"
                                            else -> "Facebook SEO: Title ${titleInput.length} · Desc ${descInput.length} · Tags $hashtagCount"
                                        },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = if (ytTitleOver || ytDescOver || igCapOver || igTagsOver) CrimsonDanger else EmeraldAccent
                                    )
                                }

                                OutlinedTextField(
                                    value = titleInput,
                                    onValueChange = { titleInput = it },
                                    label = {
                                        Text(
                                            if (selectedPlatform == "YOUTUBE") "YouTube Title (${titleInput.length}/100)"
                                            else if (selectedPlatform == "INSTAGRAM") "Reel Cover Headline"
                                            else "Facebook Video Title"
                                        )
                                    },
                                    isError = ytTitleOver,
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = hookInput,
                                    onValueChange = { hookInput = it },
                                    label = { Text("Opening 3-Second Hook Line") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = descInput,
                                    onValueChange = { descInput = it },
                                    label = {
                                        Text(
                                            when (selectedPlatform) {
                                                "YOUTUBE" -> "Description (${descInput.length}/5000)"
                                                "INSTAGRAM" -> "Caption (${descInput.length}/2200)"
                                                else -> "Facebook Post Description"
                                            }
                                        )
                                    },
                                    isError = ytDescOver || igCapOver,
                                    minLines = 3,
                                    maxLines = 6,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = tagsInput,
                                    onValueChange = { tagsInput = it },
                                    label = {
                                        Text(
                                            if (selectedPlatform == "INSTAGRAM") "Hashtags ($hashtagCount/30)"
                                            else "Hashtags ($hashtagCount tags)"
                                        )
                                    },
                                    isError = igTagsOver,
                                    minLines = 2,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // 1-Tap Append Hashtag Pack Chips
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("1-Tap Append Hashtag Packs", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                                    TextButton(onClick = { showNewPackDialog = true }) {
                                        Text("+ New Pack")
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    items(hashtagPacks, key = { it.id }) { pack ->
                                        FilterChip(
                                            selected = pack.platform.equals(selectedPlatform, ignoreCase = true),
                                            onClick = {
                                                val merged = (tagsInput.trim() + " " + pack.tagsText.trim())
                                                    .split(Regex("\\s+"))
                                                    .filter { it.isNotBlank() }
                                                    .distinct()
                                                    .joinToString(" ")
                                                tagsInput = merged
                                                viewModel.recordHashtagPackUsed(pack.id)
                                            },
                                            label = { Text("+ ${pack.packName} (${pack.useCount})") }
                                        )
                                    }
                                }

                                OutlinedTextField(
                                    value = pinnedInput,
                                    onValueChange = { pinnedInput = it },
                                    label = { Text("Pinned Comment") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = ctaInput,
                                    onValueChange = { ctaInput = it },
                                    label = { Text("Call To Action") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                OutlinedTextField(
                                    value = publishedUrlInput,
                                    onValueChange = { publishedUrlInput = it },
                                    label = { Text("Live Published URL Tracker (Optional)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )

                                // Save + Mark as Posted + Schedule Reminder
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Button(
                                        onClick = {
                                            viewModel.saveSocialPost(
                                                existingId = currentPost?.id ?: 0L,
                                                scriptId = selectedScriptIdForSeo,
                                                platform = selectedPlatform,
                                                title = titleInput,
                                                abTitlesJson = currentPost?.abTitlesJson ?: "[]",
                                                hookLine = hookInput,
                                                description = descInput,
                                                hashtags = tagsInput,
                                                pinnedComment = pinnedInput,
                                                callToAction = ctaInput,
                                                publishStatus = publishStatus,
                                                scheduledAtMillis = currentPost?.scheduledAtMillis,
                                                publishedUrl = publishedUrlInput
                                            )
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Save Metadata")
                                    }
                                    FilterChip(
                                        selected = publishStatus == "POSTED",
                                        onClick = {
                                            val next = if (publishStatus == "POSTED") "DRAFT" else "POSTED"
                                            publishStatus = next
                                            viewModel.saveSocialPost(
                                                existingId = currentPost?.id ?: 0L,
                                                scriptId = selectedScriptIdForSeo,
                                                platform = selectedPlatform,
                                                title = titleInput,
                                                abTitlesJson = currentPost?.abTitlesJson ?: "[]",
                                                hookLine = hookInput,
                                                description = descInput,
                                                hashtags = tagsInput,
                                                pinnedComment = pinnedInput,
                                                callToAction = ctaInput,
                                                publishStatus = next,
                                                scheduledAtMillis = currentPost?.scheduledAtMillis,
                                                publishedUrl = publishedUrlInput
                                            )
                                        },
                                        leadingIcon = { Icon(Icons.Filled.CheckCircle, contentDescription = null) },
                                        label = { Text(if (publishStatus == "POSTED") "Posted [✓]" else "Mark as Posted [✓]") }
                                    )
                                }

                                // 1-Tap Platform Copy Buttons & Share
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(titleInput))
                                            viewModel.postToast("Copied $selectedPlatform Title.")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Copy Title")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val full = buildString {
                                                if (hookInput.isNotBlank()) appendLine(hookInput)
                                                if (descInput.isNotBlank()) appendLine(descInput)
                                                if (ctaInput.isNotBlank()) appendLine("\n$ctaInput")
                                                if (tagsInput.isNotBlank()) appendLine("\n$tagsInput")
                                            }.trim()
                                            clipboard.setText(AnnotatedString(full))
                                            viewModel.postToast("Copied Full Description + Hashtags.")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Desc + Tags")
                                    }
                                }

                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    OutlinedButton(
                                        onClick = {
                                            clipboard.setText(AnnotatedString(pinnedInput))
                                            viewModel.postToast("Copied Pinned Comment.")
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Text("Copy Pinned Comment")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            val shareText = "$titleInput\n\n$descInput\n\n$tagsInput".trim()
                                            val sendIntent = Intent(Intent.ACTION_SEND).apply {
                                                type = "text/plain"
                                                putExtra(Intent.EXTRA_SUBJECT, titleInput)
                                                putExtra(Intent.EXTRA_TEXT, shareText)
                                            }
                                            context.startActivity(Intent.createChooser(sendIntent, "Share $selectedPlatform SEO Kit"))
                                        }
                                    ) {
                                        Icon(Icons.Filled.Share, contentDescription = "Share SEO Text")
                                    }
                                }

                                // v5.3 Direct YouTube Data API v3 Upload + AI Comment Reply Assistant
                                if (selectedPlatform == "YOUTUBE") {
                                    HorizontalDivider(color = colorScheme.outline)
                                    val latestMp4 = downloads.firstOrNull { it.status == "COMPLETED" }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        Button(
                                            enabled = !isUploadingToYouTube && latestMp4 != null,
                                            onClick = {
                                                val targetFile = latestMp4?.filePath ?: return@Button
                                                val fullDesc = buildString {
                                                    if (hookInput.isNotBlank()) appendLine(hookInput)
                                                    if (descInput.isNotBlank()) appendLine(descInput)
                                                    if (ctaInput.isNotBlank()) appendLine("\n$ctaInput")
                                                    if (tagsInput.isNotBlank()) appendLine("\n$tagsInput")
                                                }.trim()
                                                val tagList = tagsInput
                                                    .split(Regex("[\\s,]+"))
                                                    .map { it.trim().removePrefix("#") }
                                                    .filter { it.isNotBlank() }
                                                viewModel.uploadVideoToYouTube(
                                                    videoFilePath = targetFile,
                                                    title = titleInput.ifBlank { activeScriptBundle?.script?.title ?: "OmniAI Video" },
                                                    description = fullDesc,
                                                    tags = tagList,
                                                    firstComment = pinnedInput,
                                                    privacyStatus = appSettings.youtubeDefaultPrivacy,
                                                    socialPostId = currentPost?.id
                                                )
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text(if (isUploadingToYouTube) "Uploading $youTubeUploadProgress%" else "Direct Upload YT")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                commentAssistantVideoId = publishedUrlInput.trim()
                                                showCommentAssistantSheet = true
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.Forum, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Comment AI")
                                        }
                                    }
                                    if (isUploadingToYouTube) {
                                        LinearProgressIndicator(
                                            progress = { (youTubeUploadProgress / 100f).coerceIn(0f, 1f) },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                    }
                                    if (youTubeUploadStatus.isNotBlank()) {
                                        Text(
                                            text = youTubeUploadStatus,
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldAccent
                                        )
                                    }
                                }

                                HorizontalDivider(color = colorScheme.outline)

                                // Human-in-the-Loop Posting Reminder Scheduler
                                val scheduledText = currentPost?.scheduledAtMillis?.let { ms ->
                                    SimpleDateFormat("MMM dd, HH:mm", Locale.US).format(Date(ms))
                                }
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text("Posting Reminder (WorkManager)", style = MaterialTheme.typography.labelMedium)
                                        Text(
                                            text = if (scheduledText != null) "Scheduled: $scheduledText ($publishStatus)" else "Not scheduled yet",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = if (scheduledText != null) EmeraldAccent else colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(onClick = { showScheduleDialog = true }) {
                                        Icon(Icons.Filled.NotificationsActive, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("Schedule")
                                    }
                                }
                            }
                        }
                    }
                }

                // Posting Reminder Schedule Picker Dialog
                if (showScheduleDialog) {
                    var customDelayMinutes by remember { mutableStateOf("60") }
                    AlertDialog(
                        onDismissRequest = { showScheduleDialog = false },
                        containerColor = colorScheme.surface,
                        title = { Text("Schedule $selectedPlatform Posting Reminder") },
                        text = {
                            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                Text(
                                    "Schedules a WorkManager OneTimeWorkRequest notification so you can 1-tap copy & publish at peak engagement time.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    listOf(15 to "+15m", 60 to "+1h", 180 to "+3h").forEach { (mins, lbl) ->
                                        OutlinedButton(
                                            onClick = {
                                                viewModel.scheduleSocialPostReminder(
                                                    existingId = currentPost?.id ?: 0L,
                                                    scriptId = selectedScriptIdForSeo,
                                                    platform = selectedPlatform,
                                                    title = titleInput.ifBlank { activeScriptBundle?.script?.title ?: "Creator Post" },
                                                    abTitlesJson = currentPost?.abTitlesJson ?: "[]",
                                                    hookLine = hookInput,
                                                    description = descInput,
                                                    hashtags = tagsInput,
                                                    pinnedComment = pinnedInput,
                                                    callToAction = ctaInput,
                                                    publishedUrl = publishedUrlInput,
                                                    delayMillis = mins * 60_000L
                                                )
                                                publishStatus = "SCHEDULED"
                                                showScheduleDialog = false
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) { Text(lbl) }
                                    }
                                }
                                OutlinedButton(
                                    onClick = {
                                        val cal = Calendar.getInstance().apply {
                                            add(Calendar.DAY_OF_YEAR, 1)
                                            set(Calendar.HOUR_OF_DAY, 9)
                                            set(Calendar.MINUTE, 0)
                                            set(Calendar.SECOND, 0)
                                        }
                                        val delayMs = (cal.timeInMillis - System.currentTimeMillis()).coerceAtLeast(60_000L)
                                        viewModel.scheduleSocialPostReminder(
                                            existingId = currentPost?.id ?: 0L,
                                            scriptId = selectedScriptIdForSeo,
                                            platform = selectedPlatform,
                                            title = titleInput.ifBlank { activeScriptBundle?.script?.title ?: "Creator Post" },
                                            abTitlesJson = currentPost?.abTitlesJson ?: "[]",
                                            hookLine = hookInput,
                                            description = descInput,
                                            hashtags = tagsInput,
                                            pinnedComment = pinnedInput,
                                            callToAction = ctaInput,
                                            publishedUrl = publishedUrlInput,
                                            delayMillis = delayMs
                                        )
                                        publishStatus = "SCHEDULED"
                                        showScheduleDialog = false
                                    },
                                    modifier = Modifier.fillMaxWidth()
                                ) { Text("Tomorrow 09:00 AM") }

                                OutlinedTextField(
                                    value = customDelayMinutes,
                                    onValueChange = { customDelayMinutes = it.filter { ch -> ch.isDigit() } },
                                    label = { Text("Custom Delay (Minutes from now)") },
                                    singleLine = true,
                                    modifier = Modifier.fillMaxWidth()
                                )
                            }
                        },
                        confirmButton = {
                            Button(onClick = {
                                val mins = customDelayMinutes.toLongOrNull()?.coerceAtLeast(1L) ?: 60L
                                viewModel.scheduleSocialPostReminder(
                                    existingId = currentPost?.id ?: 0L,
                                    scriptId = selectedScriptIdForSeo,
                                    platform = selectedPlatform,
                                    title = titleInput.ifBlank { activeScriptBundle?.script?.title ?: "Creator Post" },
                                    abTitlesJson = currentPost?.abTitlesJson ?: "[]",
                                    hookLine = hookInput,
                                    description = descInput,
                                    hashtags = tagsInput,
                                    pinnedComment = pinnedInput,
                                    callToAction = ctaInput,
                                    publishedUrl = publishedUrlInput,
                                    delayMillis = mins * 60_000L
                                )
                                publishStatus = "SCHEDULED"
                                showScheduleDialog = false
                            }) { Text("Set Reminder") }
                        },
                        dismissButton = {
                            OutlinedButton(onClick = { showScheduleDialog = false }) { Text("Cancel") }
                        }
                    )
                }
            }
        }
    }

    // Dialog: Edit Chat Thread Folder & Tags
    editingThreadOrg?.let { thread ->
        var folderInput by remember(thread.id) { mutableStateOf(thread.folderName) }
        var tagsCsvInput by remember(thread.id) { mutableStateOf(thread.tagsCsv) }
        AlertDialog(
            onDismissRequest = { editingThreadOrg = null },
            containerColor = colorScheme.surface,
            title = { Text("Organize Chat Thread") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = folderInput,
                        onValueChange = { folderInput = it },
                        label = { Text("Folder Name (e.g. Research, Scripts, General)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tagsCsvInput,
                        onValueChange = { tagsCsvInput = it },
                        label = { Text("Comma-Separated Tags (e.g. kotlin,seo,ideas)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(onClick = {
                    viewModel.updateThreadOrganization(thread.id, folderInput, tagsCsvInput)
                    editingThreadOrg = null
                }) { Text("Save") }
            },
            dismissButton = {
                OutlinedButton(onClick = { editingThreadOrg = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Script Revision History & 1-Tap Restore
    historyDialogScript?.let { bundle ->
        val fmt = remember { SimpleDateFormat("MMM dd, HH:mm:ss", Locale.US) }
        AlertDialog(
            onDismissRequest = { historyDialogScript = null },
            containerColor = colorScheme.surface,
            title = { Text("Version History · ${bundle.script.title}") },
            text = {
                LazyColumn(
                    modifier = Modifier.height(280.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    val orderedRevs = bundle.revisions.sortedByDescending { it.savedAt }
                    if (orderedRevs.isEmpty()) {
                        item {
                            Text("No revisions saved yet.", color = colorScheme.onSurfaceVariant)
                        }
                    } else {
                        items(orderedRevs, key = { it.id }) { rev ->
                            Surface(
                                color = colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, colorScheme.outline),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(rev.note, style = MaterialTheme.typography.bodyMedium)
                                        Text(
                                            fmt.format(Date(rev.savedAt)),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                    OutlinedButton(onClick = {
                                        viewModel.restoreScriptRevision(bundle.script.id, rev)
                                        historyDialogScript = null
                                    }) {
                                        Text("Restore")
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { historyDialogScript = null }) { Text("Close") }
            }
        )
    }

    // Dialog: Create New Normalized Video Script
    if (showNewScriptDialog) {
        var newTitle by remember { mutableStateOf("") }
        var newTopic by remember { mutableStateOf("") }
        var newAspect by remember { mutableStateOf("9:16") }
        var newStyle by remember { mutableStateOf("Cinematic Realism") }
        AlertDialog(
            onDismissRequest = { showNewScriptDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("New Video Script Record") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = newTitle,
                        onValueChange = { newTitle = it },
                        label = { Text("Script Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newTopic,
                        onValueChange = { newTopic = it },
                        label = { Text("Topic / Concept") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(selected = newAspect == "9:16", onClick = { newAspect = "9:16" }, label = { Text("9:16 Shorts") })
                        FilterChip(selected = newAspect == "16:9", onClick = { newAspect = "16:9" }, label = { Text("16:9 YouTube") })
                    }
                    OutlinedTextField(
                        value = newStyle,
                        onValueChange = { newStyle = it },
                        label = { Text("Style Tag") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.createNormalizedVideoScript(newTitle, newTopic, newAspect, newStyle)
                        showNewScriptDialog = false
                    },
                    enabled = newTitle.isNotBlank()
                ) { Text("Create") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewScriptDialog = false }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Add Scene to Existing Script
    addSceneForScriptId?.let { sid ->
        var visual by remember { mutableStateOf("") }
        var narration by remember { mutableStateOf("") }
        var camera by remember { mutableStateOf("Slow Cinematic Push-In") }
        var duration by remember { mutableStateOf("5") }
        AlertDialog(
            onDismissRequest = { addSceneForScriptId = null },
            containerColor = colorScheme.surface,
            title = { Text("Add Scene to Script #$sid") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = visual,
                        onValueChange = { visual = it },
                        label = { Text("Visual Prompt (Wan 2.1)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = narration,
                        onValueChange = { narration = it },
                        label = { Text("Spoken Narration") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = camera,
                        onValueChange = { camera = it },
                        label = { Text("Camera Movement") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = duration,
                        onValueChange = { duration = it.filter { c -> c.isDigit() } },
                        label = { Text("Duration (Seconds)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.addSceneToVideoScript(
                            scriptId = sid,
                            visualPrompt = visual,
                            narration = narration,
                            cameraMovement = camera,
                            durationSec = duration.toIntOrNull()?.coerceIn(2, 15) ?: 5
                        )
                        addSceneForScriptId = null
                    },
                    enabled = visual.isNotBlank()
                ) { Text("Save Scene") }
            },
            dismissButton = {
                OutlinedButton(onClick = { addSceneForScriptId = null }) { Text("Cancel") }
            }
        )
    }

    // Dialog: Create New Hashtag Pack
    if (showNewPackDialog) {
        var packName by remember { mutableStateOf("") }
        var packPlatform by remember { mutableStateOf(selectedPlatform) }
        var packTags by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showNewPackDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("New Reusable Hashtag Pack") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = packName,
                        onValueChange = { packName = it },
                        label = { Text("Pack Name (e.g. Tech AI Viral)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("YOUTUBE", "INSTAGRAM", "FACEBOOK", "ALL").forEach { plat ->
                            FilterChip(
                                selected = packPlatform == plat,
                                onClick = { packPlatform = plat },
                                label = { Text(plat.take(4)) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = packTags,
                        onValueChange = { packTags = it },
                        label = { Text("Hashtags (space-separated #tags)") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.saveHashtagPack(packName, packPlatform, packTags)
                        showNewPackDialog = false
                    },
                    enabled = packName.isNotBlank() && packTags.isNotBlank()
                ) { Text("Save Pack") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showNewPackDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showNicheEditorDialog) {
        NicheEditorDialog(
            initialNiche = editingNiche,
            characters = characterProfiles,
            hashtagPacks = hashtagPacks,
            youtubeProfiles = youTubeProfiles,
            isAutoFilling = isAutoFillingNiche,
            onAutoFillBlueprint = { name, onResult -> viewModel.aiAutoFillNicheBlueprint(name, onResult) },
            onCompressMemoryNow = { id -> viewModel.compressNicheMemoryNow(id) },
            onSave = { niche ->
                viewModel.saveNiche(niche)
                showNicheEditorDialog = false
            },
            onDelete = { niche ->
                viewModel.deleteNiche(niche)
                showNicheEditorDialog = false
            },
            onDismiss = { showNicheEditorDialog = false }
        )
    }

    if (showCommentAssistantSheet) {
        CommentAssistantSheet(
            initialVideoId = commentAssistantVideoId,
            comments = youTubeComments,
            isLoading = isLoadingYouTubeComments,
            statusMessage = youTubeCommentStatus,
            onFetchComments = { videoIdOrUrl, onlyUnanswered ->
                viewModel.fetchYouTubeComments(videoIdOrUrl, onlyUnanswered)
            },
            onGenerateAiDraft = { comment, onReady ->
                viewModel.generateAiCommentReplyDraft(comment, onReady)
            },
            onPostReply = { comment, replyText ->
                viewModel.postYouTubeCommentReply(comment, replyText)
            },
            onDismiss = { showCommentAssistantSheet = false }
        )
    }
}
