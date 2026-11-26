package com.omniai.studio.ui.components

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.core.RepeatMode
import androidx.compose.animation.core.animateFloat
import androidx.compose.animation.core.infiniteRepeatable
import androidx.compose.animation.core.rememberInfiniteTransition
import androidx.compose.animation.core.tween
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
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
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AccountTree
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Download
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material.icons.filled.Menu
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material.icons.filled.VideoLibrary
import androidx.compose.material.icons.filled.Wifi
import androidx.compose.material.icons.filled.Workspaces
import androidx.compose.material3.BottomSheetDefaults
import androidx.compose.material3.Button
import androidx.compose.material3.DrawerValue
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.ModalDrawerSheet
import androidx.compose.material3.ModalNavigationDrawer
import androidx.compose.material3.NavigationBar
import androidx.compose.material3.NavigationBarItem
import androidx.compose.material3.NavigationBarItemDefaults
import androidx.compose.material3.NavigationDrawerItem
import androidx.compose.material3.NavigationDrawerItemDefaults
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SnackbarHost
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberDrawerState
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniai.studio.cloud.CloudUserRole
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.settings.StudioTabId
import com.omniai.studio.ui.screens.RoleGuardBanner
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.OmniAccentBadge
import com.omniai.studio.ui.theme.OmniDesignTokens
import com.omniai.studio.ui.theme.OmniEmptyState
import com.omniai.studio.ui.theme.OmniSurfaceCard
import com.omniai.studio.ui.theme.omniAccentBorder
import com.omniai.studio.ui.theme.omniAccentContainer
import com.omniai.studio.viewmodel.OmniAiViewModel
import kotlinx.coroutines.launch

/**
 * Icon mapping for all StudioTabId destinations.
 */
fun iconForStudioTab(tab: StudioTabId): ImageVector = when (tab) {
    StudioTabId.HOME -> Icons.Filled.Home
    StudioTabId.CHAT -> Icons.Filled.ChatBubble
    StudioTabId.DIRECTOR -> Icons.Filled.MovieCreation
    StudioTabId.PROJECTS -> Icons.Filled.FolderSpecial
    StudioTabId.MERGER -> Icons.Filled.VideoLibrary
    StudioTabId.TERMINAL -> Icons.Filled.Terminal
    StudioTabId.GALLERY -> Icons.Filled.Download
    StudioTabId.CONTENT_HUB -> Icons.Filled.Storage
    StudioTabId.VAULT -> Icons.Filled.Security
}

