package com.omniai.studio.ui.screens

import android.app.Activity
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.BorderStroke
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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Face
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Language
import androidx.compose.material.icons.filled.Loop
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Speed
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.data.local.TemplateSceneBlueprint
import com.omniai.studio.data.local.VideoSceneTemplate
import com.omniai.studio.data.local.VideoSceneTemplateLibrary
import com.omniai.studio.data.local.VideoSceneTemplateOptions
import com.omniai.studio.data.local.VideoTemplateCategory
import com.omniai.studio.data.remote.StoryboardDialogueLine
import com.omniai.studio.media.AdobeAppPickerDialog
import com.omniai.studio.media.AdobeTargetApp
import com.omniai.studio.media.ProAdobeWorkflowCard
import com.omniai.studio.media.ThumbnailStudioSheet
import com.omniai.studio.ui.components.AutoTypePromptBar
import com.omniai.studio.ui.components.NicheEditorDialog
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.OmniAccentBadge
import com.omniai.studio.ui.theme.OmniDesignTokens
import com.omniai.studio.ui.theme.OmniEmptyState
import com.omniai.studio.ui.theme.OmniOverflowAction
import com.omniai.studio.ui.theme.OmniOverflowMenuButton
import com.omniai.studio.ui.theme.OmniSectionHeader
import com.omniai.studio.ui.theme.OmniSkeletonCard
import com.omniai.studio.ui.theme.OmniSurfaceCard
import com.omniai.studio.ui.theme.omniAccentBorder
import com.omniai.studio.ui.theme.omniAccentContainer
import com.omniai.studio.viewmodel.OmniAiViewModel
import kotlin.math.roundToInt

/**
 * Alias matching v5.1 naming while keeping VideoDirectorScreen 100% compatible.
 */
