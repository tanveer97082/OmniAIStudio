package com.omniai.studio

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.DocumentScanner
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SnackbarHostState
import androidx.compose.material3.Text
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import androidx.fragment.app.FragmentActivity
import com.omniai.studio.security.PrivacyShieldHelper
import com.omniai.studio.service.QuickVoiceTileService
import com.omniai.studio.settings.QuickFabAction
import com.omniai.studio.settings.StudioTabId
import com.omniai.studio.ui.components.AppNavigationShell
import com.omniai.studio.ui.screens.AnalyticsScreen
import com.omniai.studio.ui.screens.AppLockOverlay
import com.omniai.studio.ui.screens.BackupSyncCenterScreen
import com.omniai.studio.ui.screens.ChainRunnerScreen
import com.omniai.studio.ui.screens.ChatStudioScreen
import com.omniai.studio.ui.screens.CommandPaletteDialog
import com.omniai.studio.ui.screens.ContentHubScreen
import com.omniai.studio.ui.screens.DecoySandboxScreen
import com.omniai.studio.ui.screens.DownloadsGalleryScreen
import com.omniai.studio.ui.screens.HomeBentoScreen
import com.omniai.studio.ui.screens.ProjectDashboardScreen
import com.omniai.studio.ui.screens.SettingsHubScreen
import com.omniai.studio.ui.screens.TerminalScreen
import com.omniai.studio.ui.screens.UiThemeStudioScreen
import com.omniai.studio.ui.screens.VaultSettingsScreen
import com.omniai.studio.ui.screens.VideoDirectorScreen
import com.omniai.studio.ui.screens.VideoMergerScreen
import com.omniai.studio.ui.theme.OmniAiAmoledTheme
import com.omniai.studio.viewmodel.OmniAiViewModel

private fun legacyIndexToTab(index: Int): StudioTabId = when (index) {
    0 -> StudioTabId.CHAT
    1 -> StudioTabId.DIRECTOR
    2 -> StudioTabId.MERGER
    3 -> StudioTabId.GALLERY
    4 -> StudioTabId.VAULT
    5 -> StudioTabId.TERMINAL
    6 -> StudioTabId.CONTENT_HUB
    7 -> StudioTabId.PROJECTS
    8 -> StudioTabId.HOME
    else -> StudioTabId.HOME
}

class MainActivity : FragmentActivity() {

    companion object {
        const val EXTRA_LAUNCH_TAB = "LAUNCH_TAB"
        const val EXTRA_LAUNCH_VOICE_CHAT = "LAUNCH_VOICE_CHAT"
        const val EXTRA_LAUNCH_OCR = "LAUNCH_OCR"
        const val EXTRA_LAUNCH_BACKUP_CENTER = "LAUNCH_BACKUP_CENTER"
        const val EXTRA_OPEN_SOCIAL_POST_ID = "OPEN_SOCIAL_POST_ID"
        const val EXTRA_OPEN_SOCIAL_PLATFORM = "OPEN_SOCIAL_PLATFORM"
    }

    private val viewModel: OmniAiViewModel by viewModels()
    private var lastBackgroundTimestampMs: Long = 0L

    private val notificationPermissionLauncher =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { }

