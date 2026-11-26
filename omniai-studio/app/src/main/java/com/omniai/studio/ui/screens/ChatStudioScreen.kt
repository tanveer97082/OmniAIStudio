package com.omniai.studio.ui.screens

import android.app.Activity
import android.content.Intent
import android.graphics.BitmapFactory
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
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
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AttachFile
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.BookmarkBorder
import androidx.compose.material.icons.filled.CenterFocusStrong
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.Compare
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.FullscreenExit
import androidx.compose.material.icons.filled.History
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Psychology
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material.icons.filled.VolumeUp
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.OutlinedTextFieldDefaults
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
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
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniai.studio.data.local.ChatMessageEntity
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.projects.AiMultiFileSplitter
import com.omniai.studio.settings.ChatBubbleStyle
import com.omniai.studio.ui.components.AutoTypePromptBar
import com.omniai.studio.ui.components.NicheEditorDialog
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.AmoledSurfaceVariant
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.LocalUiConfig
import com.omniai.studio.ui.theme.OmniAccentBadge
import com.omniai.studio.ui.theme.OmniDesignTokens
import com.omniai.studio.ui.theme.OmniEmptyState
import com.omniai.studio.ui.theme.OmniOverflowAction
import com.omniai.studio.ui.theme.OmniOverflowMenuButton
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.ui.theme.omniAccentBorder
import com.omniai.studio.ui.theme.omniAccentContainer
import com.omniai.studio.viewmodel.OmniAiViewModel
import kotlinx.coroutines.launch
import java.io.File

/**
 * Alias for the v5.1 minimalist progressive-disclosure Chat Screen.
 */
@Composable
fun ChatScreen(
    viewModel: OmniAiViewModel,
    isZenFocusMode: Boolean = false,
    onToggleZenFocusMode: () -> Unit = {},
    onOpenWorkflows: () -> Unit = {}
) = ChatStudioScreen(
    viewModel = viewModel,
    isZenFocusMode = isZenFocusMode,
    onToggleZenFocusMode = onToggleZenFocusMode,
    onOpenWorkflows = onOpenWorkflows
)