/**
 * Hub-and-Spoke Navigation Shell for OmniAI-Studio v5.1:
 * - Clean 4-tab Bottom Navigation Bar by default (Home, Chat, Studio, Projects — still customizable in Settings).
 * - Unified slim 56dp TopAppBar: Left = Hamburger + Compact Active Niche Pill (opens Niche Switcher BottomSheet);
 *   Right = ONLY 2 items (Command Palette Search + Live Status Pill when any background job is active).
 * - Organized ModalNavigationDrawer grouping Secondary Power Tools into:
 *   Create & Media, Developer & Agents, System & Vault.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun AppNavigationShell(
    viewModel: OmniAiViewModel,
    selectedTab: StudioTabId,
    pinnedBottomTabs: List<StudioTabId>,
    activeOverlay: String,
    hideChrome: Boolean,
    appTitle: String,
    snackbarHostState: SnackbarHostState,
    onSelectTab: (StudioTabId) -> Unit,
    onOpenOverlay: (String) -> Unit,
    onOpenCommandPalette: () -> Unit,
    onCreateWorkspaceClick: () -> Unit,
    floatingActionButton: @Composable () -> Unit = {},
    content: @Composable (PaddingValues) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val scope = rememberCoroutineScope()
    val drawerState = rememberDrawerState(initialValue = DrawerValue.Closed)

    val niches by viewModel.niches.collectAsState()
    val activeNiche by viewModel.activeNiche.collectAsState()
    val filterAllNiches by viewModel.filterAllNiches.collectAsState()
    val isAutoFillingNiche by viewModel.isAutoFillingNiche.collectAsState()
    val characterProfiles by viewModel.characterProfiles.collectAsState()
    val hashtagPacks by viewModel.hashtagPacks.collectAsState()
    val youTubeProfiles by viewModel.youTubeProfiles.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val activeWorkspaceId by viewModel.activeWorkspaceId.collectAsState()

    val renderJobs by viewModel.renderJobs.collectAsState()
    val actionsState by viewModel.actionsMonitorState.collectAsState()
    val isTerminalRunning by viewModel.isTerminalRunning.collectAsState()
    val isNetlogEnabled by viewModel.isNetlogEnabled.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val videoScripts by viewModel.videoScriptsWithRelations.collectAsState()
    val rssFeeds by viewModel.rssFeeds.collectAsState()
    val newsDigests by viewModel.newsDigests.collectAsState()
    val localServers by viewModel.localServerCandidates.collectAsState()
    val showAdobeReturnBanner by viewModel.showAdobeReturnBanner.collectAsState()
    val awaitingAdobeSourceApp by viewModel.awaitingAdobeSourceApp.collectAsState()
    val cloudRole by viewModel.cloudRole.collectAsState()
    val lanWebServerState by viewModel.lanWebServerState.collectAsState()
    val deviceHealth by viewModel.deviceHealth.collectAsState()

    // v5.4 Seamless Return-to-App Adobe Thumbnail Import via Lifecycle ON_RESUME
    LifecycleEventEffect(Lifecycle.Event.ON_RESUME) {
        viewModel.onAppResumedCheckAdobeReturn()
    }

    val adobeReturnPhotoPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.attachPickedAdobeThumbnail(uri)
        }
    }

    var showNicheSheet by remember { mutableStateOf(false) }
    var showLiveJobsSheet by remember { mutableStateOf(false) }
    var showLanStudioSheet by remember { mutableStateOf(false) }
    var showNicheEditorDialog by remember { mutableStateOf(false) }
    var editingNiche by remember { mutableStateOf<NicheEntity?>(null) }

    val activeRenderJobs = remember(renderJobs) {
        renderJobs.filter { it.status in setOf("QUEUED", "PUSHED", "RENDERING", "DOWNLOADING") }
    }
    val isActionsActive = actionsState.isWatching || actionsState.status in setOf("QUEUED", "IN_PROGRESS")
    val onlineLanCount = remember(localServers) { localServers.size }
    val hasLiveActivity = activeRenderJobs.isNotEmpty() || isActionsActive || isTerminalRunning || onlineLanCount > 0 || lanWebServerState.isRunning || isNetlogEnabled

    val liveSummaryLabel = remember(activeRenderJobs.size, isActionsActive, isTerminalRunning, onlineLanCount, lanWebServerState.isRunning) {
        when {
            activeRenderJobs.isNotEmpty() -> "GPU ${activeRenderJobs.size}"
            isActionsActive -> "CI Live"
            lanWebServerState.isRunning -> "LAN ON"
            isTerminalRunning -> "Shell"
            onlineLanCount > 0 -> "LAN $onlineLanCount"
            else -> "Live"
        }
    }

    fun closeDrawerAnd(action: () -> Unit) {
        scope.launch {
            drawerState.close()
            action()
        }
    }

    val drawerGesturesEnabled = activeOverlay == "NONE" && !hideChrome

    ModalNavigationDrawer(
        drawerState = drawerState,
        gesturesEnabled = drawerGesturesEnabled,
        drawerContent = {
            ModalDrawerSheet(
                drawerContainerColor = colorScheme.surface,
                drawerContentColor = colorScheme.onSurface,
                modifier = Modifier.width(312.dp)
            ) {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .verticalScroll(rememberScrollState())
                        .padding(vertical = 12.dp)
                ) {
                    // Drawer Brand & Workspace Header
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(horizontal = 16.dp, vertical = 8.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text(
                                    text = appTitle,
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = colorScheme.onSurface,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis
                                )
                                Text(
                                    text = "Hub & Power Tools · v5.5",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                            OmniAccentBadge(text = "AMOLED", accent = colorScheme.primary)
                        }

                        // Compact Workspace Profile Switcher
                        if (workspaces.isNotEmpty()) {
                            Surface(
                                color = colorScheme.surfaceVariant,
                                border = BorderStroke(OmniDesignTokens.BorderWidth, colorScheme.outline),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
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
                                            verticalAlignment = Alignment.CenterVertically,
                                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Workspaces,
                                                contentDescription = null,
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(16.dp)
                                            )
                                            Text(
                                                text = "Workspace Profile",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = colorScheme.onSurfaceVariant
                                            )
                                        }
                                        IconButton(
                                            onClick = { closeDrawerAnd(onCreateWorkspaceClick) },
                                            modifier = Modifier.size(28.dp)
                                        ) {
                                            Icon(
                                                Icons.Filled.Add,
                                                contentDescription = "New Workspace Profile",
                                                tint = colorScheme.primary,
                                                modifier = Modifier.size(18.dp)
                                            )
                                        }
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                                    ) {
                                        workspaces.take(3).forEach { ws ->
                                            val isWsSelected = ws.id == activeWorkspaceId
                                            Surface(
                                                onClick = { viewModel.switchWorkspace(ws.id) },
                                                shape = RoundedCornerShape(8.dp),
                                                color = if (isWsSelected) colorScheme.primary.omniAccentContainer() else Color.Transparent,
                                                border = BorderStroke(
                                                    1.dp,
                                                    if (isWsSelected) colorScheme.primary.omniAccentBorder() else colorScheme.outline
                                                ),
                                                modifier = Modifier.weight(1f)
                                            ) {
                                                Text(
                                                    text = ws.name,
                                                    style = MaterialTheme.typography.labelSmall,
                                                    color = if (isWsSelected) colorScheme.primary else colorScheme.onSurfaceVariant,
                                                    maxLines = 1,
                                                    overflow = TextOverflow.Ellipsis,
                                                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 6.dp)
                                                )
                                            }
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(
                        color = colorScheme.outline,
                        modifier = Modifier.padding(vertical = 8.dp)
                    )

                    // GROUP 1: Create & Media
                    DrawerGroupHeader("Create & Media")
                    DrawerToolItem(
                        label = "Media Studio (Merge/Trim/Compress)",
                        icon = Icons.Filled.VideoLibrary,
                        selected = activeOverlay == "NONE" && selectedTab == StudioTabId.MERGER,
                        onClick = { closeDrawerAnd { onOpenOverlay("NONE"); onSelectTab(StudioTabId.MERGER) } }
                    )
                    DrawerToolItem(
                        label = "Content DB (Chats, Scripts & SEO)",
                        icon = Icons.Filled.Storage,
                        badgeText = videoScripts.size.takeIf { it > 0 }?.toString(),
                        selected = activeOverlay == "NONE" && selectedTab == StudioTabId.CONTENT_HUB,
                        onClick = { closeDrawerAnd { onOpenOverlay("NONE"); onSelectTab(StudioTabId.CONTENT_HUB) } }
                    )
                    DrawerToolItem(
                        label = "Offline Gallery & Downloads",
                        icon = Icons.Filled.Download,
                        badgeText = downloads.size.takeIf { it > 0 }?.toString(),
                        selected = activeOverlay == "NONE" && selectedTab == StudioTabId.GALLERY,
                        onClick = { closeDrawerAnd { onOpenOverlay("NONE"); onSelectTab(StudioTabId.GALLERY) } }
                    )

                    HorizontalDivider(
                        color = colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // GROUP 2: Developer & Agents
                    DrawerGroupHeader("Developer & Agents")
                    DrawerToolItem(
                        label = "Hybrid Terminal & Termux Bridge",
                        icon = Icons.Filled.Terminal,
                        badgeText = if (isTerminalRunning) "RUN" else null,
                        selected = activeOverlay == "NONE" && selectedTab == StudioTabId.TERMINAL,
                        onClick = { closeDrawerAnd { onOpenOverlay("NONE"); onSelectTab(StudioTabId.TERMINAL) } }
                    )
                    DrawerToolItem(
                        label = "Local Wi-Fi Web Studio",
                        icon = Icons.Filled.Wifi,
                        badgeText = when {
                            lanWebServerState.isRunning -> "LIVE"
                            onlineLanCount > 0 -> "$onlineLanCount ON"
                            else -> null
                        },
                        selected = lanWebServerState.isRunning,
                        onClick = {
                            closeDrawerAnd {
                                viewModel.refreshDeviceHealth()
                                showLanStudioSheet = true
                            }
                        }
                    )
                    DrawerToolItem(
                        label = "RSS News Agent",
                        icon = Icons.Filled.RssFeed,
                        badgeText = newsDigests.size.takeIf { it > 0 }?.toString()
                            ?: rssFeeds.size.takeIf { it > 0 }?.toString(),
                        selected = activeOverlay == "SETTINGS_HUB",
                        onClick = { closeDrawerAnd { onOpenOverlay("SETTINGS_HUB") } }
                    )
                    DrawerToolItem(
                        label = "Prompt Workflows / Chainer",
                        icon = Icons.Filled.AccountTree,
                        selected = activeOverlay == "CHAIN_RUNNER",
                        onClick = { closeDrawerAnd { onOpenOverlay("CHAIN_RUNNER") } }
                    )

                    HorizontalDivider(
                        color = colorScheme.outline,
                        modifier = Modifier.padding(horizontal = 16.dp, vertical = 8.dp)
                    )

                    // GROUP 3: System & Vault
                    DrawerGroupHeader("System & Vault")
                    DrawerToolItem(
                        label = "Cloud DB & Roles",
                        icon = Icons.Filled.AdminPanelSettings,
                        badgeText = cloudRole.badgeText,
                        selected = activeOverlay == "CLOUD_ACCOUNT",
                        onClick = { closeDrawerAnd { onOpenOverlay("CLOUD_ACCOUNT") } }
                    )
                    DrawerToolItem(
                        label = "API Vault & Multi-Key Pool",
                        icon = Icons.Filled.Security,
                        selected = activeOverlay == "NONE" && selectedTab == StudioTabId.VAULT,
                        onClick = { closeDrawerAnd { onOpenOverlay("NONE"); onSelectTab(StudioTabId.VAULT) } }
                    )
                    DrawerToolItem(
                        label = "4-Layer Backup & Sync",
                        icon = Icons.Filled.CloudSync,
                        selected = activeOverlay == "BACKUP_CENTER",
                        onClick = { closeDrawerAnd { onOpenOverlay("BACKUP_CENTER") } }
                    )
                    DrawerToolItem(
                        label = "Analytics & GPU Quota",
                        icon = Icons.Filled.BarChart,
                        selected = activeOverlay == "ANALYTICS",
                        onClick = { closeDrawerAnd { onOpenOverlay("ANALYTICS") } }
                    )
                    DrawerToolItem(
                        label = "UI Theme & Layout Studio",
                        icon = Icons.Filled.Palette,
                        selected = activeOverlay == "THEME_STUDIO",
                        onClick = { closeDrawerAnd { onOpenOverlay("THEME_STUDIO") } }
                    )
                    DrawerToolItem(
                        label = "Settings & Stealth Icons",
                        icon = Icons.Filled.Settings,
                        selected = activeOverlay == "SETTINGS_HUB",
                        onClick = { closeDrawerAnd { onOpenOverlay("SETTINGS_HUB") } }
                    )
                }
            }
        }
    ) {
        Scaffold(
            containerColor = colorScheme.background,
            snackbarHost = { SnackbarHost(snackbarHostState) },
            topBar = {
                AnimatedVisibility(
                    visible = activeOverlay == "NONE" && !hideChrome,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        SlimStudioTopBar(
                            activeNiche = activeNiche,
                            filterAllNiches = filterAllNiches,
                            hasLiveActivity = hasLiveActivity,
                            liveSummaryLabel = liveSummaryLabel,
                            onOpenDrawer = { scope.launch { drawerState.open() } },
                            onOpenNicheSheet = { showNicheSheet = true },
                            onOpenCommandPalette = onOpenCommandPalette,
                            onOpenLiveJobsSheet = { showLiveJobsSheet = true }
                        )
                        if (cloudRole == CloudUserRole.VIEWER) {
                            RoleGuardBanner(
                                role = cloudRole,
                                modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp),
                                onOpenCloudRoles = { onOpenOverlay("CLOUD_ACCOUNT") }
                            )
                        }
                    }
                }
            },
            bottomBar = {
                AnimatedVisibility(
                    visible = activeOverlay == "NONE" && !hideChrome,
                    enter = fadeIn(),
                    exit = fadeOut()
                ) {
                    Column {
                        AnimatedVisibility(
                            visible = showAdobeReturnBanner,
                            enter = fadeIn(),
                            exit = fadeOut()
                        ) {
                            Surface(
                                color = EmeraldAccent.copy(alpha = 0.16f),
                                border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.65f)),
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(horizontal = 12.dp, vertical = 6.dp),
                                shape = RoundedCornerShape(14.dp)
                            ) {
                                Row(
                                    modifier = Modifier
                                        .fillMaxWidth()
                                        .padding(horizontal = 12.dp, vertical = 10.dp),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(10.dp)
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.Image,
                                        contentDescription = null,
                                        tint = EmeraldAccent,
                                        modifier = Modifier.size(22.dp)
                                    )
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "Back from ${awaitingAdobeSourceApp.ifBlank { "Adobe" }}!",
                                            style = MaterialTheme.typography.labelLarge,
                                            fontWeight = FontWeight.Bold,
                                            color = EmeraldAccent
                                        )
                                        Text(
                                            text = "Pick your exported thumbnail to attach to YouTube",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = colorScheme.onSurface
                                        )
                                    }
                                    Button(
                                        onClick = {
                                            adobeReturnPhotoPicker.launch(
                                                PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                            )
                                        },
                                        contentPadding = PaddingValues(horizontal = 12.dp, vertical = 6.dp),
                                        shape = RoundedCornerShape(10.dp)
                                    ) {
                                        Text("Pick", style = MaterialTheme.typography.labelMedium)
                                    }
                                    IconButton(
                                        onClick = { viewModel.dismissAdobeReturnBanner() },
                                        modifier = Modifier.size(28.dp)
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.Close,
                                            contentDescription = "Dismiss Adobe return banner",
                                            tint = colorScheme.onSurfaceVariant,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                            }
                        }
                        HorizontalDivider(color = colorScheme.outline, thickness = OmniDesignTokens.BorderWidth)
                        NavigationBar(
                            containerColor = colorScheme.surface,
                            tonalElevation = 0.dp
                        ) {
                            pinnedBottomTabs.forEach { tab ->
                                val isSelected = selectedTab == tab
                                NavigationBarItem(
                                    selected = isSelected,
                                    onClick = { onSelectTab(tab) },
                                    icon = {
                                        Icon(
                                            imageVector = iconForStudioTab(tab),
                                            contentDescription = tab.label
                                        )
                                    },
                                    label = {
                                        Text(
                                            text = tab.label,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis,
                                            style = MaterialTheme.typography.labelSmall,
                                            fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal
                                        )
                                    },
                                    colors = NavigationBarItemDefaults.colors(
                                        selectedIconColor = colorScheme.primary,
                                        selectedTextColor = colorScheme.primary,
                                        indicatorColor = colorScheme.primary.omniAccentContainer(),
                                        unselectedIconColor = colorScheme.onSurfaceVariant,
                                        unselectedTextColor = colorScheme.onSurfaceVariant
                                    )
                                )
                            }
                        }
                    }
                }
            },
            floatingActionButton = {
                if (activeOverlay == "NONE" && !hideChrome) {
                    floatingActionButton()
                }
            },
            content = content
        )
    }

    // Niche Switcher ModalBottomSheet
    if (showNicheSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showNicheSheet = false },
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
                    Column {
                        Text(
                            text = "Switch Active Niche",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.SemiBold
                        )
                        Text(
                            text = "Applies voice, style, aspect ratio & rolling memory across Chat and Studio",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                Surface(
                    onClick = {
                        viewModel.setFilterAllNiches(!filterAllNiches)
                        showNicheSheet = false
                    },
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                    color = if (filterAllNiches) colorScheme.primary.omniAccentContainer() else colorScheme.surface,
                    border = BorderStroke(
                        1.dp,
                        if (filterAllNiches) colorScheme.primary.omniAccentBorder() else colorScheme.outline
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget)
                            .padding(horizontal = 14.dp, vertical = 10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(10.dp)
                        ) {
                            Icon(Icons.Filled.Layers, contentDescription = null, tint = colorScheme.primary)
                            Text("All Niches (Unfiltered Content View)", style = MaterialTheme.typography.bodyMedium)
                        }
                        if (filterAllNiches) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldAccent)
                        }
                    }
                }

                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .heightIn(max = 320.dp),
                    verticalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    items(niches, key = { it.id }) { niche ->
                        val isSelected = !filterAllNiches && niche.id == activeNiche?.id
                        Surface(
                            onClick = {
                                viewModel.setFilterAllNiches(false)
                                viewModel.selectNiche(niche.id)
                                showNicheSheet = false
                            },
                            shape = RoundedCornerShape(OmniDesignTokens.ChipRadius),
                            color = if (isSelected) colorScheme.primary.omniAccentContainer() else colorScheme.surface,
                            border = BorderStroke(
                                1.dp,
                                if (isSelected) colorScheme.primary.omniAccentBorder() else colorScheme.outline
                            ),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget)
                                    .padding(horizontal = 14.dp, vertical = 10.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "${niche.emoji} ${niche.name}",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = if (isSelected) FontWeight.SemiBold else FontWeight.Normal,
                                        color = if (isSelected) colorScheme.primary else colorScheme.onSurface
                                    )
                                    Text(
                                        text = "${niche.visualStylePreset} · ${niche.defaultAspectRatio} · Ep ${niche.episodeCount}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                }
                                IconButton(
                                    onClick = {
                                        showNicheSheet = false
                                        editingNiche = niche
                                        showNicheEditorDialog = true
                                    }
                                ) {
                                    Icon(
                                        Icons.Filled.Edit,
                                        contentDescription = "Edit ${niche.name}",
                                        tint = colorScheme.onSurfaceVariant,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                            }
                        }
                    }
                }

                Button(
                    onClick = {
                        showNicheSheet = false
                        editingNiche = null
                        showNicheEditorDialog = true
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                ) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Create Custom Niche")
                }
            }
        }
    }

    // Live Jobs & Activity ModalBottomSheet
    if (showLiveJobsSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLiveJobsSheet = false },
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
                Text(
                    text = "Live Studio Activity",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )

                if (!hasLiveActivity) {
                    OmniEmptyState(
                        icon = Icons.Filled.Layers,
                        title = "All pipelines idle",
                        helperText = "Kaggle GPU renders, GitHub Actions builds, and local LAN Studio sessions appear here live.",
                        actionLabel = "Open Video Studio",
                        onActionClick = {
                            showLiveJobsSheet = false
                            onSelectTab(StudioTabId.DIRECTOR)
                        }
                    )
                } else {
                    activeRenderJobs.forEach { job ->
                        OmniSurfaceCard(
                            onClick = {
                                showLiveJobsSheet = false
                                onSelectTab(StudioTabId.DIRECTOR)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    text = job.title,
                                    style = MaterialTheme.typography.titleSmall,
                                    maxLines = 1,
                                    overflow = TextOverflow.Ellipsis,
                                    modifier = Modifier.weight(1f)
                                )
                                OmniAccentBadge(text = job.status, accent = EmeraldAccent)
                            }
                            Text(
                                text = job.statusMessage,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            LinearProgressIndicator(
                                progress = { (job.progressPercent / 100f).coerceIn(0f, 1f) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (isActionsActive) {
                        OmniSurfaceCard(
                            onClick = {
                                showLiveJobsSheet = false
                                onSelectTab(StudioTabId.PROJECTS)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("GitHub Actions Build", style = MaterialTheme.typography.titleSmall)
                                OmniAccentBadge(
                                    text = actionsState.status,
                                    accent = when (actionsState.status) {
                                        "SUCCESS" -> EmeraldAccent
                                        "FAILURE" -> CrimsonDanger
                                        else -> AmberWarning
                                    }
                                )
                            }
                            Text(
                                text = "${actionsState.workflowName.ifBlank { "Release APK Pipeline" }} · ${actionsState.elapsedSec}s",
                                fontFamily = FontFamily.Monospace,
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (isTerminalRunning || isNetlogEnabled) {
                        OmniSurfaceCard(
                            onClick = {
                                showLiveJobsSheet = false
                                onSelectTab(StudioTabId.TERMINAL)
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Hybrid Terminal & Netlog", style = MaterialTheme.typography.titleSmall)
                                OmniAccentBadge(
                                    text = if (isTerminalRunning) "RUNNING" else "NETLOG ON",
                                    accent = colorScheme.primary
                                )
                            }
                            Text(
                                text = "Tap to inspect live stdout or stop active process.",
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    if (onlineLanCount > 0 || lanWebServerState.isRunning) {
                        OmniSurfaceCard(
                            onClick = {
                                showLiveJobsSheet = false
                                showLanStudioSheet = true
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text("Local Wi-Fi Web Studio", style = MaterialTheme.typography.titleSmall)
                                OmniAccentBadge(
                                    text = if (lanWebServerState.isRunning) "PIN ${lanWebServerState.accessPin}" else "$onlineLanCount ONLINE",
                                    accent = EmeraldAccent
                                )
                            }
                            if (lanWebServerState.isRunning) {
                                Text(
                                    text = "${lanWebServerState.url} · ${lanWebServerState.servedRequests} request(s)",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }

                    // v5.0 Device Health Snapshot Card
                    OmniSurfaceCard {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Device Thermal & Storage Guard", style = MaterialTheme.typography.titleSmall)
                            OmniAccentBadge(
                                text = deviceHealth.thermalLevel.label,
                                accent = if (deviceHealth.isSafeForHeavyMediaJob) EmeraldAccent else AmberWarning
                            )
                        }
                        Text(
                            text = "Battery ${deviceHealth.batteryPercent}% · Free ${deviceHealth.freeStorageMb} MB · RAM ${deviceHealth.availableRamMb} MB",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                }

                OutlinedButton(
                    onClick = { showLiveJobsSheet = false },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                ) {
                    Text("Close")
                }
            }
        }
    }

    // v5.0 Local Wi-Fi LAN Web Studio Control ModalBottomSheet
    if (showLanStudioSheet) {
        val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
        ModalBottomSheet(
            onDismissRequest = { showLanStudioSheet = false },
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
                    Column(modifier = Modifier.weight(1f)) {
                        Text(
                            text = "Local Wi-Fi Web Studio",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold
                        )
                        Text(
                            text = "Zero-dependency ServerSocket LAN bridge with 6-digit pairing PIN",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    OmniAccentBadge(
                        text = if (lanWebServerState.isRunning) "LIVE" else "OFFLINE",
                        accent = if (lanWebServerState.isRunning) EmeraldAccent else colorScheme.onSurfaceVariant
                    )
                }

                OmniSurfaceCard {
                    if (lanWebServerState.isRunning) {
                        Text(
                            text = "Open in any desktop browser on the same Wi-Fi:",
                            style = MaterialTheme.typography.labelSmall,
                            color = colorScheme.onSurfaceVariant
                        )
                        Text(
                            text = lanWebServerState.url,
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.titleSmall,
                            fontWeight = FontWeight.Bold,
                            color = EmeraldAccent
                        )
                        Text(
                            text = "6-Digit Pairing PIN: ${lanWebServerState.accessPin} · Requests: ${lanWebServerState.servedRequests}",
                            fontFamily = FontFamily.Monospace,
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurface
                        )
                    } else {
                        Text(
                            text = "Start the built-in LAN server to browse chats, video scripts, and projects from your PC over local Wi-Fi without cloud uploads.",
                            style = MaterialTheme.typography.bodySmall,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    lanWebServerState.lastError?.let { err ->
                        Text(
                            text = err,
                            style = MaterialTheme.typography.labelSmall,
                            color = CrimsonDanger
                        )
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Button(
                        onClick = {
                            if (lanWebServerState.isRunning) {
                                viewModel.stopLanWebServer()
                            } else {
                                viewModel.startLanWebServer()
                            }
                        },
                        modifier = Modifier
                            .weight(1f)
                            .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                    ) {
                        Text(if (lanWebServerState.isRunning) "Stop LAN Server" else "Start LAN Server")
                    }
                    OutlinedButton(
                        onClick = {
                            showLanStudioSheet = false
                            viewModel.scanLocalhostPorts()
                            onOpenOverlay("NONE")
                            onSelectTab(StudioTabId.VAULT)
                        },
                        modifier = Modifier.defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                    ) {
                        Text("Port Scan")
                    }
                }
            }
        }
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
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun SlimStudioTopBar(
    activeNiche: NicheEntity?,
    filterAllNiches: Boolean,
    hasLiveActivity: Boolean,
    liveSummaryLabel: String,
    onOpenDrawer: () -> Unit,
    onOpenNicheSheet: () -> Unit,
    onOpenCommandPalette: () -> Unit,
    onOpenLiveJobsSheet: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val pulseTransition = rememberInfiniteTransition(label = "live_status_pulse")
    val pulseAlpha by pulseTransition.animateFloat(
        initialValue = 0.35f,
        targetValue = 1.0f,
        animationSpec = infiniteRepeatable(
            animation = tween(durationMillis = 850),
            repeatMode = RepeatMode.Reverse
        ),
        label = "pulse_dot_alpha"
    )

    Column {
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
                // Left: Hamburger Menu + Compact Active Niche Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(6.dp),
                    modifier = Modifier.weight(1f)
                ) {
                    IconButton(
                        onClick = onOpenDrawer,
                        modifier = Modifier.defaultMinSize(
                            minWidth = OmniDesignTokens.MinTouchTarget,
                            minHeight = OmniDesignTokens.MinTouchTarget
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Menu,
                            contentDescription = "Open Navigation Drawer",
                            tint = colorScheme.onSurface
                        )
                    }

                    Surface(
                        onClick = onOpenNicheSheet,
                        shape = RoundedCornerShape(20.dp),
                        color = colorScheme.primary.omniAccentContainer(),
                        border = BorderStroke(1.dp, colorScheme.primary.omniAccentBorder())
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(5.dp)
                        ) {
                            val pillLabel = when {
                                filterAllNiches -> "All Niches"
                                activeNiche != null -> "${activeNiche.emoji} ${activeNiche.name}"
                                else -> "Select Niche"
                            }
                            Text(
                                text = pillLabel,
                                style = MaterialTheme.typography.labelMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = colorScheme.primary,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Icon(
                                imageVector = Icons.Filled.ExpandMore,
                                contentDescription = "Switch Niche",
                                tint = colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                        }
                    }
                }

                // Right: ONLY 2 items — Command Palette Search + Live Status Pill
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    if (hasLiveActivity) {
                        Surface(
                            onClick = onOpenLiveJobsSheet,
                            shape = RoundedCornerShape(20.dp),
                            color = EmeraldAccent.omniAccentContainer(),
                            border = BorderStroke(1.dp, EmeraldAccent.omniAccentBorder())
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(8.dp)
                                        .alpha(pulseAlpha)
                                        .clip(CircleShape)
                                        .background(EmeraldAccent)
                                )
                                Text(
                                    text = liveSummaryLabel,
                                    style = MaterialTheme.typography.labelSmall,
                                    fontWeight = FontWeight.SemiBold,
                                    color = EmeraldAccent,
                                    maxLines = 1
                                )
                            }
                        }
                    }

                    IconButton(
                        onClick = onOpenCommandPalette,
                        modifier = Modifier.defaultMinSize(
                            minWidth = OmniDesignTokens.MinTouchTarget,
                            minHeight = OmniDesignTokens.MinTouchTarget
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Search,
                            contentDescription = "Open Universal Command Palette",
                            tint = colorScheme.onSurface
                        )
                    }
                }
            }
        }
        HorizontalDivider(color = colorScheme.outline, thickness = OmniDesignTokens.BorderWidth)
    }
}

@Composable
private fun DrawerGroupHeader(title: String) {
    Text(
        text = title.uppercase(),
        style = MaterialTheme.typography.labelSmall,
        fontWeight = FontWeight.Bold,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
        modifier = Modifier.padding(horizontal = 20.dp, vertical = 6.dp)
    )
}

@Composable
private fun DrawerToolItem(
    label: String,
    icon: ImageVector,
    selected: Boolean,
    badgeText: String? = null,
    onClick: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    NavigationDrawerItem(
        label = {
            Text(
                text = label,
                style = MaterialTheme.typography.bodyMedium,
                maxLines = 1,
                overflow = TextOverflow.Ellipsis
            )
        },
        selected = selected,
        onClick = onClick,
        icon = {
            Icon(
                imageVector = icon,
                contentDescription = null,
                modifier = Modifier.size(20.dp)
            )
        },
        badge = badgeText?.let { text ->
            {
                OmniAccentBadge(text = text, accent = colorScheme.primary)
            }
        },
        colors = NavigationDrawerItemDefaults.colors(
            selectedContainerColor = colorScheme.primary.omniAccentContainer(),
            selectedIconColor = colorScheme.primary,
            selectedTextColor = colorScheme.primary,
            unselectedContainerColor = Color.Transparent,
            unselectedIconColor = colorScheme.onSurfaceVariant,
            unselectedTextColor = colorScheme.onSurface
        ),
        modifier = Modifier.padding(horizontal = 12.dp, vertical = 2.dp)
    )
}