    @OptIn(ExperimentalMaterial3Api::class)
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        val app = application as OmniAiApplication
        PrivacyShieldHelper.applyFlagSecure(this, app.vaultManager.flagSecureEnabled)
        handleIncomingShareIntent(intent)

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
                notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
            }
        }

        val initialTabName = app.uiConfigRepository.config.value.defaultStartupTab.name

        setContent {
            val appSettings by viewModel.appSettings.collectAsState()
            val uiConfig by viewModel.uiConfig.collectAsState()

            OmniAiAmoledTheme(
                accent = appSettings.themeAccent,
                uiConfig = uiConfig
            ) {
                val colorScheme = MaterialTheme.colorScheme
                val haptics = LocalHapticFeedback.current

                var selectedTabName by rememberSaveable { mutableStateOf(initialTabName) }
                val selectedTab = runCatching { StudioTabId.valueOf(selectedTabName) }
                    .getOrDefault(StudioTabId.HOME)

                // "NONE" | "SETTINGS_HUB" | "THEME_STUDIO" | "BACKUP_CENTER" | "ANALYTICS" | "CHAIN_RUNNER"
                var activeOverlay by rememberSaveable { mutableStateOf("NONE") }
                var showCommandPalette by remember { mutableStateOf(false) }
                var showCreateWorkspaceDialog by remember { mutableStateOf(false) }
                var newWorkspaceName by remember { mutableStateOf("") }
                var newWorkspaceDesc by remember { mutableStateOf("") }

                // Distraction-free chrome hiding states (Chat Zen Focus Mode & Full-Screen Code Editor)
                var isZenFocusMode by rememberSaveable { mutableStateOf(false) }
                var isFullscreenCodeEditor by remember { mutableStateOf(false) }

                val isAppLocked by viewModel.isAppLocked.collectAsState()
                val isDecoyMode by viewModel.isDecoyMode.collectAsState()
                val hasMasterPin by viewModel.hasMasterPin.collectAsState()
                val hasDecoyPin by viewModel.hasDecoyPin.collectAsState()
                val flagSecure by viewModel.flagSecureEnabled.collectAsState()
                val toastMessage by viewModel.toastMessage.collectAsState()
                val pendingSharedText by viewModel.pendingSharedText.collectAsState()
                val pendingVoiceAutoLaunch by viewModel.pendingVoiceAutoLaunch.collectAsState()
                val pendingOcrAutoLaunch by viewModel.pendingOcrAutoLaunch.collectAsState()
                val pendingOpenBackupCenter by viewModel.pendingOpenBackupCenter.collectAsState()
                val pendingLaunchTab by viewModel.pendingLaunchTab.collectAsState()
                val pendingOpenSocialPostId by viewModel.pendingOpenSocialPostId.collectAsState()
                val snackbarHostState = remember { SnackbarHostState() }

                val pinnedBottomTabs = uiConfig.bottomNavTabs
                val unpinnedTabs = StudioTabId.entries.filterNot { it in pinnedBottomTabs }
                val hideChrome = (selectedTab == StudioTabId.CHAT && isZenFocusMode) ||
                    (selectedTab == StudioTabId.PROJECTS && isFullscreenCodeEditor)

                fun tapHaptic() {
                    if (uiConfig.hapticFeedbackEnabled) {
                        haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
                    }
                }

                fun navigateToTab(target: StudioTabId) {
                    tapHaptic()
                    activeOverlay = "NONE"
                    if (target != StudioTabId.CHAT) isZenFocusMode = false
                    if (target != StudioTabId.PROJECTS) isFullscreenCodeEditor = false
                    selectedTabName = target.name
                }

                LaunchedEffect(flagSecure) {
                    PrivacyShieldHelper.applyFlagSecure(this@MainActivity, flagSecure)
                }

                LaunchedEffect(pendingVoiceAutoLaunch) {
                    if (pendingVoiceAutoLaunch) {
                        navigateToTab(StudioTabId.CHAT)
                    }
                }

                LaunchedEffect(pendingOcrAutoLaunch) {
                    if (pendingOcrAutoLaunch) {
                        navigateToTab(StudioTabId.CHAT)
                    }
                }

                LaunchedEffect(pendingOpenBackupCenter) {
                    if (pendingOpenBackupCenter) {
                        activeOverlay = "BACKUP_CENTER"
                        viewModel.consumeOpenBackupCenter()
                    }
                }

                LaunchedEffect(pendingLaunchTab) {
                    pendingLaunchTab?.let { targetIndex ->
                        navigateToTab(legacyIndexToTab(targetIndex))
                        viewModel.consumeLaunchTab()
                    }
                }

                LaunchedEffect(pendingOpenSocialPostId) {
                    if (pendingOpenSocialPostId != null) {
                        navigateToTab(StudioTabId.CONTENT_HUB)
                    }
                }

                LaunchedEffect(toastMessage) {
                    toastMessage?.let { msg ->
                        snackbarHostState.showSnackbar(msg)
                        viewModel.consumeToast()
                    }
                }

                Box(modifier = Modifier.fillMaxSize()) {
                    // Hub-and-Spoke Navigation Shell (4-Tab Bar + Slide-Out Drawer + Slim 56dp TopAppBar)
                    // Open Universal Command Palette via TopAppBar Search action
                    AppNavigationShell(
                        viewModel = viewModel,
                        selectedTab = selectedTab,
                        pinnedBottomTabs = pinnedBottomTabs,
                        activeOverlay = activeOverlay,
                        hideChrome = hideChrome,
                        appTitle = appSettings.iconVariant.launcherLabel,
                        snackbarHostState = snackbarHostState,
                        onSelectTab = { target -> navigateToTab(target) },
                        onOpenOverlay = { overlay ->
                            tapHaptic()
                            if (overlay != "NONE") {
                                isZenFocusMode = false
                                isFullscreenCodeEditor = false
                            }
                            activeOverlay = overlay
                        },
                        onOpenCommandPalette = {
                            tapHaptic()
                            showCommandPalette = true
                        },
                        onCreateWorkspaceClick = {
                            tapHaptic()
                            showCreateWorkspaceDialog = true
                        },
                        floatingActionButton = {
                            if (activeOverlay == "NONE" && uiConfig.quickFabAction != QuickFabAction.DISABLED) {
                                val fabIcon = when (uiConfig.quickFabAction) {
                                    QuickFabAction.VOICE_CHAT -> Icons.Filled.Mic
                                    QuickFabAction.QUICK_TERMINAL -> Icons.Filled.Terminal
                                    QuickFabAction.OCR_SCAN -> Icons.Filled.DocumentScanner
                                    QuickFabAction.NEW_VIDEO -> Icons.Filled.MovieCreation
                                    QuickFabAction.GIT_AUTO_PUSH -> Icons.Filled.CloudUpload
                                    QuickFabAction.DISABLED -> Icons.Filled.Add
                                }
                                FloatingActionButton(
                                    onClick = {
                                        tapHaptic()
                                        when (uiConfig.quickFabAction) {
                                            QuickFabAction.VOICE_CHAT -> {
                                                navigateToTab(StudioTabId.CHAT)
                                                viewModel.triggerVoiceAutoLaunch()
                                            }
                                            QuickFabAction.QUICK_TERMINAL -> {
                                                navigateToTab(StudioTabId.TERMINAL)
                                            }
                                            QuickFabAction.OCR_SCAN -> {
                                                navigateToTab(StudioTabId.CHAT)
                                                viewModel.triggerOcrAutoLaunch()
                                            }
                                            QuickFabAction.NEW_VIDEO -> {
                                                navigateToTab(StudioTabId.DIRECTOR)
                                            }
                                            QuickFabAction.GIT_AUTO_PUSH -> {
                                                navigateToTab(StudioTabId.PROJECTS)
                                                viewModel.quickGitAutoPushActiveProject()
                                            }
                                            QuickFabAction.DISABLED -> Unit
                                        }
                                    },
                                    containerColor = colorScheme.primary,
                                    contentColor = colorScheme.onPrimary
                                ) {
                                    Icon(fabIcon, contentDescription = uiConfig.quickFabAction.label)
                                }
                            }
                        }
                    ) { innerPadding ->
                        Box(
                            modifier = Modifier
                                .fillMaxSize()
                                .padding(innerPadding)
                        ) {
                            when (activeOverlay) {
                                "SETTINGS_HUB" -> SettingsHubScreen(
                                    activity = this@MainActivity,
                                    viewModel = viewModel,
                                    onBack = { activeOverlay = "NONE" },
                                    onOpenDirector = { navigateToTab(StudioTabId.DIRECTOR) },
                                    onOpenBackupCenter = { activeOverlay = "BACKUP_CENTER" },
                                    onOpenAnalytics = { activeOverlay = "ANALYTICS" },
                                    onOpenThemeStudio = { activeOverlay = "THEME_STUDIO" },
                                    onOpenTerminal = { navigateToTab(StudioTabId.TERMINAL) }
                                )
                                "THEME_STUDIO" -> UiThemeStudioScreen(
                                    viewModel = viewModel,
                                    onBack = { activeOverlay = "SETTINGS_HUB" }
                                )
                                "BACKUP_CENTER" -> BackupSyncCenterScreen(
                                    viewModel = viewModel,
                                    onBack = { activeOverlay = "NONE" }
                                )
                                "ANALYTICS" -> AnalyticsScreen(
                                    viewModel = viewModel,
                                    onBack = { activeOverlay = "NONE" }
                                )
                                "CHAIN_RUNNER" -> ChainRunnerScreen(
                                    viewModel = viewModel,
                                    onBack = { activeOverlay = "NONE" },
                                    onOpenDirector = { navigateToTab(StudioTabId.DIRECTOR) }
                                )
                                else -> {
                                    when (selectedTab) {
                                        StudioTabId.HOME -> HomeBentoScreen(
                                            viewModel = viewModel,
                                            onOpenChat = { navigateToTab(StudioTabId.CHAT) },
                                            onOpenQuickVoice = {
                                                navigateToTab(StudioTabId.CHAT)
                                                viewModel.triggerVoiceAutoLaunch()
                                            },
                                            onOpenStudio = { navigateToTab(StudioTabId.DIRECTOR) },
                                            onOpenProjects = { navigateToTab(StudioTabId.PROJECTS) },
                                            onOpenTerminal = { navigateToTab(StudioTabId.TERMINAL) },
                                            onOpenAnalytics = { activeOverlay = "ANALYTICS" },
                                            onOpenGallery = { navigateToTab(StudioTabId.GALLERY) }
                                        )
                                        StudioTabId.CHAT -> ChatStudioScreen(
                                            viewModel = viewModel,
                                            isZenFocusMode = isZenFocusMode,
                                            onToggleZenFocusMode = {
                                                tapHaptic()
                                                isZenFocusMode = !isZenFocusMode
                                            },
                                            onOpenWorkflows = { activeOverlay = "CHAIN_RUNNER" }
                                        )
                                        StudioTabId.DIRECTOR -> VideoDirectorScreen(
                                            viewModel = viewModel,
                                            onOpenContentHub = { navigateToTab(StudioTabId.CONTENT_HUB) }
                                        )
                                        StudioTabId.MERGER -> VideoMergerScreen(viewModel = viewModel)
                                        StudioTabId.TERMINAL -> TerminalScreen(viewModel = viewModel)
                                        StudioTabId.GALLERY -> DownloadsGalleryScreen(
                                            viewModel = viewModel,
                                            onOpenContentHub = { navigateToTab(StudioTabId.CONTENT_HUB) },
                                            onOpenStudio = { navigateToTab(StudioTabId.DIRECTOR) }
                                        )
                                        StudioTabId.CONTENT_HUB -> ContentHubScreen(
                                            viewModel = viewModel,
                                            onResumeChat = { navigateToTab(StudioTabId.CHAT) },
                                            onOpenDirector = { navigateToTab(StudioTabId.DIRECTOR) }
                                        )
                                        StudioTabId.PROJECTS -> ProjectDashboardScreen(
                                            viewModel = viewModel,
                                            onOpenChat = { navigateToTab(StudioTabId.CHAT) },
                                            onOpenDirector = { navigateToTab(StudioTabId.DIRECTOR) },
                                            onFullscreenEditorChanged = { fullscreen ->
                                                isFullscreenCodeEditor = fullscreen
                                            }
                                        )
                                        StudioTabId.VAULT -> VaultSettingsScreen(
                                            activity = this@MainActivity,
                                            biometricManager = app.biometricAuthManager,
                                            viewModel = viewModel,
                                            onOpenSettingsHub = { activeOverlay = "SETTINGS_HUB" },
                                            onOpenBackupCenter = { activeOverlay = "BACKUP_CENTER" }
                                        )
                                    }
                                }
                            }
                        }
                    }

                    // Keep unpinnedTabs count accessible for Settings & Command Palette parity
                    if (unpinnedTabs.isEmpty() && false) {
                        Text("Open Universal Command Palette")
                    }

                    // Universal Command Palette (Spotlight / Ctrl+K)
                    if (showCommandPalette) {
                        CommandPaletteDialog(
                            viewModel = viewModel,
                            onDismiss = { showCommandPalette = false },
                            onNavigateTab = { target -> navigateToTab(target) },
                            onOpenOverlay = { overlay -> activeOverlay = overlay }
                        )
                    }

                    // Create Custom Workspace Dialog
                    if (showCreateWorkspaceDialog) {
                        AlertDialog(
                            onDismissRequest = { showCreateWorkspaceDialog = false },
                            containerColor = colorScheme.surface,
                            title = { Text("New Workspace Profile") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        "Chats, long-term Memory Facts, and Storyboards are isolated per workspace.",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    OutlinedTextField(
                                        value = newWorkspaceName,
                                        onValueChange = { newWorkspaceName = it },
                                        label = { Text("Workspace Name (e.g. Client Agency)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                    OutlinedTextField(
                                        value = newWorkspaceDesc,
                                        onValueChange = { newWorkspaceDesc = it },
                                        label = { Text("Description (Optional)") },
                                        singleLine = true,
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            },
                            confirmButton = {
                                Button(
                                    onClick = {
                                        viewModel.createWorkspace(newWorkspaceName, newWorkspaceDesc)
                                        newWorkspaceName = ""
                                        newWorkspaceDesc = ""
                                        showCreateWorkspaceDialog = false
                                    },
                                    enabled = newWorkspaceName.isNotBlank()
                                ) { Text("Create") }
                            },
                            dismissButton = {
                                OutlinedButton(onClick = { showCreateWorkspaceDialog = false }) { Text("Cancel") }
                            }
                        )
                    }

                    // Android Share-to-AI Receiver Popup (ACTION_SEND text/plain)
                    pendingSharedText?.let { shared ->
                        AlertDialog(
                            onDismissRequest = { viewModel.dismissSharedText() },
                            containerColor = colorScheme.surface,
                            title = { Text("Share to OmniAI Studio") },
                            text = {
                                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Text(
                                        text = "Choose how to process this shared text:",
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    Text(
                                        text = shared.take(260) + if (shared.length > 260) "…" else "",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurface
                                    )
                                    Button(
                                        onClick = {
                                            viewModel.routeSharedTextToChat()
                                            navigateToTab(StudioTabId.CHAT)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Send to AI Chat")
                                    }
                                    OutlinedButton(
                                        onClick = {
                                            viewModel.routeSharedTextToDirector()
                                            navigateToTab(StudioTabId.DIRECTOR)
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Create Video Storyboard")
                                    }
                                }
                            },
                            confirmButton = {},
                            dismissButton = {
                                OutlinedButton(onClick = { viewModel.dismissSharedText() }) {
                                    Text("Cancel")
                                }
                            }
                        )
                    }

                    if (isDecoyMode) {
                        DecoySandboxScreen(
                            onLockAgain = { viewModel.exitDecoyToLock() }
                        )
                    } else if (isAppLocked) {
                        AppLockOverlay(
                            hasPinConfigured = hasMasterPin || hasDecoyPin,
                            onUnlockClick = {
                                app.biometricAuthManager.authenticate(
                                    activity = this@MainActivity,
                                    title = "Unlock OmniAI-Studio",
                                    subtitle = "Verify identity with Biometric or Device PIN",
                                    onSuccess = { viewModel.unlockApp() },
                                    onFailure = { err -> viewModel.postToast(err) }
                                )
                            },
                            onVerifyPin = { pin -> viewModel.verifyUnlockPin(pin) }
                        )
                    }
                }
            }
        }
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        setIntent(intent)
        handleIncomingShareIntent(intent)
    }

    private fun handleIncomingShareIntent(incoming: Intent?) {
        if (incoming == null) return
        if (incoming.getBooleanExtra(QuickVoiceTileService.EXTRA_LAUNCH_VOICE_CHAT, false) ||
            incoming.getBooleanExtra(EXTRA_LAUNCH_VOICE_CHAT, false)
        ) {
            incoming.removeExtra(QuickVoiceTileService.EXTRA_LAUNCH_VOICE_CHAT)
            incoming.removeExtra(EXTRA_LAUNCH_VOICE_CHAT)
            viewModel.triggerVoiceAutoLaunch()
        }
        if (incoming.getBooleanExtra(EXTRA_LAUNCH_OCR, false)) {
            incoming.removeExtra(EXTRA_LAUNCH_OCR)
            viewModel.triggerOcrAutoLaunch()
        }
        if (incoming.getBooleanExtra(EXTRA_LAUNCH_BACKUP_CENTER, false)) {
            incoming.removeExtra(EXTRA_LAUNCH_BACKUP_CENTER)
            viewModel.triggerOpenBackupCenter()
        }
        val openSocialPostId = incoming.getLongExtra(EXTRA_OPEN_SOCIAL_POST_ID, -1L)
        if (openSocialPostId > 0L) {
            val openPlatform = incoming.getStringExtra(EXTRA_OPEN_SOCIAL_PLATFORM)
            incoming.removeExtra(EXTRA_OPEN_SOCIAL_POST_ID)
            incoming.removeExtra(EXTRA_OPEN_SOCIAL_PLATFORM)
            viewModel.requestOpenSocialPost(openSocialPostId, openPlatform)
            viewModel.requestLaunchTab(6)
        }
        val requestedTab = incoming.getIntExtra(EXTRA_LAUNCH_TAB, -1)
        if (requestedTab in 0..8) {
            incoming.removeExtra(EXTRA_LAUNCH_TAB)
            viewModel.requestLaunchTab(requestedTab)
        }
        if (incoming.action == Intent.ACTION_SEND && incoming.type?.startsWith("text/") == true) {
            val sharedText = incoming.getStringExtra(Intent.EXTRA_TEXT)
            viewModel.onIncomingSharedText(sharedText)
        }
    }

    override fun onStop() {
        super.onStop()
        lastBackgroundTimestampMs = System.currentTimeMillis()
    }

    override fun onStart() {
        super.onStart()
        val app = application as OmniAiApplication
        if (app.vaultManager.appLockEnabled || app.duressPinManager.hasMasterPin()) {
            val shouldLock = app.biometricAuthManager.shouldLockAfterBackground(
                lastBackgroundTimestampMs = lastBackgroundTimestampMs,
                timeout = app.vaultManager.autoLockTimeout
            )
            if (shouldLock) {
                viewModel.lockApp()
            }
        }
    }
}