/**
 * v5.1 Minimalist Uncensored AI Chat Screen:
 * - Clean main canvas: minimal thread header + Zen Focus Mode toggle, message list,
 *   collapsible Auto-Typer chip strip (hidden when empty), and rounded bottom input capsule.
 * - Progressive disclosure `+` Attachment & Modes ModalBottomSheet housing OCR Scan,
 *   Attach PDF/Doc, Read URL, Incognito RAM Toggle, Arena Split-Compare, AI Memory & Knowledge Brain,
 *   System Prompt Switcher, Slash Commands, Prompt Workflows, Bookmarks, and Voice EN/HI.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatStudioScreen(
    viewModel: OmniAiViewModel,
    isZenFocusMode: Boolean = false,
    onToggleZenFocusMode: () -> Unit = {},
    onOpenWorkflows: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val scope = rememberCoroutineScope()
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary

    val threads by viewModel.chatThreads.collectAsState()
    val activeThreadId by viewModel.activeThreadId.collectAsState()
    val messages by viewModel.activeMessages.collectAsState()
    val presets by viewModel.promptPresets.collectAsState()
    val isStreaming by viewModel.isStreamingChat.collectAsState()
    val failoverBanner by viewModel.failoverBanner.collectAsState()
    val memorySnapshot by viewModel.activeMemorySnapshot.collectAsState()
    val isCompactingMemory by viewModel.isCompactingMemory.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val speakingMessageId by viewModel.speakingMessageId.collectAsState()
    val pendingChatPrompt by viewModel.pendingChatPrompt.collectAsState()
    val pendingVoiceAutoLaunch by viewModel.pendingVoiceAutoLaunch.collectAsState()
    val pendingOcrAutoLaunch by viewModel.pendingOcrAutoLaunch.collectAsState()
    val isRunningOcr by viewModel.isRunningOcr.collectAsState()
    val attachedDocument by viewModel.attachedDocument.collectAsState()
    val isReadingDocument by viewModel.isReadingDocument.collectAsState()
    val isScrapingUrl by viewModel.isScrapingUrl.collectAsState()
    val translationsByKey by viewModel.translationsByKey.collectAsState()
    val translatingKeys by viewModel.translatingKeys.collectAsState()
    val isIncognitoMode by viewModel.isIncognitoMode.collectAsState()
    val incognitoMessages by viewModel.incognitoMessages.collectAsState()
    val isArenaMode by viewModel.isArenaMode.collectAsState()
    val arenaPrompt by viewModel.arenaPrompt.collectAsState()
    val arenaLeftProvider by viewModel.arenaLeftProvider.collectAsState()
    val arenaRightProvider by viewModel.arenaRightProvider.collectAsState()
    val arenaLeftResponse by viewModel.arenaLeftResponse.collectAsState()
    val arenaRightResponse by viewModel.arenaRightResponse.collectAsState()
    val isArenaStreaming by viewModel.isArenaStreaming.collectAsState()
    val userMemoryFacts by viewModel.userMemoryFacts.collectAsState()
    val slashCommands by viewModel.slashCommands.collectAsState()
    val offlineDatasetItems by viewModel.offlineDatasetItems.collectAsState()
    val bookmarkedMessages by viewModel.bookmarkedMessages.collectAsState()
    val activeNiche by viewModel.activeNiche.collectAsState()
    val nextCommands by viewModel.activeNicheNextCommands.collectAsState()
    val isRegeneratingNextCommands by viewModel.isRegeneratingNextCommands.collectAsState()
    val isAutoFillingNiche by viewModel.isAutoFillingNiche.collectAsState()
    val characterProfiles by viewModel.characterProfiles.collectAsState()
    val hashtagPacks by viewModel.hashtagPacks.collectAsState()

    val displayedMessages = if (isIncognitoMode) incognitoMessages else messages
    val activeThread = remember(threads, activeThreadId) {
        threads.firstOrNull { it.id == activeThreadId }
    }

    var inputPrompt by remember { mutableStateOf("") }
    var manualEditToken by remember { mutableIntStateOf(0) }
    var voiceLocale by remember { mutableStateOf("en-US") }
    var showAttachmentModesSheet by remember { mutableStateOf(false) }
    var showThreadsSheet by remember { mutableStateOf(false) }
    var showPresetsDialog by remember { mutableStateOf(false) }
    var showMemoryDialog by remember { mutableStateOf(false) }
    var showUrlDialog by remember { mutableStateOf(false) }
    var showKnowledgeBrainDialog by remember { mutableStateOf(false) }
    var showBookmarksDialog by remember { mutableStateOf(false) }
    var showNicheEditorDialog by remember { mutableStateOf(false) }
    var editingNiche by remember { mutableStateOf<NicheEntity?>(null) }
    var activeBrainTab by remember { mutableIntStateOf(0) }
    var pendingSlashTemplate by remember { mutableStateOf<String?>(null) }
    var slashPlaceholderValues by remember { mutableStateOf<Map<String, String>>(emptyMap()) }
    var urlInput by remember { mutableStateOf("") }
    var previewHtmlCode by remember { mutableStateOf<String?>(null) }
    var saveToProjectContent by remember { mutableStateOf<String?>(null) }

    LaunchedEffect(pendingChatPrompt) {
        val incoming = pendingChatPrompt
        if (!incoming.isNullOrBlank()) {
            inputPrompt = if (inputPrompt.isBlank()) incoming else "$inputPrompt\n$incoming"
            viewModel.consumePendingChatPrompt()
        }
    }

    val ocrImageLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.extractTextFromImage(uri) { extracted ->
                inputPrompt = if (inputPrompt.isBlank()) extracted else "$inputPrompt\n$extracted"
            }
        }
    }

    val documentPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.attachDocumentFromUri(uri)
        }
    }

    val datasetImportLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.importOfflineDatasetFromUri(uri)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                inputPrompt = if (inputPrompt.isBlank()) spoken else "$inputPrompt $spoken"
            }
        }
    }

    LaunchedEffect(pendingOcrAutoLaunch) {
        if (pendingOcrAutoLaunch) {
            viewModel.consumeOcrAutoLaunch()
            ocrImageLauncher.launch(
                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
            )
        }
    }

    LaunchedEffect(pendingVoiceAutoLaunch) {
        if (pendingVoiceAutoLaunch) {
            viewModel.consumeVoiceAutoLaunch()
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLocale)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "OmniAI Quick Voice ($voiceLocale)...")
            }
            try {
                speechLauncher.launch(intent)
            } catch (_: Exception) {
                viewModel.postToast("Speech recognizer unavailable on this device.")
            }
        }
    }

    val exportMarkdownLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("text/markdown")
    ) { uri ->
        if (uri != null) {
            scope.launch {
                val md = viewModel.exportActiveThreadMarkdown()
                context.contentResolver.openOutputStream(uri)?.use { out ->
                    out.write(md.toByteArray(Charsets.UTF_8))
                }
                viewModel.postToast("Exported conversation to Markdown (.md).")
            }
        }
    }

    val uiConfig = LocalUiConfig.current
    val haptics = LocalHapticFeedback.current
    val wallpaperBitmap = remember(uiConfig.chatWallpaperPath) {
        val path = uiConfig.chatWallpaperPath
        if (path.isNotBlank() && File(path).exists()) {
            runCatching { BitmapFactory.decodeFile(path)?.asImageBitmap() }.getOrNull()
        } else {
            null
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        if (wallpaperBitmap != null) {
            Image(
                bitmap = wallpaperBitmap,
                contentDescription = "Chat Wallpaper",
                contentScale = ContentScale.Crop,
                modifier = Modifier.fillMaxSize()
            )
            Box(
                modifier = Modifier
                    .fillMaxSize()
                    .background(colorScheme.background.copy(alpha = uiConfig.wallpaperDimOpacity))
            )
        }

        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = OmniDesignTokens.ScreenHorizontalPadding, vertical = 10.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            // Minimalist Thread Bar + 1-Tap Zen Focus Mode Toggle
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Surface(
                    onClick = { showThreadsSheet = true },
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                    color = colorScheme.surface,
                    border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline),
                    modifier = Modifier.weight(1f, fill = false)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        Text(
                            text = when {
                                isIncognitoMode -> "Incognito (RAM)"
                                isArenaMode -> "Arena Split-Compare"
                                activeThread != null -> activeThread.title
                                else -> "New Conversation"
                            },
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.SemiBold,
                            color = if (isIncognitoMode) AmberWarning else colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                        Icon(
                            imageVector = Icons.Filled.ExpandMore,
                            contentDescription = "Switch Chat Thread",
                            tint = colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(16.dp)
                        )
                    }
                }

                Row(verticalAlignment = Alignment.CenterVertically) {
                    // 1-Tap Zen Focus Mode (hides TopBar & BottomBar)
                    IconButton(
                        onClick = onToggleZenFocusMode,
                        modifier = Modifier.defaultMinSize(
                            minWidth = OmniDesignTokens.MinTouchTarget,
                            minHeight = OmniDesignTokens.MinTouchTarget
                        )
                    ) {
                        Icon(
                            imageVector = if (isZenFocusMode) Icons.Filled.FullscreenExit else Icons.Filled.CenterFocusStrong,
                            contentDescription = if (isZenFocusMode) "Exit Zen Focus Mode" else "Enter Zen Focus Mode",
                            tint = if (isZenFocusMode) primaryColor else colorScheme.onSurfaceVariant
                        )
                    }
                    IconButton(
                        onClick = { viewModel.createNewThread() },
                        modifier = Modifier.defaultMinSize(
                            minWidth = OmniDesignTokens.MinTouchTarget,
                            minHeight = OmniDesignTokens.MinTouchTarget
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Add,
                            contentDescription = "New Chat Thread",
                            tint = colorScheme.onSurface
                        )
                    }
                    OmniOverflowMenuButton(
                        actions = listOf(
                            OmniOverflowAction(
                                label = "Bookmarked Answers (${bookmarkedMessages.size})",
                                icon = Icons.Filled.Bookmark,
                                onClick = { showBookmarksDialog = true }
                            ),
                            OmniOverflowAction(
                                label = "Conversation Memory (~${memorySnapshot.estimatedPromptTokens} tok)",
                                icon = Icons.Filled.History,
                                onClick = { showMemoryDialog = true }
                            ),
                            OmniOverflowAction(
                                label = "Export Markdown (.md)",
                                icon = Icons.Filled.Download,
                                onClick = {
                                    exportMarkdownLauncher.launch("OmniAI_Chat_${System.currentTimeMillis()}.md")
                                }
                            )
                        )
                    )
                }
            }

            // Active mode badges (only shown when explicitly active)
            if (failoverBanner != null) {
                Surface(
                    color = colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, AmberWarning),
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = failoverBanner.orEmpty(),
                        color = AmberWarning,
                        style = MaterialTheme.typography.labelMedium,
                        modifier = Modifier.padding(12.dp)
                    )
                }
            }

            // Message List OR Arena Mode
            if (isArenaMode) {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    item {
                        Surface(
                            color = colorScheme.surface,
                            border = BorderStroke(1.dp, EmeraldAccent.omniAccentBorder()),
                            shape = RoundedCornerShape(OmniDesignTokens.CardRadius),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(OmniDesignTokens.CardInnerPadding),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "Arena Compare",
                                        style = MaterialTheme.typography.titleSmall,
                                        color = EmeraldAccent
                                    )
                                    Text(
                                        text = if (arenaPrompt.isBlank()) {
                                            "Send any prompt below to race two AI providers side by side."
                                        } else {
                                            "Prompt: $arenaPrompt"
                                        },
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                TextButton(onClick = { viewModel.toggleArenaMode() }) {
                                    Text("Exit")
                                }
                            }
                        }
                    }
                    item {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                border = BorderStroke(1.dp, colorScheme.outline),
                                shape = RoundedCornerShape(OmniDesignTokens.CardRadius),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(arenaLeftProvider, style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                                    Text(
                                        text = arenaLeftResponse.ifBlank { if (isArenaStreaming) "Streaming..." else "Waiting..." },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = (appSettings.chatFontSizeSp - 1f).coerceAtLeast(11f).sp
                                        )
                                    )
                                }
                            }
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                                border = BorderStroke(1.dp, colorScheme.outline),
                                shape = RoundedCornerShape(OmniDesignTokens.CardRadius),
                                modifier = Modifier.weight(1f)
                            ) {
                                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(arenaRightProvider, style = MaterialTheme.typography.labelMedium, color = AmberWarning)
                                    Text(
                                        text = arenaRightResponse.ifBlank { if (isArenaStreaming) "Streaming..." else "Waiting..." },
                                        style = MaterialTheme.typography.bodyMedium.copy(
                                            fontSize = (appSettings.chatFontSizeSp - 1f).coerceAtLeast(11f).sp
                                        )
                                    )
                                }
                            }
                        }
                    }
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .weight(1f)
                        .fillMaxWidth(),
                    verticalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    if (isIncognitoMode) {
                        item {
                            Surface(
                                color = colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, AmberWarning.omniAccentBorder()),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 8.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = "Incognito RAM-only session — zero Room DB history",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = AmberWarning,
                                        modifier = Modifier.weight(1f)
                                    )
                                    TextButton(onClick = { viewModel.clearIncognitoSession() }) {
                                        Text("Purge")
                                    }
                                }
                            }
                        }
                    }

                    if (displayedMessages.isEmpty()) {
                        item {
                            Box(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(top = 40.dp)
                            ) {
                                OmniEmptyState(
                                    icon = Icons.Filled.ChatBubble,
                                    title = "No messages yet",
                                    helperText = "Start an uncensored SSE session or tap + for OCR, PDF Brain, URL Reader, and Arena Compare.",
                                    actionLabel = "Open Tools & Modes",
                                    onActionClick = { showAttachmentModesSheet = true }
                                )
                            }
                        }
                    } else {
                        items(displayedMessages, key = { it.id }) { msg ->
                            val transKey = "msg_${msg.id}"
                            ChatMessageCard(
                                message = msg,
                                fontSizeSp = appSettings.chatFontSizeSp,
                                isSpeaking = speakingMessageId == msg.id,
                                translation = translationsByKey[transKey],
                                isTranslating = translatingKeys.contains(transKey),
                                onToggleTranslate = { viewModel.toggleTranslateText(transKey, msg.content) },
                                onToggleSpeak = { viewModel.toggleSpeakMessage(msg.id, msg.content) },
                                onToggleBookmark = {
    if (!isIncognitoMode && msg.id > 0L) {
        viewModel.toggleMessageBookmark(msg.id, !msg.isBookmarked)
    }
},
                                onCopyCode = { code ->
                                    clipboard.setText(AnnotatedString(code))
                                    viewModel.postToast("Copied code block to clipboard.")
                                },
                                onRunHtmlPreview = { html -> previewHtmlCode = html },
                                onSaveToProject = { raw -> saveToProjectContent = raw }
                            )
                        }
                    }
                }
            }

            // Attached Offline Document Chip
            attachedDocument?.let { doc ->
                Surface(
                    color = colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, EmeraldAccent.omniAccentBorder()),
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 12.dp, vertical = 6.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Attached: ${doc.fileName} (${doc.pageCount}p · ${doc.chunks.size} chunks)",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldAccent,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        IconButton(onClick = { viewModel.clearAttachedDocument() }) {
                            Icon(Icons.Filled.Close, contentDescription = "Remove Attached File", tint = colorScheme.onSurfaceVariant)
                        }
                    }
                }
            }

            // Slash Command Auto-Suggestion Strip when typing '/'
            if (inputPrompt.startsWith("/") && slashCommands.isNotEmpty()) {
                val matches = slashCommands.filter {
                    it.commandName.startsWith(inputPrompt.trim(), ignoreCase = true)
                }
                if (matches.isNotEmpty()) {
                    LazyRow(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(matches, key = { it.id }) { cmd ->
                            FilterChip(
                                selected = false,
                                onClick = {
                                    val placeholders = viewModel.extractSlashPlaceholders(cmd.templateBody)
                                    if (placeholders.isEmpty()) {
                                        inputPrompt = cmd.templateBody
                                    } else {
                                        pendingSlashTemplate = cmd.templateBody
                                        slashPlaceholderValues = placeholders.associateWith { "" }
                                    }
                                },
                                label = { Text("${cmd.commandName} · ${cmd.description.take(18)}") }
                            )
                        }
                    }
                }
            }

            // Collapsible 1-line Auto-Typer chip strip (hidden when empty)
            AnimatedVisibility(visible = nextCommands.isNotEmpty() || isRegeneratingNextCommands) {
                AutoTypePromptBar(
                    title = "Next · ${activeNiche?.emoji ?: "✨"} ${activeNiche?.name ?: "Niche"}",
                    commands = nextCommands,
                    isRegenerating = isRegeneratingNextCommands,
                    autoTypeEnabled = appSettings.autoTypeEnabled,
                    speedMode = appSettings.autoTypeSpeedMode,
                    autoNextCountdownEnabled = false,
                    externalManualEditToken = manualEditToken,
                    onToggleAutoType = { viewModel.setAutoTypeEnabled(it) },
                    onRegenerate = { viewModel.regenerateNextCommandsForActiveNiche(isChatContext = true) },
                    onPromptTextUpdate = { inputPrompt = it },
                    onCommandSelected = { viewModel.markNextCommandUsed(it) },
                    onRouteToVideoDirector = { cmd -> viewModel.routeNextCommandToVideoDirector(cmd) }
                )
            }

            // Sleek Rounded Bottom Input Capsule (Level 2 Elevated Surface)
            Surface(
                color = colorScheme.surfaceVariant,
                border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline),
                shape = RoundedCornerShape(OmniDesignTokens.CapsuleRadius),
                modifier = Modifier.fillMaxWidth()
            ) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(horizontal = 6.dp, vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    IconButton(
                        onClick = { showAttachmentModesSheet = true },
                        modifier = Modifier.defaultMinSize(
                            minWidth = OmniDesignTokens.MinTouchTarget,
                            minHeight = OmniDesignTokens.MinTouchTarget
                        )
                    ) {
                        Box(
                            modifier = Modifier
                                .size(34.dp)
                                .clip(CircleShape)
                                .background(primaryColor.omniAccentContainer()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Add,
                                contentDescription = "Attachments & Chat Modes",
                                tint = primaryColor,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                    }

                    OutlinedTextField(
                        value = inputPrompt,
                        onValueChange = {
                            manualEditToken++
                            inputPrompt = it
                        },
                        placeholder = {
                            Text(
                                text = if (isArenaMode) "Race 2 models..." else "Message OmniAI...",
                                color = colorScheme.onSurfaceVariant
                            )
                        },
                        colors = OutlinedTextFieldDefaults.colors(
                            focusedBorderColor = Color.Transparent,
                            unfocusedBorderColor = Color.Transparent,
                            focusedContainerColor = Color.Transparent,
                            unfocusedContainerColor = Color.Transparent
                        ),
                        modifier = Modifier.weight(1f),
                        maxLines = 4
                    )

                    IconButton(
                        onClick = {
                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                putExtra(RecognizerIntent.EXTRA_LANGUAGE, voiceLocale)
                                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak prompt ($voiceLocale)...")
                            }
                            try {
                                speechLauncher.launch(intent)
                            } catch (_: Exception) {
                                viewModel.postToast("Speech recognizer unavailable on this device.")
                            }
                        },
                        modifier = Modifier.defaultMinSize(
                            minWidth = OmniDesignTokens.MinTouchTarget,
                            minHeight = OmniDesignTokens.MinTouchTarget
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = "Voice to Text",
                            tint = colorScheme.onSurfaceVariant
                        )
                    }

                    if (isStreaming || isArenaStreaming) {
                        IconButton(
                            onClick = { viewModel.stopStreaming() },
                            modifier = Modifier.defaultMinSize(
                                minWidth = OmniDesignTokens.MinTouchTarget,
                                minHeight = OmniDesignTokens.MinTouchTarget
                            )
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = "Stop Stream", tint = AmberWarning)
                        }
                    } else {
                        IconButton(
                            onClick = {
                                if (uiConfig.hapticFeedbackEnabled) {
                                    haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                                }
                                val prompt = inputPrompt
                                inputPrompt = ""
                                viewModel.sendChatMessage(prompt)
                            },
                            enabled = inputPrompt.isNotBlank(),
                            modifier = Modifier.defaultMinSize(
                                minWidth = OmniDesignTokens.MinTouchTarget,
                                minHeight = OmniDesignTokens.MinTouchTarget
                            )
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(36.dp)
                                    .clip(CircleShape)
                                    .background(
                                        if (inputPrompt.isNotBlank()) primaryColor else colorScheme.outline
                                    ),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send",
                                    tint = if (inputPrompt.isNotBlank()) colorScheme.onPrimary else colorScheme.onSurfaceVariant,
                                    modifier = Modifier.size(18.dp)
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // '+' ATTACHMENT & MODES MODAL BOTTOM SHEET (Clean 2-Row Grid)
    // =========================================================================
    if (showAttachmentModesSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showAttachmentModesSheet = false },
            sheetState = sheetState,
            containerColor = colorScheme.surfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colorScheme.outline) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OmniDesignTokens.ScreenHorizontalPadding)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(16.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Attachments & Chat Modes",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(
                            selected = voiceLocale == "en-US",
                            onClick = { voiceLocale = "en-US" },
                            label = { Text("EN") }
                        )
                        FilterChip(
                            selected = voiceLocale == "hi-IN",
                            onClick = { voiceLocale = "hi-IN" },
                            label = { Text("HI") }
                        )
                    }
                }

                // Row 1: Input & Capture Tools
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChatToolGridTile(
                        label = if (isRunningOcr) "Scanning..." else "OCR Scan",
                        icon = Icons.Filled.DocumentScanner,
                        selected = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentModesSheet = false
                            ocrImageLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        }
                    )
                    ChatToolGridTile(
                        label = if (isReadingDocument) "Reading..." else "PDF / Doc",
                        icon = Icons.Filled.AttachFile,
                        selected = attachedDocument != null,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentModesSheet = false
                            documentPickerLauncher.launch(
                                arrayOf("text/plain", "text/markdown", "text/csv", "application/json", "application/pdf", "*/*")
                            )
                        }
                    )
                    ChatToolGridTile(
                        label = if (isScrapingUrl) "Fetching..." else "Read URL",
                        icon = Icons.Filled.Language,
                        selected = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentModesSheet = false
                            showUrlDialog = true
                        }
                    )
                    ChatToolGridTile(
                        label = "Incognito",
                        icon = Icons.Filled.VisibilityOff,
                        selected = isIncognitoMode,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.toggleIncognitoMode()
                            showAttachmentModesSheet = false
                        }
                    )
                }

                // Row 2: AI Modes, Brain, Prompts & Slash Commands
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    ChatToolGridTile(
                        label = "Arena",
                        icon = Icons.Filled.Compare,
                        selected = isArenaMode,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            viewModel.toggleArenaMode()
                            showAttachmentModesSheet = false
                        }
                    )
                    ChatToolGridTile(
                        label = "AI Memory",
                        icon = Icons.Filled.Psychology,
                        selected = userMemoryFacts.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentModesSheet = false
                            activeBrainTab = 0
                            showKnowledgeBrainDialog = true
                        }
                    )
                    ChatToolGridTile(
                        label = "Prompts",
                        icon = Icons.Filled.Tune,
                        selected = false,
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentModesSheet = false
                            showPresetsDialog = true
                        }
                    )
                    ChatToolGridTile(
                        label = "/Commands",
                        icon = Icons.Filled.Code,
                        selected = slashCommands.isNotEmpty(),
                        modifier = Modifier.weight(1f),
                        onClick = {
                            showAttachmentModesSheet = false
                            activeBrainTab = 1
                            showKnowledgeBrainDialog = true
                        }
                    )
                }

                // Quick Links Row: Workflows + Offline Datasets + Bookmarks
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedButton(
                        onClick = {
                            showAttachmentModesSheet = false
                            onOpenWorkflows()
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.AccountTree, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Workflows", maxLines = 1)
                    }
                    OutlinedButton(
                        onClick = {
                            showAttachmentModesSheet = false
                            activeBrainTab = 2
                            showKnowledgeBrainDialog = true
                        },
                        modifier = Modifier.weight(1f),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 8.dp)
                    ) {
                        Icon(Icons.Filled.Storage, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(4.dp))
                        Text("Datasets (${offlineDatasetItems.size})", maxLines = 1)
                    }
                }
            }
        }
    }

    // Thread Switcher ModalBottomSheet
    if (showThreadsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showThreadsSheet = false },
            sheetState = sheetState,
            containerColor = colorScheme.surfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colorScheme.outline) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OmniDesignTokens.ScreenHorizontalPadding)
                    .padding(bottom = 24.dp),
                verticalArrangement = Arrangement.spacedBy(12.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Conversations (${threads.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                    TextButton(onClick = {
                        viewModel.createNewThread()
                        showThreadsSheet = false
                    }) {
                        Text("+ New Chat")
                    }
                }
                if (threads.isEmpty()) {
                    OmniEmptyState(
                        icon = Icons.Filled.ChatBubble,
                        title = "No saved threads",
                        helperText = "Start your first conversation and it will be automatically indexed here.",
                        actionLabel = "Create Thread",
                        onActionClick = {
                            viewModel.createNewThread()
                            showThreadsSheet = false
                        }
                    )
                } else {
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 320.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        items(threads, key = { it.id }) { thread ->
                            val isSelected = thread.id == activeThreadId
                            Surface(
                                onClick = {
                                    viewModel.selectThread(thread.id)
                                    showThreadsSheet = false
                                },
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                                color = if (isSelected) primaryColor.omniAccentContainer() else colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (isSelected) primaryColor.omniAccentBorder() else colorScheme.outline
                                ),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = thread.title,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                            color = if (isSelected) primaryColor else colorScheme.onSurface,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${thread.messageCount} msgs · ${thread.providerUsed.ifBlank { "OmniAI" }}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { viewModel.deleteThread(thread) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Thread", tint = colorScheme.error)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS (100% Preserved from v1.0–v4.5)
    // =========================================================================
    previewHtmlCode?.let { html ->
        CodePreviewDialog(
            htmlCode = html,
            onDismiss = { previewHtmlCode = null }
        )
    }

    saveToProjectContent?.let { raw ->
        SaveToProjectDialog(
            rawAiMessage = raw,
            viewModel = viewModel,
            onDismiss = { saveToProjectContent = null }
        )
    }

    if (showNicheEditorDialog) {
        NicheEditorDialog(
            initialNiche = editingNiche,
            characters = characterProfiles,
            hashtagPacks = hashtagPacks,
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

    if (showPresetsDialog) {
        var newName by remember { mutableStateOf("") }
        var newPrompt by remember { mutableStateOf("") }

        AlertDialog(
            onDismissRequest = { showPresetsDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("System Prompt Presets") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    presets.forEach { preset ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                            border = BorderStroke(1.dp, colorScheme.outline)
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(preset.name, style = MaterialTheme.typography.titleMedium)
                                    Row {
                                        TextButton(onClick = {
                                            viewModel.updateActiveThreadSystemPrompt(preset.systemPrompt)
                                            showPresetsDialog = false
                                        }) { Text("Apply") }
                                        IconButton(onClick = { viewModel.deletePromptPreset(preset) }) {
                                            Icon(Icons.Filled.Delete, contentDescription = "Delete Preset")
                                        }
                                    }
                                }
                                Text(preset.systemPrompt, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                            }
                        }
                    }

                    HorizontalDivider(color = colorScheme.outline)
                    OutlinedTextField(
                        value = newName,
                        onValueChange = { newName = it },
                        label = { Text("New Preset Name") },
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newPrompt,
                        onValueChange = { newPrompt = it },
                        label = { Text("System Prompt Instructions") },
                        modifier = Modifier.fillMaxWidth(),
                        minLines = 2
                    )
                    Button(
                        onClick = {
                            viewModel.savePromptPreset(newName, newPrompt)
                            newName = ""
                            newPrompt = ""
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Save Preset")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showPresetsDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showMemoryDialog) {
        AlertDialog(
            onDismissRequest = { showMemoryDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Conversation Memory (Room DB)") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Stored messages: ${memorySnapshot.totalStoredMessages} · Active window: ${memorySnapshot.activeWindowMessages} · Summarized: ${memorySnapshot.summarizedMessageCount}",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldAccent
                    )
                    Text(
                        text = "Estimated context budget: ~${memorySnapshot.estimatedPromptTokens} / ${memorySnapshot.maxContextTokenBudget} tokens",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    HorizontalDivider(color = colorScheme.outline)
                    Text("Rolling Summary (Persisted in Room)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = memorySnapshot.rollingSummary.ifBlank {
                            "No rolling summary yet. Older turns are automatically summarized once a conversation exceeds 12 messages or ~2,800 tokens."
                        },
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    Button(
                        onClick = { viewModel.compactActiveThreadMemory() },
                        enabled = !isCompactingMemory && memorySnapshot.totalStoredMessages > 4,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isCompactingMemory) "Summarizing..." else "Compact & Summarize Now")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showMemoryDialog = false }) {
                    Text("Close")
                }
            }
        )
    }

    if (showUrlDialog) {
        AlertDialog(
            onDismissRequest = { showUrlDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Read Web Article URL") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste any HTTPS article URL. Jsoup strips ads/scripts and imports clean text into your prompt.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
                    )
                    OutlinedTextField(
                        value = urlInput,
                        onValueChange = { urlInput = it },
                        label = { Text("https://example.com/article") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = urlInput
                        showUrlDialog = false
                        urlInput = ""
                        viewModel.readWebUrlForChat(target) { extracted ->
                            inputPrompt = if (inputPrompt.isBlank()) extracted else "$inputPrompt\n$extracted"
                        }
                    },
                    enabled = urlInput.isNotBlank()
                ) { Text("Read Article") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showUrlDialog = false }) { Text("Cancel") }
            }
        )
    }

    pendingSlashTemplate?.let { template ->
        val keys = remember(template) { viewModel.extractSlashPlaceholders(template) }
        AlertDialog(
            onDismissRequest = { pendingSlashTemplate = null },
            containerColor = colorScheme.surface,
            title = { Text("Fill Slash Command Placeholders") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    keys.forEach { key ->
                        OutlinedTextField(
                            value = slashPlaceholderValues[key].orEmpty(),
                            onValueChange = { updated ->
                                slashPlaceholderValues = slashPlaceholderValues + (key to updated)
                            },
                            label = { Text("{{$key}}") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                }
            },
            confirmButton = {
                Button(onClick = {
                    var resolved = template
                    slashPlaceholderValues.forEach { (k, v) ->
                        resolved = resolved.replace("{{$k}}", v)
                    }
                    inputPrompt = resolved
                    pendingSlashTemplate = null
                }) { Text("Insert Prompt") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingSlashTemplate = null }) { Text("Cancel") }
            }
        )
    }

    // Knowledge Brain Dialog (Memory Facts, Slash Commands, Offline Datasets)
    if (showKnowledgeBrainDialog) {
        var factCategory by remember { mutableStateOf("Preference") }
        var factText by remember { mutableStateOf("") }
        var slashName by remember { mutableStateOf("") }
        var slashDesc by remember { mutableStateOf("") }
        var slashTemplate by remember { mutableStateOf("") }
        var datasetSearch by remember { mutableStateOf("") }
        var selectedDatasetTag by remember { mutableStateOf<String?>(null) }

        val datasetTags = remember(offlineDatasetItems) {
            offlineDatasetItems.map { it.tag }.distinct().sorted()
        }
        val filteredDataset = remember(offlineDatasetItems, datasetSearch, selectedDatasetTag) {
            offlineDatasetItems.filter { item ->
                val tagOk = selectedDatasetTag == null || item.tag == selectedDatasetTag
                val queryOk = datasetSearch.isBlank() ||
                    item.question.contains(datasetSearch, ignoreCase = true) ||
                    item.answer.contains(datasetSearch, ignoreCase = true) ||
                    item.tag.contains(datasetSearch, ignoreCase = true)
                tagOk && queryOk
            }
        }

        AlertDialog(
            onDismissRequest = { showKnowledgeBrainDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("OmniAI Knowledge Brain") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        FilterChip(selected = activeBrainTab == 0, onClick = { activeBrainTab = 0 }, label = { Text("Facts") })
                        FilterChip(selected = activeBrainTab == 1, onClick = { activeBrainTab = 1 }, label = { Text("/Commands") })
                        FilterChip(selected = activeBrainTab == 2, onClick = { activeBrainTab = 2 }, label = { Text("Datasets") })
                    }
                    HorizontalDivider(color = colorScheme.outline)

                    when (activeBrainTab) {
                        0 -> {
                            Text("Auto-injected into every system prompt:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            userMemoryFacts.take(6).forEach { fact ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("[${fact.category}] ${fact.factText}", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    IconButton(onClick = { viewModel.deleteUserMemoryFact(fact) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Fact")
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = factCategory,
                                onValueChange = { factCategory = it },
                                label = { Text("Category (e.g. Role, Tone, Stack)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = factText,
                                onValueChange = { factText = it },
                                label = { Text("Long-Term Fact to Remember") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    viewModel.addUserMemoryFact(factCategory, factText)
                                    factText = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save Memory Fact") }
                        }
                        1 -> {
                            Text("Tap a command to fill {{placeholders}}:", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            slashCommands.take(6).forEach { cmd ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(cmd.commandName, style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                                        Text(cmd.templateBody.take(60), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                    }
                                    TextButton(onClick = {
                                        showKnowledgeBrainDialog = false
                                        val placeholders = viewModel.extractSlashPlaceholders(cmd.templateBody)
                                        if (placeholders.isEmpty()) {
                                            inputPrompt = cmd.templateBody
                                        } else {
                                            pendingSlashTemplate = cmd.templateBody
                                            slashPlaceholderValues = placeholders.associateWith { "" }
                                        }
                                    }) { Text("Use") }
                                    IconButton(onClick = { viewModel.deleteSlashCommand(cmd) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete Command")
                                    }
                                }
                            }
                            OutlinedTextField(
                                value = slashName,
                                onValueChange = { slashName = it },
                                label = { Text("Command (e.g. /hook)") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = slashDesc,
                                onValueChange = { slashDesc = it },
                                label = { Text("Short Description") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            OutlinedTextField(
                                value = slashTemplate,
                                onValueChange = { slashTemplate = it },
                                label = { Text("Template with {{topic}} placeholders") },
                                modifier = Modifier.fillMaxWidth()
                            )
                            Button(
                                onClick = {
                                    viewModel.addSlashCommand(slashName, slashDesc, slashTemplate)
                                    slashName = ""
                                    slashDesc = ""
                                    slashTemplate = ""
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Save Slash Command") }
                        }
                        else -> {
                            OutlinedButton(
                                onClick = {
                                    datasetImportLauncher.launch(arrayOf("application/json", "text/csv", "text/plain", "*/*"))
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text("Import Offline Q&A Pack (.json / .csv)") }
                            OutlinedTextField(
                                value = datasetSearch,
                                onValueChange = { datasetSearch = it },
                                label = { Text("Search Offline Q&A...") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth()
                            )
                            if (datasetTags.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                    item {
                                        FilterChip(
                                            selected = selectedDatasetTag == null,
                                            onClick = { selectedDatasetTag = null },
                                            label = { Text("All") }
                                        )
                                    }
                                    items(datasetTags) { tag ->
                                        FilterChip(
                                            selected = selectedDatasetTag == tag,
                                            onClick = { selectedDatasetTag = tag },
                                            label = { Text(tag) }
                                        )
                                    }
                                }
                            }
                            filteredDataset.take(6).forEach { item ->
                                Card(
                                    colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                    border = BorderStroke(1.dp, colorScheme.outline)
                                ) {
                                    Column(modifier = Modifier.padding(8.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Text("[${item.tag}] ${item.question}", style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                                        Text(item.answer.take(140), style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                                        TextButton(onClick = {
                                            inputPrompt = "Using this offline reference:\nQ: ${item.question}\nA: ${item.answer}\n\nExpand on this:"
                                            showKnowledgeBrainDialog = false
                                        }) { Text("Inject into Chat") }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showKnowledgeBrainDialog = false }) { Text("Close") }
            }
        )
    }

    // Bookmarked Answers Dialog
    if (showBookmarksDialog) {
        AlertDialog(
            onDismissRequest = { showBookmarksDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Bookmarked Answers (${bookmarkedMessages.size})") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    if (bookmarkedMessages.isEmpty()) {
                        Text(
                            text = "No bookmarked messages yet. Tap the Bookmark icon on any saved message to keep important answers here.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        bookmarkedMessages.take(8).forEach { saved ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        text = "${saved.role.uppercase()} · ${saved.modelName.ifBlank { saved.providerTag.ifBlank { "OmniAI" } }} · ~${saved.tokenEstimate} tok",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = EmeraldAccent
                                    )
                                    Text(
                                        text = saved.content.take(220) + if (saved.content.length > 220) "…" else "",
                                        style = MaterialTheme.typography.bodyMedium
                                    )
                                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        TextButton(onClick = {
                                            clipboard.setText(AnnotatedString(saved.content))
                                            viewModel.postToast("Copied bookmarked reply.")
                                        }) { Text("Copy") }
                                        TextButton(onClick = {
                                            viewModel.selectThread(saved.threadId)
                                            showBookmarksDialog = false
                                        }) { Text("Open Thread") }
                                        TextButton(onClick = { viewModel.toggleMessageBookmark(saved.id, false) }) { Text("Remove", color = AmberWarning) }
                                    }
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showBookmarksDialog = false }) { Text("Close") }
            }
        )
    }
}

@Composable
private fun ChatToolGridTile(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    modifier: Modifier = Modifier,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val accent = colorScheme.primary
    Surface(
        onClick = onClick,
        shape = RoundedCornerShape(14.dp),
        color = if (selected) accent.omniAccentContainer() else colorScheme.surface,
        border = BorderStroke(
            OmniDesignTokens.BorderWidth,
            if (selected) accent.omniAccentBorder() else colorScheme.outline
        ),
        modifier = modifier
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 6.dp, vertical = 12.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            Icon(
                imageVector = icon,
                contentDescription = label,
                tint = if (selected) accent else colorScheme.onSurface,
                modifier = Modifier.size(22.dp)
            )
            Text(
                text = label,
                style = MaterialTheme.typography.labelSmall,
                color = if (selected) accent else colorScheme.onSurfaceVariant,
                textAlign = TextAlign.Center,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        }
    }
}

@Composable
private fun ChatMessageCard(
    message: ChatMessageEntity,
    fontSizeSp: Float,
    isSpeaking: Boolean,
    translation: Pair<String, String>?,
    isTranslating: Boolean,
    onToggleTranslate: () -> Unit,
    onToggleSpeak: () -> Unit,
    onToggleBookmark: () -> Unit = {},
    onCopyCode: (String) -> Unit,
    onRunHtmlPreview: (String) -> Unit,
    onSaveToProject: (String) -> Unit = {}
) {
    val uiConfig = LocalUiConfig.current
    val isUser = message.role == "user"
    val accentColor = MaterialTheme.colorScheme.primary
    val colorScheme = MaterialTheme.colorScheme
    val containerColor = when (uiConfig.chatBubbleStyle) {
        ChatBubbleStyle.MINIMAL_FLAT -> if (isUser) accentColor.omniAccentContainer() else Color.Transparent
        ChatBubbleStyle.TERMINAL_MONO -> if (isUser) colorScheme.surfaceVariant else colorScheme.surface
        else -> if (isUser) accentColor.omniAccentContainer() else colorScheme.surface
    }
    val bubbleShape = when (uiConfig.chatBubbleStyle) {
        ChatBubbleStyle.ROUNDED -> RoundedCornerShape(16.dp)
        ChatBubbleStyle.SHARP_CYBER -> RoundedCornerShape(2.dp)
        ChatBubbleStyle.MINIMAL_FLAT -> RoundedCornerShape(10.dp)
        ChatBubbleStyle.TERMINAL_MONO -> RoundedCornerShape(6.dp)
    }
    val bubbleBorder = when (uiConfig.chatBubbleStyle) {
        ChatBubbleStyle.MINIMAL_FLAT -> BorderStroke(0.dp, Color.Transparent)
        ChatBubbleStyle.SHARP_CYBER -> BorderStroke(1.5.dp, if (isUser) accentColor else colorScheme.outline)
        else -> BorderStroke(1.dp, if (isUser) accentColor.omniAccentBorder() else colorScheme.outline)
    }
    val bubbleFontFamily = if (uiConfig.chatBubbleStyle == ChatBubbleStyle.TERMINAL_MONO) FontFamily.Monospace else null
    val segments = remember(message.content) { parseMarkdownCodeSegments(message.content) }

    Card(
        shape = bubbleShape,
        colors = CardDefaults.cardColors(containerColor = containerColor),
        border = bubbleBorder,
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(OmniDesignTokens.CardInnerPadding),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    Text(
                        text = if (uiConfig.chatBubbleStyle == ChatBubbleStyle.TERMINAL_MONO) {
                            if (isUser) "$ user" else "> omniai"
                        } else {
                            if (isUser) "You" else "OmniAI"
                        },
                        fontFamily = bubbleFontFamily,
                        style = MaterialTheme.typography.labelMedium,
                        fontWeight = FontWeight.SemiBold,
                        color = if (isUser) accentColor else colorScheme.onSurfaceVariant
                    )
                    if (!isUser && message.providerTag.isNotBlank()) {
                        OmniAccentBadge(text = message.providerTag.take(18), accent = accentColor)
                    }
                }

                // Max 2 visible icons (Read Aloud / Bookmark) + overflow for Translate
                Row(verticalAlignment = Alignment.CenterVertically) {
                    if (!isUser && message.content.isNotBlank()) {
                        IconButton(onClick = onToggleSpeak) {
                            Icon(
                                imageVector = if (isSpeaking) Icons.Filled.Stop else Icons.Filled.VolumeUp,
                                contentDescription = if (isSpeaking) "Stop Read Aloud" else "Read Aloud",
                                tint = if (isSpeaking) AmberWarning else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (message.id > 0L && message.content.isNotBlank()) {
                        IconButton(onClick = onToggleBookmark) {
                            Icon(
                                imageVector = if (message.isBookmarked) Icons.Filled.Bookmark else Icons.Filled.BookmarkBorder,
                                contentDescription = if (message.isBookmarked) "Remove Bookmark" else "Bookmark Message",
                                tint = if (message.isBookmarked) AmberWarning else colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    if (!isUser && message.content.isNotBlank()) {
                        OmniOverflowMenuButton(
                            actions = listOf(
                                OmniOverflowAction(
                                    label = if (translation != null) "Hide Translation" else "Translate Side-by-Side",
                                    icon = Icons.Filled.Translate,
                                    enabled = !isTranslating,
                                    onClick = onToggleTranslate
                                ),
                                OmniOverflowAction(
                                    label = "Copy Reply",
                                    icon = Icons.Filled.ContentCopy,
                                    onClick = { onCopyCode(message.content) }
                                )
                            )
                        )
                    }
                }
            }

            if (isTranslating) {
                Text(
                    text = "Translating for side-by-side view...",
                    style = MaterialTheme.typography.labelMedium,
                    color = AmberWarning
                )
            }

            if (translation != null && !isUser) {
                val (targetLang, translatedText) = translation
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Surface(
                        color = AmoledBlack,
                        border = BorderStroke(1.dp, AmoledBorder),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text("Original", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                            Text(
                                text = message.content,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (fontSizeSp - 1f).coerceAtLeast(11f).sp,
                                    lineHeight = (fontSizeSp + 5f).sp
                                )
                            )
                        }
                    }
                    Surface(
                        color = AmoledSurfaceVariant,
                        border = BorderStroke(1.dp, EmeraldAccent.omniAccentBorder()),
                        shape = RoundedCornerShape(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Text(targetLang, style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                            Text(
                                text = translatedText,
                                style = MaterialTheme.typography.bodyMedium.copy(
                                    fontSize = (fontSizeSp - 1f).coerceAtLeast(11f).sp,
                                    lineHeight = (fontSizeSp + 5f).sp
                                )
                            )
                        }
                    }
                }
            } else {
                segments.forEach { seg ->
                    if (seg.isCode) {
                        Surface(
                            color = AmoledBlack,
                            border = BorderStroke(1.dp, AmoledBorder),
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(modifier = Modifier.padding(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = seg.language.ifBlank { "code" },
                                        style = MaterialTheme.typography.labelMedium,
                                        color = accentColor
                                    )
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        val isPreviewableHtml = seg.language.equals("html", ignoreCase = true) ||
                                            seg.language.equals("htm", ignoreCase = true) ||
                                            seg.text.contains("<html", ignoreCase = true) ||
                                            seg.text.contains("<!doctype", ignoreCase = true) ||
                                            seg.text.contains("<body", ignoreCase = true) ||
                                            seg.text.contains("<script", ignoreCase = true)
                                        if (isPreviewableHtml) {
                                            TextButton(onClick = { onRunHtmlPreview(seg.text) }) {
                                                Icon(Icons.Filled.PlayArrow, contentDescription = "Run Live Preview", tint = EmeraldAccent)
                                                Spacer(modifier = Modifier.width(2.dp))
                                                Text("Run Live Preview", color = EmeraldAccent)
                                            }
                                        }
                                        TextButton(onClick = { onCopyCode(seg.text) }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy Code")
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Copy")
                                        }
                                    }
                                }
                                Text(
                                    text = seg.text,
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.bodyMedium.copy(
                                        fontSize = (fontSizeSp - 1f).coerceAtLeast(11f).sp,
                                        lineHeight = (fontSizeSp + 5f).sp
                                    )
                                )
                            }
                        }
                    } else if (seg.text.isNotBlank()) {
                        Text(
                            text = seg.text,
                            style = MaterialTheme.typography.bodyMedium.copy(
                                fontSize = fontSizeSp.sp,
                                lineHeight = (fontSizeSp + 6f).sp
                            )
                        )
                    }
                }
            }

            if (!isUser && AiMultiFileSplitter.hasSaveableCodeOrFiles(message.content)) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.End
                ) {
                    FilterChip(
                        selected = false,
                        onClick = { onSaveToProject(message.content) },
                        leadingIcon = {
                            Icon(
                                imageVector = Icons.Filled.FolderSpecial,
                                contentDescription = "Save to Project",
                                tint = EmeraldAccent
                            )
                        },
                        label = { Text("Save to Project", color = EmeraldAccent) }
                    )
                }
            }
        }
    }
}

private data class MessageSegment(
    val text: String,
    val isCode: Boolean,
    val language: String = ""
)

private fun parseMarkdownCodeSegments(raw: String): List<MessageSegment> {
    val result = mutableListOf<MessageSegment>()
    val regex = Regex("```([a-zA-Z0-9_-]*)\\n([\\s\\S]*?)```")
    var lastEnd = 0
    for (match in regex.findAll(raw)) {
        if (match.range.first > lastEnd) {
            result += MessageSegment(raw.substring(lastEnd, match.range.first).trim(), false)
        }
        result += MessageSegment(
            text = match.groupValues[2].trimEnd(),
            isCode = true,
            language = match.groupValues[1]
        )
        lastEnd = match.range.last + 1
    }
    if (lastEnd < raw.length) {
        result += MessageSegment(raw.substring(lastEnd).trim(), false)
    }
    if (result.isEmpty()) {
        result += MessageSegment(raw, false)
    }
    return result
}