@Composable
fun VideoStudioScreen(
    viewModel: OmniAiViewModel,
    onOpenContentHub: () -> Unit = {}
) {
    VideoDirectorScreen(
        viewModel = viewModel,
        onOpenContentHub = onOpenContentHub
    )
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun VideoDirectorScreen(
    viewModel: OmniAiViewModel,
    onOpenContentHub: () -> Unit = {}
) {
    val colorScheme = MaterialTheme.colorScheme
    val draftTitle by viewModel.draftTitle.collectAsState()
    val draftScenes by viewModel.draftScenes.collectAsState()
    val savedStoryboards by viewModel.savedStoryboards.collectAsState()
    val customTemplates by viewModel.customVideoTemplates.collectAsState()
    val renderJobs by viewModel.renderJobs.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val isEnhancing by viewModel.isEnhancingPrompt.collectAsState()
    val isGenerating by viewModel.isGeneratingStoryboard.collectAsState()
    val isGeneratingSocialSeo by viewModel.isGeneratingSocialSeo.collectAsState()
    val pendingDirectorTopic by viewModel.pendingDirectorTopic.collectAsState()
    val isRunningOcr by viewModel.isRunningOcr.collectAsState()
    val isScrapingUrl by viewModel.isScrapingUrl.collectAsState()
    val characterProfiles by viewModel.characterProfiles.collectAsState()
    val selectedCharacterDnaId by viewModel.selectedCharacterDnaId.collectAsState()
    val weeklyUsedGpuHours by viewModel.weeklyUsedGpuHours.collectAsState()
    val draftWatermarkText by viewModel.draftWatermarkText.collectAsState()
    val draftBgmUrl by viewModel.draftBgmUrl.collectAsState()
    val draftSpeedFactor by viewModel.draftSpeedFactor.collectAsState()
    val selectedRefImagePath by viewModel.selectedRefImagePath.collectAsState()
    val kaggleAccounts by viewModel.kaggleAccounts.collectAsState()
    val activeKaggleAccountId by viewModel.activeKaggleAccountId.collectAsState()
    val translationsByKey by viewModel.translationsByKey.collectAsState()
    val translatingKeys by viewModel.translatingKeys.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val activeNiche by viewModel.activeNiche.collectAsState()
    val nextCommands by viewModel.activeNicheNextCommands.collectAsState()
    val seriesEpisodes by viewModel.activeNicheSeriesEpisodes.collectAsState()
    val isRegeneratingNextCommands by viewModel.isRegeneratingNextCommands.collectAsState()
    val adobeExpressStatus by viewModel.adobeExpressStatus.collectAsState()
    val adobeFireflyStatus by viewModel.adobeFireflyStatus.collectAsState()
    val isLaunchingAdobeBridge by viewModel.isLaunchingAdobeBridge.collectAsState()
    var adobePickerTarget by remember { mutableStateOf<AdobeTargetApp?>(null) }
    val isGeneratingSeriesPlan by viewModel.isGeneratingSeriesPlan.collectAsState()
    val isAuditingScript by viewModel.isAuditingScript.collectAsState()
    val isOptimizingScenes by viewModel.isOptimizingScenes.collectAsState()
    val scriptAuditResult by viewModel.scriptAuditResult.collectAsState()
    val draftSeamlessLoop by viewModel.draftSeamlessLoop.collectAsState()
    val pendingDirectorAutoTypeText by viewModel.pendingDirectorAutoTypeText.collectAsState()
    val isAutoFillingNiche by viewModel.isAutoFillingNiche.collectAsState()
    val hashtagPacks by viewModel.hashtagPacks.collectAsState()
    val youTubeProfiles by viewModel.youTubeProfiles.collectAsState()
    val isUploadingToYouTube by viewModel.isUploadingToYouTube.collectAsState()
    val youTubeUploadProgress by viewModel.youTubeUploadProgress.collectAsState()
    val youTubeUploadStatus by viewModel.youTubeUploadStatus.collectAsState()

    val selectedCharacter = remember(characterProfiles, selectedCharacterDnaId) {
        characterProfiles.firstOrNull { it.id == selectedCharacterDnaId }
    }

    // 3-Step Wizard State: 0 = 1. Idea, 1 = 2. Scenes, 2 = 3. Render
    var activeStep by remember { mutableIntStateOf(0) }
    var showRenderSettingsSheet by remember { mutableStateOf(false) }
    var showTemplatesLibrary by remember { mutableStateOf(false) }
    var showSavedStoryboards by remember { mutableStateOf(false) }
    var expandedSceneIdx by remember { mutableIntStateOf(0) }

    var topic by remember { mutableStateOf("") }
    var manualEditToken by remember { mutableIntStateOf(0) }
    var sceneCountSlider by remember { mutableFloatStateOf(4f) }
    var aspectRatio by remember { mutableStateOf("9:16") }
    var stylePreset by remember { mutableStateOf("Cinematic Realism") }
    var voice by remember { mutableStateOf("English Male") }
    var multiCharacterMode by remember { mutableStateOf(false) }
    var burnSubtitles by remember { mutableStateOf(true) }
    var downloadIndividualScenes by remember { mutableStateOf(true) }
    var selectedTemplateCategory by remember { mutableStateOf<VideoTemplateCategory?>(null) }

    var showDnaMenu by remember { mutableStateOf(false) }
    var showDnaDialog by remember { mutableStateOf(false) }
    var showDirectorUrlDialog by remember { mutableStateOf(false) }
    var directorUrlInput by remember { mutableStateOf("") }
    var showNicheEditorDialog by remember { mutableStateOf(false) }
    var editingNiche by remember { mutableStateOf<NicheEntity?>(null) }
    var showSeriesPlannerSheet by remember { mutableStateOf(false) }
    var showScriptAuditorDialog by remember { mutableStateOf(false) }
    var thumbnailStudioTarget by remember { mutableStateOf<Pair<String?, String>?>(null) }

    // Template Admin Studio state
    var templateAdminMode by remember { mutableStateOf(false) }
    var editingTemplateId by remember { mutableStateOf<Long?>(null) }
    var showTemplateEditor by remember { mutableStateOf(false) }
    var adminName by remember { mutableStateOf("") }
    var adminCategory by remember { mutableStateOf(VideoTemplateCategory.CUSTOM) }
    var adminDescription by remember { mutableStateOf("") }
    var adminVisualStyle by remember { mutableStateOf(VideoSceneTemplateOptions.visualStyles.first()) }
    var adminVoice by remember { mutableStateOf(VideoSceneTemplateOptions.voiceoverVoices.first()) }
    var adminAspectRatio by remember { mutableStateOf("9:16") }
    var adminBurnSubtitles by remember { mutableStateOf(true) }
    var adminScenes by remember {
        mutableStateOf(
            listOf(
                TemplateSceneBlueprint(
                    beatName = "01 · Opening Hook",
                    visualIdea = "Dramatic establishing shot of [SUBJECT], crisp key lighting, smooth dolly push-in",
                    narrationPlaceholder = "Start with [HOOK QUESTION] to capture attention in the first 3 seconds.",
                    durationSec = 5
                )
            )
        )
    }
    var adminError by remember { mutableStateOf<String?>(null) }

    fun openNewTemplateEditor() {
        editingTemplateId = null
        adminName = ""
        adminCategory = VideoTemplateCategory.CUSTOM
        adminDescription = ""
        adminVisualStyle = VideoSceneTemplateOptions.visualStyles.first()
        adminVoice = VideoSceneTemplateOptions.voiceoverVoices.first()
        adminAspectRatio = "9:16"
        adminBurnSubtitles = true
        adminScenes = listOf(
            TemplateSceneBlueprint(
                beatName = "01 · Opening Hook",
                visualIdea = "Dramatic establishing shot of [SUBJECT], crisp key lighting, smooth dolly push-in",
                narrationPlaceholder = "Start with [HOOK QUESTION] to capture attention in the first 3 seconds.",
                durationSec = 5
            )
        )
        adminError = null
        showTemplatesLibrary = true
        templateAdminMode = true
        showTemplateEditor = true
    }

    fun openTemplateInEditor(template: VideoSceneTemplate, asClone: Boolean) {
        editingTemplateId = if (asClone) null else template.customDbId
        adminName = if (asClone) "${template.name} (Custom)" else template.name
        adminCategory = template.category
        adminDescription = template.description
        adminVisualStyle = template.recommendedStyle
        adminVoice = template.recommendedVoice
        adminAspectRatio = template.recommendedAspectRatio
        adminBurnSubtitles = template.burnSubtitlesDefault
        adminScenes = template.scenes
        adminError = null
        showTemplatesLibrary = true
        templateAdminMode = true
        showTemplateEditor = true
    }

    val allTemplates = remember(customTemplates) {
        customTemplates + VideoSceneTemplateLibrary.allTemplates
    }

    val filteredTemplates = remember(selectedTemplateCategory, allTemplates) {
        if (selectedTemplateCategory == null) {
            allTemplates
        } else {
            allTemplates.filter { it.category == selectedTemplateCategory }
        }
    }

    val stylePresets = VideoSceneTemplateOptions.visualStyles
    val voices = VideoSceneTemplateOptions.voiceoverVoices

    // Auto-apply active Niche defaults when switching niches
    LaunchedEffect(activeNiche?.id) {
        val niche = activeNiche ?: return@LaunchedEffect
        if (niche.defaultAspectRatio.isNotBlank()) aspectRatio = niche.defaultAspectRatio
        if (niche.visualStylePreset.isNotBlank()) stylePreset = niche.visualStylePreset
        if (niche.defaultVoiceId.isNotBlank()) voice = niche.defaultVoiceId
        niche.linkedCharacterId?.let { viewModel.selectCharacterDna(it) }
    }

    LaunchedEffect(pendingDirectorTopic) {
        val incoming = pendingDirectorTopic
        if (!incoming.isNullOrBlank()) {
            topic = incoming
            activeStep = 0
            viewModel.consumePendingDirectorTopic()
        }
    }

    // Track storyboard generation transition so Step 2 opens naturally once scenes arrive
    var wasGenerating by remember { mutableStateOf(false) }
    LaunchedEffect(isGenerating, draftScenes.size) {
        if (wasGenerating && !isGenerating && draftScenes.isNotEmpty() && activeStep == 0) {
            activeStep = 1
            expandedSceneIdx = 0
        }
        wasGenerating = isGenerating
    }

    val ocrTopicLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.extractTextFromImage(uri) { extracted ->
                topic = if (topic.isBlank()) extracted else "$topic\n$extracted"
            }
        }
    }

    val refImagePickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.setReferenceImageFromUri(uri)
        }
    }

    val speechLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.StartActivityForResult()
    ) { result ->
        if (result.resultCode == Activity.RESULT_OK) {
            val spoken = result.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
            if (!spoken.isNullOrBlank()) {
                topic = spoken
            }
        }
    }

    val activeRenderCount = remember(renderJobs) {
        renderJobs.count { it.status in setOf("QUEUED", "PUSHED", "RENDERING", "DOWNLOADING") }
    }

    val compactSettingsSummary = remember(
        aspectRatio,
        voice,
        sceneCountSlider,
        selectedCharacter,
        draftSeamlessLoop,
        draftSpeedFactor
    ) {
        val dnaLabel = selectedCharacter?.name?.take(12) ?: "Free DNA"
        val loopLabel = if (draftSeamlessLoop) "Loop ON" else "Loop OFF"
        "$aspectRatio · ${voice.take(14)} · ${sceneCountSlider.roundToInt()} scenes · $dnaLabel · $loopLabel"
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
    ) {
        // =====================================================================
        // SEGMENTED 3-STEP WIZARD HEADER (1. Idea | 2. Scenes | 3. Render)
        // =====================================================================
        Surface(
            color = colorScheme.surface,
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = OmniDesignTokens.ScreenHorizontalPadding, vertical = 10.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text(
                        text = "Video Studio",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold
                    )
                    Surface(
                        onClick = { showSeriesPlannerSheet = true },
                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                        color = colorScheme.primary.omniAccentContainer(),
                        border = BorderStroke(1.dp, colorScheme.primary.omniAccentBorder())
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(
                                Icons.Filled.CalendarMonth,
                                contentDescription = null,
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Text(
                                text = "Series (${seriesEpisodes.size})",
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.primary
                            )
                        }
                    }
                }

                // 3-Step Segmented Pill Control
                Surface(
                    color = colorScheme.surfaceVariant,
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                    border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(4.dp),
                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                    ) {
                        val steps = listOf(
                            "1. Idea",
                            if (draftScenes.isNotEmpty()) "2. Scenes (${draftScenes.size})" else "2. Scenes",
                            if (activeRenderCount > 0) "3. Render ($activeRenderCount)" else "3. Render"
                        )
                        steps.forEachIndexed { idx, label ->
                            val isSelected = activeStep == idx
                            Surface(
                                onClick = { activeStep = idx },
                                shape = RoundedCornerShape(10.dp),
                                color = if (isSelected) colorScheme.primary.omniAccentContainer() else Color.Transparent,
                                border = if (isSelected) BorderStroke(1.dp, colorScheme.primary.omniAccentBorder()) else null,
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = 40.dp)
                            ) {
                                Box(contentAlignment = Alignment.Center, modifier = Modifier.padding(horizontal = 6.dp, vertical = 8.dp)) {
                                    Text(
                                        text = label,
                                        style = MaterialTheme.typography.labelMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
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
        HorizontalDivider(color = colorScheme.outline, thickness = OmniDesignTokens.BorderWidth)

        // =====================================================================
        // STEP CONTENT
        // =====================================================================
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(
                horizontal = OmniDesignTokens.ScreenHorizontalPadding,
                vertical = OmniDesignTokens.SectionVerticalGap
            ),
            verticalArrangement = Arrangement.spacedBy(OmniDesignTokens.SectionVerticalGap)
        ) {
            when (activeStep) {
                // =============================================================
                // STEP 1: IDEA & COMPACT RENDER SETTINGS PILL
                // =============================================================
                0 -> {
                    item {
                        OmniSurfaceCard {
                            OmniSectionHeader(
                                title = "Concept & Prompt",
                                subtitle = "${activeNiche?.emoji ?: "🎬"} ${activeNiche?.name ?: "Active Niche"} · $stylePreset"
                            )

                            // Auto-Next Ideas & Auto-Typer Strip
                            AutoTypePromptBar(
                                title = "Auto-Next Ideas · ${activeNiche?.emoji ?: "🎬"} ${activeNiche?.name ?: "Niche"}",
                                commands = nextCommands,
                                isRegenerating = isRegeneratingNextCommands,
                                autoTypeEnabled = appSettings.autoTypeEnabled,
                                speedMode = appSettings.autoTypeSpeedMode,
                                autoNextCountdownEnabled = activeNiche?.autoNextEnabled == true,
                                externalManualEditToken = manualEditToken,
                                pendingExternalAutoTypeText = pendingDirectorAutoTypeText,
                                onConsumeExternalAutoTypeText = { viewModel.consumePendingDirectorAutoTypeText() },
                                onToggleAutoType = { viewModel.setAutoTypeEnabled(it) },
                                onRegenerate = { viewModel.regenerateNextCommandsForActiveNiche(isChatContext = false) },
                                onPromptTextUpdate = { topic = it },
                                onCommandSelected = { viewModel.markNextCommandUsed(it) },
                                onRunNow = {
                                    if (topic.isNotBlank() && !isGenerating) {
                                        viewModel.generateStoryboard(
                                            topic = topic,
                                            sceneCount = sceneCountSlider.roundToInt(),
                                            aspectRatio = aspectRatio,
                                            stylePreset = stylePreset,
                                            voice = voice,
                                            burnSubtitles = burnSubtitles,
                                            seamlessLoop = draftSeamlessLoop,
                                            multiCharacterMode = multiCharacterMode
                                        )
                                    }
                                }
                            )

                            OutlinedTextField(
                                value = topic,
                                onValueChange = {
                                    manualEditToken++
                                    topic = it
                                },
                                label = { Text("Video Topic / Concept") },
                                placeholder = { Text("Describe your story, hook, or paste an article...") },
                                modifier = Modifier.fillMaxWidth(),
                                minLines = 3,
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                                trailingIcon = {
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { showDirectorUrlDialog = true },
                                            enabled = !isScrapingUrl
                                        ) {
                                            Icon(
                                                Icons.Filled.Language,
                                                contentDescription = "Read Web URL into Topic",
                                                tint = colorScheme.primary
                                            )
                                        }
                                        IconButton(
                                            onClick = {
                                                ocrTopicLauncher.launch(
                                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                                )
                                            },
                                            enabled = !isRunningOcr
                                        ) {
                                            Icon(
                                                Icons.Filled.DocumentScanner,
                                                contentDescription = "Scan Image OCR for Topic",
                                                tint = colorScheme.primary
                                            )
                                        }
                                        IconButton(onClick = {
                                            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                                                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                                            }
                                            try {
                                                speechLauncher.launch(intent)
                                            } catch (_: Exception) {
                                                viewModel.postToast("Speech recognizer unavailable.")
                                            }
                                        }) {
                                            Icon(Icons.Filled.Mic, contentDescription = "Dictate Topic", tint = colorScheme.primary)
                                        }
                                    }
                                }
                            )

                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Multi-character dialogue", style = MaterialTheme.typography.labelLarge)
                                    Text(
                                        "Generate named speakers with different voices",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                Switch(
                                    checked = multiCharacterMode,
                                    onCheckedChange = { multiCharacterMode = it }
                                )
                            }

                            // Compact Render Settings Summary Pill -> Opens ModalBottomSheet
                            Surface(
                                onClick = { showRenderSettingsSheet = true },
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                                color = colorScheme.surfaceVariant,
                                border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget)
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Row(
                                        modifier = Modifier.weight(1f),
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        Icon(
                                            Icons.Filled.Tune,
                                            contentDescription = null,
                                            tint = colorScheme.primary,
                                            modifier = Modifier.size(18.dp)
                                        )
                                        Column {
                                            Text(
                                                text = "Render Settings",
                                                style = MaterialTheme.typography.labelMedium,
                                                fontWeight = FontWeight.SemiBold,
                                                color = colorScheme.onSurface
                                            )
                                            Text(
                                                text = compactSettingsSummary,
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant,
                                                maxLines = 1,
                                                overflow = TextOverflow.Ellipsis
                                            )
                                        }
                                    }
                                    OmniAccentBadge(text = "Edit", accent = colorScheme.primary)
                                }
                            }

                            // Max 2 Primary Actions: Enhance Prompt + Generate JSON
                            Row(
                                horizontalArrangement = Arrangement.spacedBy(10.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        viewModel.enhanceTopicPrompt(topic, stylePreset) { enhanced ->
                                            topic = enhanced
                                        }
                                    },
                                    enabled = !isEnhancing && topic.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                                ) {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isEnhancing) "Enhancing..." else "Enhance")
                                }

                                Button(
                                    onClick = {
                                        viewModel.generateStoryboard(
                                            topic = topic,
                                            sceneCount = sceneCountSlider.roundToInt(),
                                            aspectRatio = aspectRatio,
                                            stylePreset = stylePreset,
                                            voice = voice,
                                            burnSubtitles = burnSubtitles,
                                            seamlessLoop = draftSeamlessLoop,
                                            multiCharacterMode = multiCharacterMode
                                        )
                                    },
                                    enabled = !isGenerating && topic.isNotBlank(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                                ) {
                                    Text(if (isGenerating) "Drafting..." else "Generate JSON")
                                }
                            }
                        }
                    }

                    if (isGenerating) {
                        item {
                            OmniSkeletonCard(lineCount = 3)
                        }
                    }

                    // Collapsible Video Scene Templates Library & Template Admin Studio
                    item {
                        OmniSurfaceCard(
                            onClick = { showTemplatesLibrary = !showTemplatesLibrary }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("Templates", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                                    Text(
                                        "${allTemplates.size} presets · ${customTemplates.size} custom saved",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                Icon(
                                    imageVector = if (showTemplatesLibrary) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = "Toggle Templates",
                                    tint = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    if (showTemplatesLibrary) {
                        item {
                            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    FilterChip(
                                        selected = templateAdminMode,
                                        onClick = {
                                            templateAdminMode = !templateAdminMode
                                            if (!templateAdminMode) showTemplateEditor = false
                                        },
                                        leadingIcon = { Icon(Icons.Filled.Tune, contentDescription = null) },
                                        label = { Text("Template Admin") }
                                    )
                                    OutlinedButton(onClick = { openNewTemplateEditor() }) {
                                        Icon(Icons.Filled.Add, contentDescription = null)
                                        Spacer(modifier = Modifier.width(4.dp))
                                        Text("New")
                                    }
                                }
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    item {
                                        FilterChip(
                                            selected = selectedTemplateCategory == null,
                                            onClick = { selectedTemplateCategory = null },
                                            label = { Text("All (${allTemplates.size})") }
                                        )
                                    }
                                    items(VideoTemplateCategory.entries) { cat ->
                                        FilterChip(
                                            selected = selectedTemplateCategory == cat,
                                            onClick = { selectedTemplateCategory = cat },
                                            label = { Text(cat.label) }
                                        )
                                    }
                                }
                            }
                        }

                        if (templateAdminMode && showTemplateEditor) {
                            item {
                                OmniSurfaceCard(
                                    containerColor = colorScheme.surfaceVariant,
                                    borderColor = EmeraldAccent
                                ) {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text(
                                            if (editingTemplateId == null) "Create Custom Template" else "Customize Template",
                                            style = MaterialTheme.typography.titleMedium,
                                            color = EmeraldAccent
                                        )
                                        TextButton(onClick = { showTemplateEditor = false }) {
                                            Text("Close")
                                        }
                                    }

                                    if (adminError != null) {
                                        Text(adminError!!, color = CrimsonDanger, style = MaterialTheme.typography.bodyMedium)
                                    }

                                    OutlinedTextField(
                                        value = adminName,
                                        onValueChange = { adminName = it; adminError = null },
                                        label = { Text("Template Name") },
                                        modifier = Modifier.fillMaxWidth(),
                                        singleLine = true
                                    )

                                    OutlinedTextField(
                                        value = adminDescription,
                                        onValueChange = { adminDescription = it },
                                        label = { Text("Template Summary / Purpose") },
                                        modifier = Modifier.fillMaxWidth(),
                                        minLines = 2
                                    )

                                    Text("Template Category", style = MaterialTheme.typography.labelMedium)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(VideoTemplateCategory.entries) { cat ->
                                            FilterChip(
                                                selected = adminCategory == cat,
                                                onClick = { adminCategory = cat },
                                                label = { Text(cat.label) }
                                            )
                                        }
                                    }

                                    Text("Default Visual Style", style = MaterialTheme.typography.labelMedium)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(stylePresets) { preset ->
                                            FilterChip(
                                                selected = adminVisualStyle == preset,
                                                onClick = { adminVisualStyle = preset },
                                                label = { Text(preset) }
                                            )
                                        }
                                    }

                                    Text("Voiceover Voice Selection", style = MaterialTheme.typography.labelMedium)
                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                        items(voices) { v ->
                                            FilterChip(
                                                selected = adminVoice == v,
                                                onClick = { adminVoice = v },
                                                label = { Text(v) }
                                            )
                                        }
                                    }

                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                                            Text("Aspect:", style = MaterialTheme.typography.labelMedium)
                                            VideoSceneTemplateOptions.aspectRatios.forEach { r ->
                                                FilterChip(
                                                    selected = adminAspectRatio == r,
                                                    onClick = { adminAspectRatio = r },
                                                    label = { Text(r) }
                                                )
                                            }
                                        }
                                        Row(verticalAlignment = Alignment.CenterVertically) {
                                            Text("SRT", style = MaterialTheme.typography.labelMedium)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Switch(checked = adminBurnSubtitles, onCheckedChange = { adminBurnSubtitles = it })
                                        }
                                    }

                                    HorizontalDivider(color = colorScheme.outline)
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Text("Scene Structure (${adminScenes.size})", style = MaterialTheme.typography.titleSmall)
                                        OutlinedButton(onClick = {
                                            val nextNum = adminScenes.size + 1
                                            adminScenes = adminScenes + TemplateSceneBlueprint(
                                                beatName = "0$nextNum · Scene Beat",
                                                visualIdea = "Placeholder visual description for [SCENE $nextNum SUBJECT], cinematic framing",
                                                narrationPlaceholder = "Placeholder narration line explaining [KEY POINT $nextNum].",
                                                durationSec = 5
                                            )
                                        }) {
                                            Icon(Icons.Filled.Add, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Add Scene")
                                        }
                                    }

                                    adminScenes.forEachIndexed { idx, sceneBp ->
                                        OmniSurfaceCard(containerColor = colorScheme.surface) {
                                            Row(
                                                modifier = Modifier.fillMaxWidth(),
                                                horizontalArrangement = Arrangement.SpaceBetween,
                                                verticalAlignment = Alignment.CenterVertically
                                            ) {
                                                Text("Scene ${idx + 1}", style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                                                OmniOverflowMenuButton(
                                                    actions = listOf(
                                                        OmniOverflowAction(
                                                            label = "Move Up",
                                                            icon = Icons.Filled.ArrowUpward,
                                                            enabled = idx > 0
                                                        ) {
                                                            val list = adminScenes.toMutableList()
                                                            val moved = list.removeAt(idx)
                                                            list.add(idx - 1, moved)
                                                            adminScenes = list
                                                        },
                                                        OmniOverflowAction(
                                                            label = "Move Down",
                                                            icon = Icons.Filled.ArrowDownward,
                                                            enabled = idx < adminScenes.lastIndex
                                                        ) {
                                                            val list = adminScenes.toMutableList()
                                                            val moved = list.removeAt(idx)
                                                            list.add(idx + 1, moved)
                                                            adminScenes = list
                                                        },
                                                        OmniOverflowAction(
                                                            label = "Remove Scene",
                                                            icon = Icons.Filled.Delete,
                                                            isDestructive = true,
                                                            enabled = adminScenes.size > 1
                                                        ) {
                                                            adminScenes = adminScenes.filterIndexed { i, _ -> i != idx }
                                                        }
                                                    )
                                                )
                                            }
                                            OutlinedTextField(
                                                value = sceneBp.beatName,
                                                onValueChange = { updated ->
                                                    adminScenes = adminScenes.mapIndexed { i, item ->
                                                        if (i == idx) item.copy(beatName = updated) else item
                                                    }
                                                },
                                                label = { Text("Scene Beat Title") },
                                                modifier = Modifier.fillMaxWidth(),
                                                singleLine = true
                                            )
                                            OutlinedTextField(
                                                value = sceneBp.visualIdea,
                                                onValueChange = { updated ->
                                                    adminScenes = adminScenes.mapIndexed { i, item ->
                                                        if (i == idx) item.copy(visualIdea = updated) else item
                                                    }
                                                },
                                                label = { Text("Placeholder Scene Visual Description") },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 2
                                            )
                                            OutlinedTextField(
                                                value = sceneBp.narrationPlaceholder,
                                                onValueChange = { updated ->
                                                    adminScenes = adminScenes.mapIndexed { i, item ->
                                                        if (i == idx) item.copy(narrationPlaceholder = updated) else item
                                                    }
                                                },
                                                label = { Text("Placeholder Narration Script") },
                                                modifier = Modifier.fillMaxWidth(),
                                                minLines = 2
                                            )
                                        }
                                    }

                                    Button(
                                        onClick = {
                                            val candidate = VideoSceneTemplate(
                                                id = editingTemplateId?.let { "custom_$it" } ?: "custom_new",
                                                name = adminName,
                                                category = adminCategory,
                                                description = adminDescription,
                                                recommendedAspectRatio = adminAspectRatio,
                                                recommendedStyle = adminVisualStyle,
                                                recommendedVoice = adminVoice,
                                                burnSubtitlesDefault = adminBurnSubtitles,
                                                scenes = adminScenes,
                                                isCustom = true,
                                                customDbId = editingTemplateId
                                            )
                                            val err = viewModel.saveCustomVideoTemplate(candidate)
                                            if (err != null) {
                                                adminError = err
                                            } else {
                                                adminError = null
                                                showTemplateEditor = false
                                            }
                                        },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget)
                                    ) {
                                        Icon(Icons.Filled.Save, contentDescription = null)
                                        Spacer(modifier = Modifier.width(6.dp))
                                        Text(if (editingTemplateId == null) "Save Custom Template" else "Update Template")
                                    }
                                }
                            }
                        }

                        items(filteredTemplates, key = { it.id }) { template ->
                            OmniSurfaceCard(
                                borderColor = if (template.isCustom) EmeraldAccent.copy(alpha = 0.45f) else colorScheme.outline
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = if (template.isCustom) "${template.name} · Custom" else template.name,
                                            style = MaterialTheme.typography.titleSmall,
                                            fontWeight = FontWeight.SemiBold
                                        )
                                        Text(
                                            "${template.category.label} · ${template.scenes.size} scenes · ${template.recommendedStyle}",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldAccent
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        OutlinedButton(
                                            onClick = {
                                                topic = template.name
                                                aspectRatio = template.recommendedAspectRatio
                                                stylePreset = template.recommendedStyle
                                                voice = template.recommendedVoice
                                                burnSubtitles = template.burnSubtitlesDefault
                                                sceneCountSlider = template.scenes.size.toFloat().coerceIn(3f, 8f)
                                                viewModel.applyVideoSceneTemplate(template)
                                                activeStep = 1
                                            }
                                        ) {
                                            Text("Use")
                                        }
                                        if (templateAdminMode) {
                                            val adminActions = if (template.isCustom) {
                                                listOf(
                                                    OmniOverflowAction("Edit Template", Icons.Filled.Edit) {
                                                        openTemplateInEditor(template, asClone = false)
                                                    },
                                                    OmniOverflowAction("Delete Template", Icons.Filled.Delete, isDestructive = true) {
                                                        template.customDbId?.let { id ->
                                                            viewModel.deleteCustomVideoTemplate(id, template.name)
                                                        }
                                                    }
                                                )
                                            } else {
                                                listOf(
                                                    OmniOverflowAction("Clone & Customize", Icons.Filled.ContentCopy) {
                                                        openTemplateInEditor(template, asClone = true)
                                                    }
                                                )
                                            }
                                            OmniOverflowMenuButton(actions = adminActions)
                                        }
                                    }
                                }
                                Text(
                                    template.description,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant,
                                    maxLines = 2,
                                    overflow = TextOverflow.Ellipsis
                                )
                            }
                        }
                    }
                }

                // =============================================================
                // STEP 2: SCENES, AUDIT HOOK & YT/IG/FB SEO SHORTCUT
                // =============================================================
                1 -> {
                    item {
                        OmniSurfaceCard {
                            OmniSectionHeader(
                                title = "Scene Storyboard (${draftScenes.size})",
                                subtitle = "$aspectRatio · $voice · $stylePreset",
                                trailing = {
                                    OmniOverflowMenuButton(
                                        actions = listOf(
                                            OmniOverflowAction("Add Blank Scene", Icons.Filled.Add) {
                                                viewModel.addBlankScene()
                                            },
                                            OmniOverflowAction("Save Storyboard to Room", Icons.Filled.Save) {
                                                viewModel.saveDraftStoryboard(topic, aspectRatio, stylePreset, voice, burnSubtitles)
                                            },
                                            OmniOverflowAction("Open Render Settings", Icons.Filled.Tune) {
                                                showRenderSettingsSheet = true
                                            },
                                            OmniOverflowAction("Open Content Hub (SEO)", Icons.Filled.Campaign) {
                                                onOpenContentHub()
                                            }
                                        )
                                    )
                                }
                            )

                            OutlinedTextField(
                                value = draftTitle,
                                onValueChange = { viewModel.updateDraftTitle(it) },
                                label = { Text("Production Title") },
                                singleLine = true,
                                modifier = Modifier.fillMaxWidth(),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            )

                            // Max 2 Primary Actions: Audit Hook + Generate YT/IG/FB SEO
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(10.dp)
                            ) {
                                OutlinedButton(
                                    onClick = {
                                        showScriptAuditorDialog = true
                                        viewModel.auditCurrentStoryboard(topic)
                                    },
                                    enabled = draftScenes.isNotEmpty(),
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                                ) {
                                    Icon(Icons.Filled.Verified, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text("Audit Hook")
                                }

                                OutlinedButton(
                                    onClick = {
                                        viewModel.generateAllThreePlatformsSeo(
                                            scriptId = null,
                                            scriptTitle = draftTitle.ifBlank { topic.ifBlank { "Untitled Video" } },
                                            topic = topic.ifBlank { draftTitle },
                                            sceneSummaries = draftScenes.map { it.narration.ifBlank { it.visualPrompt } }
                                        )
                                    },
                                    enabled = draftScenes.isNotEmpty() && !isGeneratingSocialSeo,
                                    modifier = Modifier
                                        .weight(1f)
                                        .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                                ) {
                                    Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(18.dp))
                                    Spacer(modifier = Modifier.width(6.dp))
                                    Text(if (isGeneratingSocialSeo) "SEO..." else "YT/IG/FB SEO")
                                }
                            }
                        }
                    }

                    if (isGenerating) {
                        items(2) {
                            OmniSkeletonCard(lineCount = 3)
                        }
                    } else if (draftScenes.isEmpty()) {
                        item {
                            OmniEmptyState(
                                icon = Icons.Filled.MovieCreation,
                                title = "No scenes drafted yet",
                                helperText = "Start in Step 1 to generate a structured Wan 2.1 storyboard or add scenes manually.",
                                actionLabel = "Go to Step 1 · Idea",
                                onActionClick = { activeStep = 0 }
                            )
                        }
                    } else {
                        itemsIndexed(draftScenes, key = { index, scene -> "${index}_${scene.sceneIndex}" }) { idx, scene ->
                            val isExpanded = expandedSceneIdx == idx
                            val sceneTransKey = "scene_${idx}_${scene.sceneIndex}"
                            val sceneTranslation = translationsByKey[sceneTransKey]
                            val isSceneTranslating = translatingKeys.contains(sceneTransKey)

                            OmniSurfaceCard(
                                containerColor = if (isExpanded) colorScheme.surfaceVariant else colorScheme.surface,
                                borderColor = if (isExpanded) colorScheme.primary.omniAccentBorder() else colorScheme.outline
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clickable { expandedSceneIdx = if (isExpanded) -1 else idx },
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Row(
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                                        ) {
                                            OmniAccentBadge(text = "Scene ${idx + 1}", accent = EmeraldAccent)
                                            if (sceneTranslation != null) {
                                                OmniAccentBadge(text = sceneTranslation.first, accent = colorScheme.primary)
                                            }
                                        }
                                        Spacer(modifier = Modifier.height(4.dp))
                                        Text(
                                            text = scene.narration.ifBlank { scene.visualPrompt.ifBlank { "Tap to edit visual prompt & narration" } },
                                            style = MaterialTheme.typography.bodySmall,
                                            color = colorScheme.onSurfaceVariant,
                                            maxLines = if (isExpanded) 3 else 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                    }
                                    Row(verticalAlignment = Alignment.CenterVertically) {
                                        IconButton(
                                            onClick = { viewModel.toggleTranslateText(sceneTransKey, scene.narration) },
                                            enabled = !isSceneTranslating && scene.narration.isNotBlank()
                                        ) {
                                            Icon(
                                                Icons.Filled.Translate,
                                                contentDescription = "Translate Narration Side-by-Side",
                                                tint = if (sceneTranslation != null) EmeraldAccent else colorScheme.primary
                                            )
                                        }
                                        OmniOverflowMenuButton(
                                            actions = listOf(
                                                OmniOverflowAction(
                                                    label = if (isExpanded) "Collapse Scene" else "Expand Scene",
                                                    icon = if (isExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore
                                                ) { expandedSceneIdx = if (isExpanded) -1 else idx },
                                                OmniOverflowAction(
                                                    label = "Move Up",
                                                    icon = Icons.Filled.ArrowUpward,
                                                    enabled = idx > 0
                                                ) { viewModel.moveScene(idx, -1) },
                                                OmniOverflowAction(
                                                    label = "Move Down",
                                                    icon = Icons.Filled.ArrowDownward,
                                                    enabled = idx < draftScenes.lastIndex
                                                ) { viewModel.moveScene(idx, 1) },
                                                OmniOverflowAction(
                                                    label = "Delete Scene",
                                                    icon = Icons.Filled.Delete,
                                                    isDestructive = true
                                                ) { viewModel.deleteScene(idx) }
                                            )
                                        )
                                    }
                                }

                                AnimatedVisibility(visible = isExpanded) {
                                    Column(
                                        modifier = Modifier.padding(top = 6.dp),
                                        verticalArrangement = Arrangement.spacedBy(10.dp)
                                    ) {
                                        OutlinedTextField(
                                            value = scene.visualPrompt,
                                            onValueChange = { viewModel.updateScene(idx, scene.copy(visualPrompt = it)) },
                                            label = { Text("Wan 2.1 Visual Prompt") },
                                            modifier = Modifier.fillMaxWidth(),
                                            minLines = 2
                                        )
                                        OutlinedTextField(
                                            value = scene.narration,
                                            onValueChange = { viewModel.updateScene(idx, scene.copy(narration = it)) },
                                            label = { Text("Voiceover Narration ($voice)") },
                                            modifier = Modifier.fillMaxWidth()
                                        )
                                        HorizontalDivider(color = colorScheme.outline)
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Column(modifier = Modifier.weight(1f)) {
                                                Text(
                                                    "Character dialogue (${scene.dialogue.size})",
                                                    style = MaterialTheme.typography.labelLarge
                                                )
                                                Text(
                                                    "When added, these lines replace narration for this scene.",
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = colorScheme.onSurfaceVariant
                                                )
                                            }
                                            OutlinedButton(
                                                onClick = {
                                                    val nextSpeaker = "Character ${scene.dialogue.size + 1}"
                                                    viewModel.updateScene(
                                                        idx,
                                                        scene.copy(
                                                            dialogue = scene.dialogue + StoryboardDialogueLine(
                                                                characterName = nextSpeaker,
                                                                voice = voices.first(),
                                                                text = ""
                                                            )
                                                        )
                                                    )
                                                }
                                            ) {
                                                Icon(Icons.Filled.Add, contentDescription = null)
                                                Text("Add speaker")
                                            }
                                        }
                                        scene.dialogue.forEachIndexed { lineIndex, line ->
                                            Surface(
                                                color = colorScheme.surface,
                                                shape = RoundedCornerShape(10.dp),
                                                border = BorderStroke(1.dp, colorScheme.outline),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Column(
                                                    modifier = Modifier.padding(10.dp),
                                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Row(
                                                        verticalAlignment = Alignment.CenterVertically
                                                    ) {
                                                        OutlinedTextField(
                                                            value = line.characterName,
                                                            onValueChange = { value ->
                                                                val updated = scene.dialogue.toMutableList()
                                                                updated[lineIndex] = line.copy(characterName = value)
                                                                viewModel.updateScene(idx, scene.copy(dialogue = updated))
                                                            },
                                                            label = { Text("Speaker name") },
                                                            singleLine = true,
                                                            modifier = Modifier.weight(1f)
                                                        )
                                                        IconButton(
                                                            onClick = {
                                                                viewModel.updateScene(
                                                                    idx,
                                                                    scene.copy(dialogue = scene.dialogue.filterIndexed { i, _ -> i != lineIndex })
                                                                )
                                                            }
                                                        ) {
                                                            Icon(
                                                                Icons.Filled.Delete,
                                                                contentDescription = "Remove speaker line",
                                                                tint = CrimsonDanger
                                                            )
                                                        }
                                                    }
                                                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                                        items(voices) { speakerVoice ->
                                                            FilterChip(
                                                                selected = line.voice == speakerVoice,
                                                                onClick = {
                                                                    val updated = scene.dialogue.toMutableList()
                                                                    updated[lineIndex] = line.copy(voice = speakerVoice)
                                                                    viewModel.updateScene(idx, scene.copy(dialogue = updated))
                                                                },
                                                                label = { Text(speakerVoice) }
                                                            )
                                                        }
                                                    }
                                                    OutlinedTextField(
                                                        value = line.text,
                                                        onValueChange = { value ->
                                                            val updated = scene.dialogue.toMutableList()
                                                            updated[lineIndex] = line.copy(text = value)
                                                            viewModel.updateScene(idx, scene.copy(dialogue = updated))
                                                        },
                                                        label = { Text("${line.characterName} dialogue") },
                                                        modifier = Modifier.fillMaxWidth(),
                                                        minLines = 2
                                                    )
                                                }
                                            }
                                        }
                                        if (isSceneTranslating) {
                                            Text("Translating narration...", style = MaterialTheme.typography.labelMedium, color = AmberWarning)
                                        }
                                        sceneTranslation?.let { (langLabel, translatedNarration) ->
                                            Surface(
                                                color = colorScheme.background,
                                                border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f)),
                                                shape = RoundedCornerShape(8.dp),
                                                modifier = Modifier.fillMaxWidth()
                                            ) {
                                                Row(
                                                    modifier = Modifier.padding(10.dp),
                                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                                ) {
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text("Original", style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                                                        Text(scene.narration, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                    Column(modifier = Modifier.weight(1f)) {
                                                        Text(langLabel, style = MaterialTheme.typography.labelSmall, color = EmeraldAccent)
                                                        Text(translatedNarration, style = MaterialTheme.typography.bodySmall)
                                                    }
                                                }
                                            }
                                        }
                                    }
                                }
                            }
                        }

                        item {
                            Button(
                                onClick = {
                                    viewModel.enqueueDraftForBatchRender(
                                        aspectRatio = aspectRatio,
                                        voice = voice,
                                        burnSubtitles = burnSubtitles,
                                        downloadIndividualScenes = downloadIndividualScenes,
                                        directYoutubeUpload = appSettings.directKaggleYoutubeUpload,
                                        youtubePrivacyStatus = appSettings.youtubeDefaultPrivacy,
                                        directFbIgUpload = appSettings.directKaggleMetaReelsUpload
                                    )
                                    activeStep = 2
                                },
                                enabled = draftScenes.isNotEmpty(),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Queue Background Render (Kaggle T4 x2)")
                            }
                        }
                    }

                    // Collapsible Saved Storyboards
                    if (savedStoryboards.isNotEmpty()) {
                        item {
                            OmniSurfaceCard(onClick = { showSavedStoryboards = !showSavedStoryboards }) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        "Saved Storyboards (${savedStoryboards.size})",
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold
                                    )
                                    Icon(
                                        imageVector = if (showSavedStoryboards) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                        contentDescription = "Toggle Saved Storyboards",
                                        tint = colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                        }
                        if (showSavedStoryboards) {
                            items(savedStoryboards, key = { it.id }) { sb ->
                                OmniSurfaceCard {
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.SpaceBetween,
                                        verticalAlignment = Alignment.CenterVertically
                                    ) {
                                        Column(modifier = Modifier.weight(1f)) {
                                            Text(sb.title, style = MaterialTheme.typography.titleSmall, maxLines = 1, overflow = TextOverflow.Ellipsis)
                                            Text(
                                                "${sb.aspectRatio} · ${sb.voice} · ${sb.stylePreset}",
                                                style = MaterialTheme.typography.labelSmall,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        OutlinedButton(onClick = {
                                            viewModel.loadStoryboardIntoEditor(sb)
                                            expandedSceneIdx = 0
                                        }) {
                                            Text("Load")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }

                // =============================================================
                // STEP 3: BATCH QUEUE, LIVE KAGGLE STEPPER & RESUMABLE DOWNLOADS
                // =============================================================
                2 -> {
                    // Compact GPU Quota & Account Pool Card
                    item {
                        val remainingHrs = (30.0 - weeklyUsedGpuHours).coerceAtLeast(0.0)
                        val isLowQuota = remainingHrs < 2.0
                        OmniSurfaceCard(
                            borderColor = if (isLowQuota) CrimsonDanger else colorScheme.outline
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Kaggle Weekly GPU Quota", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.SemiBold)
                                Text(
                                    text = "Used: %.1f hrs / 30.0 hrs".format(weeklyUsedGpuHours),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (isLowQuota) CrimsonDanger else EmeraldAccent
                                )
                            }
                            LinearProgressIndicator(
                                progress = { (weeklyUsedGpuHours / 30.0).toFloat().coerceIn(0f, 1f) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clip(RoundedCornerShape(4.dp)),
                                color = if (isLowQuota) CrimsonDanger else EmeraldAccent
                            )
                            if (isLowQuota) {
                                Row(
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                ) {
                                    Icon(Icons.Filled.Warning, contentDescription = null, tint = CrimsonDanger, modifier = Modifier.size(16.dp))
                                    Text(
                                        text = "Only %.1f hrs remaining · Auto-rotation triggers < 1.0 hr".format(remainingHrs),
                                        style = MaterialTheme.typography.labelSmall,
                                        color = CrimsonDanger
                                    )
                                }
                            }
                            if (kaggleAccounts.isNotEmpty()) {
                                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    items(kaggleAccounts, key = { it.id }) { acct ->
                                        FilterChip(
                                            selected = acct.id == activeKaggleAccountId,
                                            onClick = { viewModel.selectKaggleAccount(acct.id) },
                                            label = { Text(acct.label) }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Active / Resumable Download Progress Card
                    val activeOrRecentDownloads = downloads.filter { it.status == "DOWNLOADING" }.ifEmpty {
                        downloads.take(2)
                    }
                    if (activeOrRecentDownloads.isNotEmpty()) {
                        item {
                            OmniSurfaceCard {
                                OmniSectionHeader(
                                    title = "Resumable Download Pipeline",
                                    subtitle = "Range-aware streaming directly into local Vault storage"
                                )
                                activeOrRecentDownloads.forEach { dl ->
                                    val sizeMb = dl.sizeBytes / (1024f * 1024f)
                                    Surface(
                                        color = colorScheme.surfaceVariant,
                                        shape = RoundedCornerShape(10.dp),
                                        border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline),
                                        modifier = Modifier.fillMaxWidth()
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
                                                Row(
                                                    modifier = Modifier.weight(1f),
                                                    verticalAlignment = Alignment.CenterVertically,
                                                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                                                ) {
                                                    Icon(
                                                        Icons.Filled.CloudDownload,
                                                        contentDescription = null,
                                                        tint = if (dl.status == "COMPLETED") EmeraldAccent else colorScheme.primary,
                                                        modifier = Modifier.size(16.dp)
                                                    )
                                                    Text(
                                                        text = dl.fileName,
                                                        style = MaterialTheme.typography.labelMedium,
                                                        maxLines = 1,
                                                        overflow = TextOverflow.Ellipsis
                                                    )
                                                }
                                                OmniAccentBadge(
                                                    text = if (dl.status == "DOWNLOADING") "${dl.progressPercent}%" else dl.status,
                                                    accent = if (dl.status == "COMPLETED") EmeraldAccent else colorScheme.primary
                                                )
                                            }
                                            if (dl.status == "DOWNLOADING") {
                                                LinearProgressIndicator(
                                                    progress = { (dl.progressPercent / 100f).coerceIn(0f, 1f) },
                                                    modifier = Modifier.fillMaxWidth()
                                                )
                                            } else if (sizeMb > 0f) {
                                                Text(
                                                    text = "%.1f MB · %s".format(sizeMb, dl.category),
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

                    // Batch Render Queue Section
                    item {
                        OmniSectionHeader(
                            title = "Background Batch Queue (${renderJobs.size})",
                            subtitle = "Live Kaggle T4 x2 stepper & status",
                            trailing = {
                                OutlinedButton(
                                    onClick = {
                                        val latestPath = renderJobs.firstOrNull { !it.outputFilePath.isNullOrBlank() }?.outputFilePath
                                        thumbnailStudioTarget = latestPath to draftTitle.ifBlank { topic.ifBlank { "OmniAI Video" } }
                                    }
                                ) {
                                    Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                    Spacer(modifier = Modifier.width(4.dp))
                                    Text("Thumbnails")
                                }
                            }
                        )
                    }

                    if (isUploadingToYouTube || youTubeUploadStatus.isNotBlank()) {
                        item {
                            OmniSurfaceCard(borderColor = EmeraldAccent.copy(alpha = 0.6f)) {
                                Text(
                                    text = if (isUploadingToYouTube) "YouTube Resumable Upload ($youTubeUploadProgress%)" else "YouTube Direct Uploader",
                                    style = MaterialTheme.typography.titleSmall,
                                    color = EmeraldAccent
                                )
                                if (youTubeUploadStatus.isNotBlank()) {
                                    Text(
                                        text = youTubeUploadStatus,
                                        style = MaterialTheme.typography.bodySmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                if (isUploadingToYouTube) {
                                    LinearProgressIndicator(
                                        progress = { (youTubeUploadProgress / 100f).coerceIn(0f, 1f) },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                        }
                    }

                    // v5.4 Pro Adobe Workflow (Native Installed Apps — Zero Browser / Zero Custom Tabs)
                    item {
                        val latestRenderedJob = renderJobs.firstOrNull {
                            it.status == "COMPLETED" && !it.outputFilePath.isNullOrBlank()
                        }
                        val targetTitle = latestRenderedJob?.title?.ifBlank { draftTitle }
                            ?: draftTitle.ifBlank { activeNiche?.name ?: "OmniAI Video" }
                        ProAdobeWorkflowCard(
                            expressStatus = adobeExpressStatus,
                            fireflyStatus = adobeFireflyStatus,
                            isBusy = isLaunchingAdobeBridge,
                            onGenerateInFirefly = {
                                viewModel.launchAdobeFireflyForVideo(
                                    title = targetTitle,
                                    aspectRatio = aspectRatio
                                )
                            },
                            onEditFrameInExpress = {
                                viewModel.launchAdobeExpressForVideo(
                                    videoFilePath = latestRenderedJob?.outputFilePath,
                                    title = targetTitle,
                                    hookText = targetTitle.split(" ").take(3).joinToString(" ")
                                )
                            },
                            onChangeBoundApp = { target -> adobePickerTarget = target }
                        )
                    }

                    if (renderJobs.isEmpty()) {
                        item {
                            OmniEmptyState(
                                icon = Icons.Filled.PlayArrow,
                                title = "No active Kaggle renders",
                                helperText = "Draft scenes in Step 1 & 2, then queue a background Dual-GPU render.",
                                actionLabel = if (draftScenes.isNotEmpty()) "Queue Current Draft" else "Start a Video Idea",
                                onActionClick = {
                                    if (draftScenes.isNotEmpty()) {
                                        viewModel.enqueueDraftForBatchRender(
                                            aspectRatio = aspectRatio,
                                            voice = voice,
                                            burnSubtitles = burnSubtitles,
                                            downloadIndividualScenes = downloadIndividualScenes,
                                            directYoutubeUpload = appSettings.directKaggleYoutubeUpload,
                                            youtubePrivacyStatus = appSettings.youtubeDefaultPrivacy,
                                            directFbIgUpload = appSettings.directKaggleMetaReelsUpload
                                        )
                                    } else {
                                        activeStep = 0
                                    }
                                }
                            )
                        }
                    } else {
                        items(renderJobs, key = { it.id }) { job ->
                            OmniSurfaceCard {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text(
                                        text = job.title,
                                        style = MaterialTheme.typography.titleSmall,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f)
                                    )
                                    OmniAccentBadge(
                                        text = job.status,
                                        accent = when (job.status) {
                                            "COMPLETED" -> EmeraldAccent
                                            "FAILED" -> CrimsonDanger
                                            else -> colorScheme.primary
                                        }
                                    )
                                }

                                // Live Kaggle Stepper (Queued -> Pushed -> Dual T4 -> Download -> Done)
                                KaggleRenderStepper(status = job.status)

                                Text(
                                    text = job.statusMessage,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                                LinearProgressIndicator(
                                    progress = { (job.progressPercent / 100f).coerceIn(0f, 1f) },
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .clip(RoundedCornerShape(4.dp))
                                )

                                // Max 2 primary actions + overflow for Thumbnail Studio / YouTube Upload
                                Row(
                                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    if (job.status == "FAILED") {
                                        Button(onClick = { viewModel.retryRenderJob(job) }) {
                                            Icon(Icons.Filled.Refresh, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Retry")
                                        }
                                    } else if (job.status == "COMPLETED" && job.outputFilePath.isNullOrBlank()) {
                                        Button(onClick = { viewModel.downloadKaggleCopyToPhone(job) }) {
                                            Icon(Icons.Filled.CloudDownload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Download Copy to Phone")
                                        }
                                    } else if (job.status == "COMPLETED" && !job.outputFilePath.isNullOrBlank()) {
                                        Button(
                                            enabled = !isUploadingToYouTube,
                                            onClick = {
                                                viewModel.uploadVideoToYouTube(
                                                    videoFilePath = job.outputFilePath,
                                                    title = job.title,
                                                    description = "Rendered with OmniAI-Studio Dual-GPU Pipeline.",
                                                    tags = listOf("OmniAI", "AI", "Shorts"),
                                                    privacyStatus = appSettings.youtubeDefaultPrivacy
                                                )
                                            }
                                        ) {
                                            Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Upload YT")
                                        }
                                        OutlinedButton(onClick = {
                                            thumbnailStudioTarget = job.outputFilePath to job.title
                                        }) {
                                            Icon(Icons.Filled.Image, contentDescription = null, modifier = Modifier.size(16.dp))
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Thumbnail")
                                        }
                                    }
                                    OutlinedButton(onClick = { viewModel.deleteRenderJob(job) }) {
                                        Text("Remove")
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
    // RENDER SETTINGS MODAL BOTTOM SHEET (Progressive Disclosure for Step 1)
    // =========================================================================
    if (showRenderSettingsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showRenderSettingsSheet = false },
            sheetState = sheetState,
            containerColor = colorScheme.surfaceVariant,
            dragHandle = { BottomSheetDefaults.DragHandle(color = colorScheme.outline) }
        ) {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .verticalScroll(rememberScrollState())
                    .padding(horizontal = OmniDesignTokens.ScreenHorizontalPadding)
                    .padding(bottom = 28.dp),
                verticalArrangement = Arrangement.spacedBy(14.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text(
                            text = "Render Settings",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Configure aspect ratio, voice, Character DNA, loop & audio ducking",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    TextButton(onClick = { showRenderSettingsSheet = false }) {
                        Text("Done")
                    }
                }

                // Character DNA Lock
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Box {
                        FilterChip(
                            selected = selectedCharacter != null,
                            onClick = { showDnaMenu = true },
                            leadingIcon = { Icon(Icons.Filled.Face, contentDescription = null) },
                            label = {
                                Text(
                                    selectedCharacter?.let { "Character DNA: ${it.name}" } ?: "Lock Character DNA"
                                )
                            }
                        )
                        DropdownMenu(
                            expanded = showDnaMenu,
                            onDismissRequest = { showDnaMenu = false }
                        ) {
                            DropdownMenuItem(
                                text = { Text("No Character Lock (Freeform)") },
                                onClick = {
                                    viewModel.selectCharacterDna(null)
                                    showDnaMenu = false
                                }
                            )
                            characterProfiles.forEach { profile ->
                                DropdownMenuItem(
                                    text = { Text("${profile.name} (${profile.defaultStyle})") },
                                    onClick = {
                                        viewModel.selectCharacterDna(profile.id)
                                        stylePreset = profile.defaultStyle
                                        showDnaMenu = false
                                    }
                                )
                            }
                            HorizontalDivider(color = colorScheme.outline)
                            DropdownMenuItem(
                                text = { Text("+ Create / Manage Character DNA", color = EmeraldAccent) },
                                onClick = {
                                    showDnaMenu = false
                                    showDnaDialog = true
                                }
                            )
                        }
                    }
                    TextButton(onClick = { showDnaDialog = true }) {
                        Text("Manage DNA (${characterProfiles.size})")
                    }
                }

                if (selectedCharacter != null) {
                    Text(
                        text = "Locked Descriptor: ${selectedCharacter.appearancePrompt}",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldAccent
                    )
                }

                HorizontalDivider(color = colorScheme.outline)

                // Scene Count Slider
                Column {
                    Text("Scene Count: ${sceneCountSlider.roundToInt()} scenes (3–8)", style = MaterialTheme.typography.labelMedium)
                    Slider(
                        value = sceneCountSlider,
                        onValueChange = { sceneCountSlider = it },
                        valueRange = 3f..8f,
                        steps = 4
                    )
                }

                // Aspect Ratio
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text("Aspect:", style = MaterialTheme.typography.labelMedium)
                    listOf("9:16", "16:9").forEach { ratio ->
                        FilterChip(
                            selected = aspectRatio == ratio,
                            onClick = { aspectRatio = ratio },
                            label = { Text(ratio) }
                        )
                    }
                }

                // Visual Style Preset
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Visual Style Preset", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(stylePresets) { preset ->
                            FilterChip(
                                selected = stylePreset == preset,
                                onClick = { stylePreset = preset },
                                label = { Text(preset) }
                            )
                        }
                    }
                }

                // Edge-TTS Voiceover
                Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Text("Edge-TTS Voiceover", style = MaterialTheme.typography.labelMedium)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        items(voices) { v ->
                            FilterChip(
                                selected = voice == v,
                                onClick = { voice = v },
                                label = { Text(v) }
                            )
                        }
                    }
                }

                HorizontalDivider(color = colorScheme.outline)

                // Toggles
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Burn Bold Subtitles (SRT)", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = burnSubtitles, onCheckedChange = { burnSubtitles = it })
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Loop, contentDescription = null, tint = EmeraldAccent)
                        Column {
                            Text("Seamless Loop (Reels/Shorts)", style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "Syntactic lead-in ending + Kaggle 0.25s tail micro-fade",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    Switch(
                        checked = draftSeamlessLoop,
                        onCheckedChange = { viewModel.setDraftSeamlessLoop(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Also Download Individual scene_X.mp4", style = MaterialTheme.typography.bodyMedium)
                    Switch(checked = downloadIndividualScenes, onCheckedChange = { downloadIndividualScenes = it })
                }

                HorizontalDivider(color = colorScheme.outline)

                // v5.2 Direct Kaggle -> Telegram 2 GB MTProto Uploader Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Direct Kaggle → Telegram Upload (Up to 2 GB)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Kaggle worker uploads via Telethon MTProto directly to your chat/channel",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appSettings.directKaggleTelegramUpload,
                        onCheckedChange = { viewModel.setDirectKaggleTelegramUpload(it) }
                    )
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Save Mobile Data (Skip Phone Download if Sent to Telegram)", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Skips downloading large MP4 to phone; tap 'Download Copy to Phone' anytime",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appSettings.skipPhoneDownloadWhenSentToTelegram,
                        enabled = appSettings.directKaggleTelegramUpload,
                        onCheckedChange = { viewModel.setSkipPhoneDownloadWhenSentToTelegram(it) }
                    )
                }

                HorizontalDivider(color = colorScheme.outline)

                // v5.3 Direct Kaggle -> YouTube & Meta FB/IG Reels Auto-Uploader Controls
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Direct Kaggle → YouTube Auto-Upload", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Kaggle worker uploads final MP4 + thumb.jpg directly via YouTube API v3",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appSettings.directKaggleYoutubeUpload,
                        onCheckedChange = { viewModel.setDirectKaggleYoutubeUpload(it) }
                    )
                }

                Row(
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("YT Privacy:", style = MaterialTheme.typography.labelMedium)
                    listOf("private", "unlisted", "public").forEach { priv ->
                        FilterChip(
                            selected = appSettings.youtubeDefaultPrivacy.equals(priv, ignoreCase = true),
                            onClick = { viewModel.setYoutubeDefaultPrivacy(priv) },
                            label = { Text(priv.replaceFirstChar { it.uppercase() }) }
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Direct Kaggle → Meta FB/IG Reels", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Optional headless Facebook Page / IG Business Reels publish after render",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    Switch(
                        checked = appSettings.directKaggleMetaReelsUpload,
                        onCheckedChange = { viewModel.setDirectKaggleMetaReelsUpload(it) }
                    )
                }

                HorizontalDivider(color = colorScheme.outline)

                // Narration Speed
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Speed, contentDescription = null, tint = EmeraldAccent)
                    Text("Video & Narration Speed (${draftSpeedFactor}x)", style = MaterialTheme.typography.labelMedium)
                }
                LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    items(listOf(0.75f, 1.0f, 1.25f, 1.5f)) { spd ->
                        FilterChip(
                            selected = draftSpeedFactor == spd,
                            onClick = { viewModel.updateDraftSpeedFactor(spd) },
                            label = { Text("${spd}x") }
                        )
                    }
                }

                // Watermark & BGM Ducking
                OutlinedTextField(
                    value = draftWatermarkText,
                    onValueChange = { viewModel.updateDraftWatermarkText(it) },
                    label = { Text("Bottom-Right Watermark Text (e.g. @OmniAIStudio)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = draftBgmUrl,
                    onValueChange = { viewModel.updateDraftBgmUrl(it) },
                    label = { Text("Background Music URL (Auto-Ducked 15%)") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )

                // Reference Image Picker (I2V)
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedButton(
                        onClick = {
                            refImagePickerLauncher.launch(
                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                            )
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(Icons.Filled.Image, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (selectedRefImagePath.isBlank()) "Pick Ref Image (I2V)" else "Ref Image: ref_image.jpg")
                    }
                    if (selectedRefImagePath.isNotBlank()) {
                        TextButton(onClick = { viewModel.clearReferenceImage() }) {
                            Text("Clear")
                        }
                    }
                }
            }
        }
    }

    // =========================================================================
    // DIALOGS (Character DNA, URL Importer, Series Planner, Script Auditor, Niche)
    // =========================================================================
    if (showDnaDialog) {
        var newDnaName by remember { mutableStateOf("") }
        var newDnaAppearance by remember { mutableStateOf("") }
        var newDnaNegative by remember { mutableStateOf("blurry, deformed hands, inconsistent face, watermark") }
        var newDnaStyle by remember { mutableStateOf(stylePreset) }

        AlertDialog(
            onDismissRequest = { showDnaDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Character DNA & Style Lock Profiles") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    characterProfiles.forEach { profile ->
                        Surface(
                            color = colorScheme.surfaceVariant,
                            border = BorderStroke(
                                1.dp,
                                if (profile.id == selectedCharacterDnaId) EmeraldAccent else colorScheme.outline
                            ),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(8.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text("${profile.name} (${profile.defaultStyle})", style = MaterialTheme.typography.titleSmall)
                                    Text(profile.appearancePrompt.take(80), style = MaterialTheme.typography.labelSmall, color = colorScheme.onSurfaceVariant)
                                }
                                Row {
                                    TextButton(onClick = {
                                        viewModel.selectCharacterDna(profile.id)
                                        stylePreset = profile.defaultStyle
                                        showDnaDialog = false
                                    }) { Text("Lock") }
                                    IconButton(onClick = { viewModel.deleteCharacterProfile(profile) }) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Delete DNA", tint = CrimsonDanger)
                                    }
                                }
                            }
                        }
                    }
                    HorizontalDivider(color = colorScheme.outline)
                    OutlinedTextField(
                        value = newDnaName,
                        onValueChange = { newDnaName = it },
                        label = { Text("Character Name (e.g. Arya Cyberpunk)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDnaAppearance,
                        onValueChange = { newDnaAppearance = it },
                        label = { Text("Exact Visual Appearance Descriptor") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDnaNegative,
                        onValueChange = { newDnaNegative = it },
                        label = { Text("Negative Prompt (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newDnaStyle,
                        onValueChange = { newDnaStyle = it },
                        label = { Text("Default Visual Style") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            viewModel.saveCharacterProfile(
                                name = newDnaName,
                                appearancePrompt = newDnaAppearance,
                                negativePrompt = newDnaNegative,
                                defaultStyle = newDnaStyle
                            )
                            newDnaName = ""
                            newDnaAppearance = ""
                            showDnaDialog = false
                        },
                        enabled = newDnaName.isNotBlank() && newDnaAppearance.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Save & Lock Character DNA") }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showDnaDialog = false }) { Text("Close") }
            }
        )
    }

    if (showDirectorUrlDialog) {
        AlertDialog(
            onDismissRequest = { showDirectorUrlDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Read Web URL into Storyboard Topic") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Paste any HTTPS article URL. Jsoup will strip boilerplate HTML and import the core story into your Video Topic.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = directorUrlInput,
                        onValueChange = { directorUrlInput = it },
                        label = { Text("https://example.com/news-story") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val target = directorUrlInput
                        showDirectorUrlDialog = false
                        directorUrlInput = ""
                        viewModel.readWebUrlForDirector(target) { extracted ->
                            topic = extracted
                        }
                    },
                    enabled = directorUrlInput.isNotBlank()
                ) { Text("Import Article") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showDirectorUrlDialog = false }) { Text("Cancel") }
            }
        )
    }

    if (showSeriesPlannerSheet) {
        SeriesPlannerSheet(
            activeNiche = activeNiche,
            episodes = seriesEpisodes,
            isGenerating = isGeneratingSeriesPlan,
            onGenerateRoadmap = { count, theme -> viewModel.generateSeriesRoadmap(count, theme) },
            onLoadEpisodeIntoDirector = { ep ->
                viewModel.loadSeriesEpisodeIntoDirector(ep)
                activeStep = 0
            },
            onDismiss = { showSeriesPlannerSheet = false }
        )
    }

    if (showScriptAuditorDialog) {
        ScriptAuditorDialog(
            auditResult = scriptAuditResult,
            isAuditing = isAuditingScript,
            isOptimizing = isOptimizingScenes,
            lockedCharacterName = selectedCharacter?.name,
            onAutoOptimizeWeakScenes = { viewModel.autoOptimizeWeakScenes(topic) },
            onDismiss = { showScriptAuditorDialog = false }
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

    thumbnailStudioTarget?.let { (videoPath, defaultTitle) ->
        ThumbnailStudioSheet(
            videoFilePath = videoPath,
            defaultTitle = defaultTitle,
            defaultAspectRatio = aspectRatio,
            adobeExpressStatus = adobeExpressStatus,
            adobeFireflyStatus = adobeFireflyStatus,
            isLaunchingAdobe = isLaunchingAdobeBridge,
            onRequestAiHook = { contextTitle, onHookReady ->
                viewModel.generateViralThumbnailHook(contextTitle, onHookReady)
            },
            onThumbnailExported = { exportedPath ->
                viewModel.setActiveThumbnailPath(exportedPath)
            },
            onLaunchAdobeFirefly = { chosenAspect ->
                viewModel.launchAdobeFireflyForVideo(
                    title = defaultTitle,
                    aspectRatio = chosenAspect
                )
            },
            onLaunchAdobeExpress = { frameBmp, tsMs, hookText ->
                viewModel.launchAdobeExpressForVideo(
                    videoFilePath = videoPath,
                    title = defaultTitle,
                    hookText = hookText,
                    timestampMs = tsMs,
                    bitmapOverride = frameBmp
                )
            },
            onBindAdobeApp = { target, pkg -> viewModel.bindAdobeAppPackage(target, pkg) },
            onResetAdobeApp = { target -> viewModel.resetAdobeAppBinding(target) },
            onDismiss = { thumbnailStudioTarget = null }
        )
    }

    adobePickerTarget?.let { target ->
        val currentPkg = if (target == AdobeTargetApp.ADOBE_EXPRESS) {
            adobeExpressStatus.configuredPackage
        } else {
            adobeFireflyStatus.configuredPackage
        }
        AdobeAppPickerDialog(
            targetApp = target,
            currentPackage = currentPkg,
            onSelectPackage = { pkg ->
                viewModel.bindAdobeAppPackage(target, pkg)
                adobePickerTarget = null
            },
            onResetDefault = {
                viewModel.resetAdobeAppBinding(target)
                adobePickerTarget = null
            },
            onDismiss = { adobePickerTarget = null }
        )
    }
}

@Composable
private fun KaggleRenderStepper(status: String) {
    val colorScheme = MaterialTheme.colorScheme
    val stages = listOf("Queued", "Pushed", "Dual T4", "Fetch", "Done")
    val activeIdx = when (status.uppercase()) {
        "QUEUED" -> 0
        "PUSHED" -> 1
        "RENDERING" -> 2
        "DOWNLOADING" -> 3
        "COMPLETED" -> 4
        "FAILED" -> 2
        else -> 0
    }
    val isFailed = status.uppercase() == "FAILED"

    Row(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 4.dp),
        horizontalArrangement = Arrangement.SpaceBetween,
        verticalAlignment = Alignment.CenterVertically
    ) {
        stages.forEachIndexed { idx, label ->
            val reached = idx <= activeIdx
            val dotColor = when {
                isFailed && idx == activeIdx -> CrimsonDanger
                reached -> EmeraldAccent
                else -> colorScheme.outline
            }
            Column(
                horizontalAlignment = Alignment.CenterHorizontally,
                modifier = Modifier.weight(1f)
            ) {
                Box(
                    modifier = Modifier
                        .size(18.dp)
                        .clip(CircleShape)
                        .background(dotColor),
                    contentAlignment = Alignment.Center
                ) {
                    if (reached && !(isFailed && idx == activeIdx)) {
                        Icon(
                            Icons.Filled.Check,
                            contentDescription = null,
                            tint = Color.Black,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                }
                Spacer(modifier = Modifier.height(3.dp))
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelSmall,
                    fontFamily = FontFamily.Monospace,
                    color = if (reached) colorScheme.onSurface else colorScheme.onSurfaceVariant,
                    maxLines = 1
                )
            }
        }
    }
}
