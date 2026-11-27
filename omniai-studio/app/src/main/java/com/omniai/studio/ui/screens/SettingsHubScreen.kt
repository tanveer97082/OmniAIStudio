package com.omniai.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AdminPanelSettings
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.BarChart
import androidx.compose.material.icons.filled.Bolt
import androidx.compose.material.icons.filled.BugReport
import androidx.compose.material.icons.filled.Calculate
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.EditNote
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RssFeed
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.StarOutline
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Warning
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
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
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.fragment.app.FragmentActivity
import com.omniai.studio.BuildConfig
import com.omniai.studio.data.local.AppSettingsRepository
import com.omniai.studio.data.local.AutoTypeSpeedMode
import com.omniai.studio.cloud.CloudUserRole
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.ErrorOutline
import com.omniai.studio.media.AdobeAppPickerDialog
import com.omniai.studio.media.AdobeTargetApp
import com.omniai.studio.media.StorageGalleryHelper
import com.omniai.studio.security.AppIconVariant
import com.omniai.studio.security.AutoLockTimeout
import com.omniai.studio.ui.guides.PlatformGuideBottomSheet
import com.omniai.studio.ui.guides.PlatformGuideId
import com.omniai.studio.ui.guides.PlatformGuideTriggerButton
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.ThemeAccent
import com.omniai.studio.viewmodel.OmniAiViewModel
import kotlin.math.roundToInt

@Composable
fun SettingsHubScreen(
    activity: FragmentActivity,
    viewModel: OmniAiViewModel,
    onBack: () -> Unit,
    onOpenDirector: () -> Unit = {},
    onOpenBackupCenter: () -> Unit = {},
    onOpenCloudAccount: () -> Unit = {},
    onOpenAnalytics: () -> Unit = {},
    onOpenThemeStudio: () -> Unit = {},
    onRunGuideCommandInTerminal: (String) -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val settings by viewModel.appSettings.collectAsState()
    val uiConfig by viewModel.uiConfig.collectAsState()
    val isNetlogEnabled by viewModel.isNetlogEnabled.collectAsState()
    val vaultSnapshot by viewModel.vaultSnapshot.collectAsState()
    val cloudRole by viewModel.cloudRole.collectAsState()
    val cloudSyncStatus by viewModel.cloudSyncStatus.collectAsState()
    val storage by viewModel.storageUsage.collectAsState()
    val otaState by viewModel.otaState.collectAsState()
    val crashLog by viewModel.crashLogText.collectAsState()
    val weeklyUsedGpuHours by viewModel.weeklyUsedGpuHours.collectAsState()
    val rssFeeds by viewModel.rssFeeds.collectAsState()
    val newsDigests by viewModel.newsDigests.collectAsState()
    val hasMasterPin by viewModel.hasMasterPin.collectAsState()
    val hasDecoyPin by viewModel.hasDecoyPin.collectAsState()
    val failedPinAuditLog by viewModel.failedPinAuditLog.collectAsState()
    val autoWipeThreshold by viewModel.autoWipeThreshold.collectAsState()
    val tunnelWatchdogEnabled by viewModel.tunnelWatchdogEnabled.collectAsState()
    val adobeExpressStatus by viewModel.adobeExpressStatus.collectAsState()
    val adobeFireflyStatus by viewModel.adobeFireflyStatus.collectAsState()

    var searchQuery by remember { mutableStateOf("") }
    var pendingIconVariant by remember { mutableStateOf<AppIconVariant?>(null) }
    var showCrashLogDialog by remember { mutableStateOf(false) }
    var backupPassword by remember { mutableStateOf("") }
    var pendingBackupMode by remember { mutableStateOf<String?>(null) }
    var panicStep by remember { mutableStateOf(0) }
    var activeGuideId by remember { mutableStateOf<PlatformGuideId?>(null) }
    var adobePickerTarget by remember { mutableStateOf<AdobeTargetApp?>(null) }

    // News Tracker RSS input state
    var newFeedName by remember { mutableStateOf("") }
    var newFeedUrl by remember { mutableStateOf("") }

    // Duress / Decoy PIN input state
    var masterPinInput by remember { mutableStateOf("") }
    var decoyPinInput by remember { mutableStateOf("") }
    var pinStatusError by remember { mutableStateOf<String?>(null) }

    // Custom Webhook URL input state (Discord / n8n / Slack)
    var webhookUrlInput by remember(vaultSnapshot.customWebhookUrl) {
        mutableStateOf(vaultSnapshot.customWebhookUrl)
    }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageUsage()
        viewModel.refreshCrashLog()
        viewModel.refreshAdobeAppBindings()
    }

    val exportBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && backupPassword.length >= 6) {
            viewModel.exportEncryptedBackupToUri(uri, backupPassword)
            backupPassword = ""
        }
    }

    val importBackupLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && backupPassword.length >= 6) {
            viewModel.importEncryptedBackupFromUri(uri, backupPassword)
            backupPassword = ""
        }
    }

    val primaryColor = MaterialTheme.colorScheme.primary
    val normalizedQuery = searchQuery.trim()
    fun matchesSearch(vararg terms: String): Boolean {
        if (normalizedQuery.isBlank()) return true
        return terms.any { it.contains(normalizedQuery, ignoreCase = true) }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header with Back Navigation
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column(modifier = Modifier.weight(1f)) {
                    Text("Settings Hub", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Appearance · AI · Automation · Security",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
        }

        // v3.0 Live Settings Search Bar
        item {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null, tint = primaryColor) },
                placeholder = { Text("Search settings (theme, haptics, tts, rss, pin, ota, backup)...") },
                singleLine = true,
                modifier = Modifier.fillMaxWidth()
            )
        }

        // v3.0 Pinned Quick Settings Strip
        if (uiConfig.pinnedSettingIds.isNotEmpty() && normalizedQuery.isBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, primaryColor.copy(alpha = 0.5f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.spacedBy(6.dp)
                        ) {
                            Icon(Icons.Filled.Star, contentDescription = null, tint = primaryColor)
                            Text("Pinned Controls", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                        }
                        uiConfig.pinnedSettingIds.forEach { pinId ->
                            when (pinId) {
                                "theme_studio" -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("UI Theme & Layout Studio (${uiConfig.themePreset.label})", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    OutlinedButton(onClick = onOpenThemeStudio) { Text("Open") }
                                    IconButton(onClick = { viewModel.togglePinnedSetting(pinId) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Unpin", tint = primaryColor)
                                    }
                                }
                                "backup_center" -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("4-Layer Encrypted Backup & Sync", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    OutlinedButton(onClick = onOpenBackupCenter) { Text("Open") }
                                    IconButton(onClick = { viewModel.togglePinnedSetting(pinId) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Unpin", tint = primaryColor)
                                    }
                                }
                                "network_inspector" -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Live API Network Inspector (netlog)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = isNetlogEnabled, onCheckedChange = { viewModel.toggleNetlogStreaming() })
                                    IconButton(onClick = { viewModel.togglePinnedSetting(pinId) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Unpin", tint = primaryColor)
                                    }
                                }
                                "haptics_toggle" -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Haptic Feedback", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = uiConfig.hapticFeedbackEnabled, onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) })
                                    IconButton(onClick = { viewModel.togglePinnedSetting(pinId) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Unpin", tint = primaryColor)
                                    }
                                }
                                "auto_read_tts" -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Auto-Read AI Replies (TTS)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = settings.autoReadAiReplies, onCheckedChange = { viewModel.setAutoReadAiReplies(it) })
                                    IconButton(onClick = { viewModel.togglePinnedSetting(pinId) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Unpin", tint = primaryColor)
                                    }
                                }
                                "wifi_only_dl" -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Wi-Fi Only Auto-Download", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = settings.wifiOnlyAutoDownload, onCheckedChange = { viewModel.setWifiOnlyAutoDownload(it) })
                                    IconButton(onClick = { viewModel.togglePinnedSetting(pinId) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Unpin", tint = primaryColor)
                                    }
                                }
                                "app_lock" -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Biometric / PIN App Lock", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = vaultSnapshot.appLockEnabled, onCheckedChange = { viewModel.setAppLockEnabled(it) })
                                    IconButton(onClick = { viewModel.togglePinnedSetting(pinId) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Unpin", tint = primaryColor)
                                    }
                                }
                                "flag_secure" -> Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Text("Privacy Screen Shield (FLAG_SECURE)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                                    Switch(checked = vaultSnapshot.flagSecureEnabled, onCheckedChange = { viewModel.setFlagSecure(it) })
                                    IconButton(onClick = { viewModel.togglePinnedSetting(pinId) }) {
                                        Icon(Icons.Filled.Star, contentDescription = "Unpin", tint = primaryColor)
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }

        // v5.5 Cloud DB & Roles (Supabase PostgreSQL Compressed Delta Sync + Google OAuth PKCE)
        if (matchesSearch("cloud", "supabase", "postgres", "google", "oauth", "rbac", "roles", "sync", "team")) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.45f))
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("Cloud DB & Roles (Supabase + Google RBAC)", style = MaterialTheme.typography.titleMedium)
                                Text(
                                    "Active Role: ${cloudRole.badgeText} · ${cloudSyncStatus.lastMessage}",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            if (BuildConfig.DEBUG || cloudRole == CloudUserRole.OWNER) {
                                PlatformGuideTriggerButton(
                                    onClick = { activeGuideId = PlatformGuideId.SUPABASE_CLOUD }
                                )
                            }
                        }
                        Button(
                            onClick = onOpenCloudAccount,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.AdminPanelSettings, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Open Cloud DB, Delta Sync & Team Roles")
                        }
                    }
                }
            }
        }

        // Quick access to the settings used most often.
        if (matchesSearch("theme", "studio", "layout", "backup", "sync", "analytics", "netlog", "wallpaper", "nav")) {
            item {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Button(
                        onClick = onOpenThemeStudio,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Palette, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("UI Theme Studio, Wallpaper & Bottom Nav Customizer")
                    }
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedButton(
                            onClick = onOpenBackupCenter,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CloudSync, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Backup")
                        }
                        OutlinedButton(
                            onClick = onOpenAnalytics,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.BarChart, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Analytics")
                        }
                    }
                }
            }
        }

        // =====================================================================
        // SECTION 1: APPEARANCE & APP ICON
        // =====================================================================
        if (matchesSearch("appearance", "icon", "disguise", "calculator", "notes", "accent", "color", "font", "size", "theme")) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
                    Text("1. Appearance & App Icon Disguise", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    Text(
                        "Switch the Android launcher icon and home screen label dynamically without reinstalling.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    // 2x2 Visual App Icon Picker Grid
                    val variants = AppIconVariant.entries
                    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        variants.chunked(2).forEach { rowItems ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                rowItems.forEach { variant ->
                                    val isSelected = settings.iconVariant == variant
                                    val iconVector = when (variant) {
                                        AppIconVariant.DEFAULT -> Icons.Filled.AutoAwesome
                                        AppIconVariant.CYBER -> Icons.Filled.Bolt
                                        AppIconVariant.CALCULATOR -> Icons.Filled.Calculate
                                        AppIconVariant.NOTES -> Icons.Filled.EditNote
                                    }
                                    Card(
                                        colors = CardDefaults.cardColors(
                                            containerColor = if (isSelected) MaterialTheme.colorScheme.surfaceVariant else MaterialTheme.colorScheme.background
                                        ),
                                        border = BorderStroke(
                                            width = if (isSelected) 2.dp else 1.dp,
                                            color = if (isSelected) primaryColor else MaterialTheme.colorScheme.outline
                                        ),
                                        modifier = Modifier
                                            .weight(1f)
                                            .clickable {
                                                if (!isSelected) pendingIconVariant = variant
                                            }
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
                                                Surface(
                                                    color = Color(variant.previewAccentHex).copy(alpha = 0.16f),
                                                    shape = RoundedCornerShape(10.dp),
                                                    modifier = Modifier.size(38.dp)
                                                ) {
                                                    Box(contentAlignment = Alignment.Center) {
                                                        Icon(
                                                            imageVector = iconVector,
                                                            contentDescription = variant.displayTitle,
                                                            tint = Color(variant.previewAccentHex),
                                                            modifier = Modifier.size(22.dp)
                                                        )
                                                    }
                                                }
                                                if (isSelected) {
                                                    Icon(
                                                        Icons.Filled.CheckCircle,
                                                        contentDescription = "Active Icon",
                                                        tint = primaryColor,
                                                        modifier = Modifier.size(18.dp)
                                                    )
                                                }
                                            }
                                            Text(variant.displayTitle, style = MaterialTheme.typography.titleMedium)
                                            Text(
                                                "Label: \"${variant.launcherLabel}\"",
                                                style = MaterialTheme.typography.labelMedium,
                                                color = if (variant.isStealth) Color(0xFFF59E0B) else MaterialTheme.colorScheme.onSurfaceVariant
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Theme Accent Color Picker
                    Text("Theme Accent Color", style = MaterialTheme.typography.titleMedium)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        ThemeAccent.entries.forEach { accent ->
                            val selected = settings.themeAccent == accent
                            Surface(
                                color = if (selected) accent.primaryContainer else MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(
                                    width = if (selected) 2.dp else 1.dp,
                                    color = if (selected) accent.primary else MaterialTheme.colorScheme.outline
                                ),
                                shape = RoundedCornerShape(10.dp),
                                modifier = Modifier
                                    .weight(1f)
                                    .clickable { viewModel.setThemeAccent(accent) }
                            ) {
                                Column(
                                    modifier = Modifier.padding(vertical = 8.dp, horizontal = 4.dp),
                                    horizontalAlignment = Alignment.CenterHorizontally,
                                    verticalArrangement = Arrangement.spacedBy(4.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(20.dp)
                                            .clip(CircleShape)
                                            .background(accent.primary)
                                    )
                                    Text(
                                        text = accent.label.substringBefore(" "),
                                        style = MaterialTheme.typography.labelMedium,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    // Chat Font Size Slider (12sp..20sp)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text("Chat Font Size", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "${settings.chatFontSizeSp.roundToInt()}sp",
                            style = MaterialTheme.typography.labelMedium,
                            color = primaryColor
                        )
                    }
                    Slider(
                        value = settings.chatFontSizeSp,
                        onValueChange = { viewModel.setChatFontSize(it.roundToInt().toFloat()) },
                        valueRange = 12f..20f,
                        steps = 7
                    )
                }
            }
        }
        }

        // =====================================================================
        // SECTION 2: AI ENGINE & GENERATION CONTROLS
        // =====================================================================
        if (matchesSearch("ai", "engine", "temperature", "token", "context", "window", "tts", "voice", "hindi", "read")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. AI Engine & Generation Controls", style = MaterialTheme.typography.titleMedium, color = primaryColor)

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Temperature", style = MaterialTheme.typography.bodyMedium)
                        Text("%.2f".format(settings.temperature), style = MaterialTheme.typography.labelMedium, color = primaryColor)
                    }
                    Slider(
                        value = settings.temperature,
                        onValueChange = { viewModel.setTemperature((it * 20).roundToInt() / 20f) },
                        valueRange = 0f..1.5f
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Max Output Tokens", style = MaterialTheme.typography.bodyMedium)
                        Text("${settings.maxOutputTokens} tok", style = MaterialTheme.typography.labelMedium, color = primaryColor)
                    }
                    Slider(
                        value = settings.maxOutputTokens.toFloat(),
                        onValueChange = { viewModel.setMaxOutputTokens(((it / 256f).roundToInt() * 256).coerceIn(256, 8192)) },
                        valueRange = 256f..8192f
                    )

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Chat Context Window", style = MaterialTheme.typography.bodyMedium)
                        Text("${settings.chatContextWindow} msgs", style = MaterialTheme.typography.labelMedium, color = primaryColor)
                    }
                    Slider(
                        value = settings.chatContextWindow.toFloat(),
                        onValueChange = { viewModel.setChatContextWindow(it.roundToInt()) },
                        valueRange = 4f..30f,
                        steps = 25
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Read AI Replies (Offline TTS)", style = MaterialTheme.typography.bodyMedium)
                            Text("Auto-detects Hindi (Devanagari) & English", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.togglePinnedSetting("auto_read_tts") }) {
                            val pinned = "auto_read_tts" in uiConfig.pinnedSettingIds
                            Icon(
                                if (pinned) Icons.Filled.Star else Icons.Filled.StarOutline,
                                contentDescription = "Pin Auto-Read TTS",
                                tint = if (pinned) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.autoReadAiReplies,
                            onCheckedChange = { viewModel.setAutoReadAiReplies(it) }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Hindi / English TTS Voice Speed", style = MaterialTheme.typography.bodyMedium)
                        Text("%.2fx".format(settings.ttsSpeechRate), style = MaterialTheme.typography.labelMedium, color = primaryColor)
                    }
                    Slider(
                        value = settings.ttsSpeechRate,
                        onValueChange = { viewModel.setTtsSpeechRate((it * 20).roundToInt() / 20f) },
                        valueRange = 0.75f..1.5f
                    )
                }
            }
        }
        }

        // =====================================================================
        // SECTION 3: AUTOMATION & DOWNLOAD RULES
        // =====================================================================
        if (matchesSearch("automation", "download", "wifi", "kaggle", "telegram", "mtproto", "2gb", "guide", "poll", "quota", "gpu", "watchdog", "webhook", "discord", "n8n", "slack")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("3. Automation & Download Rules", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                        PlatformGuideTriggerButton(
                            label = "? Kaggle Guide",
                            onClick = { activeGuideId = PlatformGuideId.KAGGLE_WORKER }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Direct Kaggle → Telegram Upload (Up to 2 GB)", style = MaterialTheme.typography.bodyMedium)
                            Text("Headless Telethon MTProto upload directly from Kaggle GPU", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.directKaggleTelegramUpload,
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
                            Text("Avoids pulling large MP4 onto phone; download a copy anytime in Queue", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.skipPhoneDownloadWhenSentToTelegram,
                            enabled = settings.directKaggleTelegramUpload,
                            onCheckedChange = { viewModel.setSkipPhoneDownloadWhenSentToTelegram(it) }
                        )
                    }

                    PlatformGuideTriggerButton(
                        label = "? Telegram 2 GB MTProto Setup Guide & Commands",
                        onClick = { activeGuideId = PlatformGuideId.TELEGRAM_2GB }
                    )

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Wi-Fi Only Auto-Download", style = MaterialTheme.typography.bodyMedium)
                            Text("Pause Kaggle MP4 downloads when on mobile data", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        IconButton(onClick = { viewModel.togglePinnedSetting("wifi_only_dl") }) {
                            val pinned = "wifi_only_dl" in uiConfig.pinnedSettingIds
                            Icon(
                                if (pinned) Icons.Filled.Star else Icons.Filled.StarOutline,
                                contentDescription = "Pin Wi-Fi Only Download",
                                tint = if (pinned) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = settings.wifiOnlyAutoDownload,
                            onCheckedChange = { viewModel.setWifiOnlyAutoDownload(it) }
                        )
                    }

                    Text("Kaggle Polling Interval", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppSettingsRepository.VALID_POLL_INTERVALS.forEach { sec ->
                            FilterChip(
                                selected = settings.kagglePollIntervalSec == sec,
                                onClick = { viewModel.setKagglePollInterval(sec) },
                                label = { Text("${sec}s") }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Clean Temp Files After Merge", style = MaterialTheme.typography.bodyMedium)
                            Text("Purge Media3 scratch cache once MP4 is saved", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoCleanTempAfterMerge,
                            onCheckedChange = { viewModel.setAutoCleanTempAfterMerge(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tunnel Health Watchdog (WorkManager)", style = MaterialTheme.typography.bodyMedium)
                            Text("Periodic background ping to Primary Tunnel /models", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = tunnelWatchdogEnabled,
                            onCheckedChange = { viewModel.setTunnelWatchdogEnabled(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    val remainingQuota = (30.0 - weeklyUsedGpuHours).coerceAtLeast(0.0)
                    val isLowQuota = remainingQuota < 2.0
                    Text("Kaggle 30-Hour Weekly GPU Quota Tracker", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = "Used: %.1f hrs / 30.0 hrs — Resets weekly".format(weeklyUsedGpuHours),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (isLowQuota) CrimsonDanger else EmeraldAccent
                    )
                    LinearProgressIndicator(
                        progress = { (weeklyUsedGpuHours / 30.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (isLowQuota) CrimsonDanger else primaryColor
                    )
                    if (isLowQuota) {
                        Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            Icon(Icons.Filled.Warning, contentDescription = null, tint = CrimsonDanger)
                            Text(
                                "Less than 2.0 hrs remaining in this week's Kaggle GPU quota.",
                                style = MaterialTheme.typography.labelMedium,
                                color = CrimsonDanger
                            )
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("Custom Webhook Alerts (Discord / n8n / Slack)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Sends JSON event payloads on Kaggle render completion/failure and Daily RSS News Briefs.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = webhookUrlInput,
                        onValueChange = { webhookUrlInput = it },
                        label = { Text("CUSTOM_WEBHOOK_URL (HTTPS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.saveCustomWebhookUrl(webhookUrlInput) },
                            modifier = Modifier.weight(1f)
                        ) { Text("Save Webhook") }
                        Button(
                            onClick = {
                                viewModel.saveCustomWebhookUrl(webhookUrlInput)
                                viewModel.testCustomWebhook()
                            },
                            enabled = webhookUrlInput.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) { Text("Test Webhook") }
                    }
                }
            }
        }
        }

        // =====================================================================
        // SECTION 3B: NEWS TRACKER (SCHEDULED RSS NEWS-TO-SCRIPT AGENT)
        // =====================================================================
        if (matchesSearch("news", "rss", "feed", "agent", "script", "brief", "workmanager")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.RssFeed, contentDescription = null, tint = primaryColor)
                        Text("News Tracker (RSS WorkManager Agent)", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    }
                    Text(
                        "Polls custom RSS feeds in the background, drafts AI summaries & video angles, and notifies you for manual approval. Never auto-renders GPU.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Enable Background RSS Agent", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = settings.newsAgentEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setNewsAgentSchedule(enabled, settings.newsCheckIntervalHours)
                            }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Row(horizontalArrangement = Arrangement.spacedBy(6.dp), verticalAlignment = Alignment.CenterVertically) {
                            Text("Interval:", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            AppSettingsRepository.VALID_NEWS_INTERVALS.forEach { hrs ->
                                FilterChip(
                                    selected = settings.newsCheckIntervalHours == hrs,
                                    onClick = { viewModel.setNewsAgentSchedule(settings.newsAgentEnabled, hrs) },
                                    label = { Text("${hrs}h") }
                                )
                            }
                        }
                        OutlinedButton(onClick = { viewModel.runNewsAgentNow() }) {
                            Text("Run Now")
                        }
                    }

                    OutlinedTextField(
                        value = newFeedName,
                        onValueChange = { newFeedName = it },
                        label = { Text("Feed Label (e.g. Hacker News AI)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newFeedUrl,
                        onValueChange = { newFeedUrl = it },
                        label = { Text("RSS / Atom Feed URL (HTTPS)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.addRssFeed(newFeedName, newFeedUrl)
                            newFeedName = ""
                            newFeedUrl = ""
                        },
                        enabled = newFeedUrl.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save RSS Feed")
                    }

                    if (rssFeeds.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text("Saved RSS Feeds (${rssFeeds.size})", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        rssFeeds.forEach { feed ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(feed.name, style = MaterialTheme.typography.bodyMedium)
                                    Text(feed.feedUrl, style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant, maxLines = 1)
                                }
                                IconButton(onClick = { viewModel.deleteRssFeed(feed) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete RSS Feed", tint = CrimsonDanger)
                                }
                            }
                        }
                    }

                    if (newsDigests.isNotEmpty()) {
                        HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                        Text("Pending AI Briefs for Approval (${newsDigests.size})", style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                        newsDigests.take(3).forEach { digest ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                                shape = RoundedCornerShape(8.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                                    Text(digest.title, style = MaterialTheme.typography.titleMedium)
                                    Text(digest.summaryText.take(220), style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                        Button(
                                            onClick = {
                                                viewModel.approveDigestToDirector(digest)
                                                onOpenDirector()
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Text("Approve -> Script")
                                        }
                                        OutlinedButton(onClick = { viewModel.deleteNewsDigest(digest) }) {
                                            Text("Dismiss")
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
        }

        // =====================================================================
        // SECTION 3C: NICHE CONTEXT ENGINE & AUTO-TYPER (v4.0)
        // =====================================================================
        if (matchesSearch("niche", "auto", "type", "speed", "continuity", "loop", "summarize", "series", "next")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = primaryColor)
                        Text("3C. Niche Context & Auto-Typer", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    }
                    Text(
                        "Control typewriter animation speed, automatic post-render sequel generation, niche rolling memory compression, and default Reels/Shorts seamless loop mode.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Type Prompt Animation", style = MaterialTheme.typography.bodyMedium)
                            Text("Type selected Next Command chips smoothly into prompt fields", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoTypeEnabled,
                            onCheckedChange = { viewModel.setAutoTypeEnabled(it) }
                        )
                    }

                    Text("Auto-Type Speed", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AutoTypeSpeedMode.entries.forEach { mode ->
                            FilterChip(
                                selected = settings.autoTypeSpeedMode == mode,
                                onClick = { viewModel.setAutoTypeSpeedMode(mode) },
                                label = { Text(mode.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Generate Next Commands After Render", style = MaterialTheme.typography.bodyMedium)
                            Text("Draft 4 non-repeating sequel & twist ideas when Kaggle finishes", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoGenerateNextAfterRender,
                            onCheckedChange = { viewModel.setAutoGenerateNextAfterRender(it) }
                        )
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Auto-Summarize Niche Memory Threshold", style = MaterialTheme.typography.bodyMedium)
                        Text("${settings.autoSummarizeNicheThreshold} msgs", style = MaterialTheme.typography.labelMedium, color = primaryColor)
                    }
                    Slider(
                        value = settings.autoSummarizeNicheThreshold.toFloat(),
                        onValueChange = { viewModel.setAutoSummarizeNicheThreshold(it.roundToInt()) },
                        valueRange = 10f..40f,
                        steps = 29
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Seamless Loop Default (Reels / Shorts)", style = MaterialTheme.typography.bodyMedium)
                            Text("Default to syntactic lead-in endings & 0.25s tail micro-fade", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.seamlessLoopDefault,
                            onCheckedChange = { viewModel.setSeamlessLoopDefault(it) }
                        )
                    }
                }
            }
        }
        }

        // =====================================================================
        // SECTION 3D: v4.5 PROJECT & GIT AUTOMATION
        // =====================================================================
        if (matchesSearch("project", "git", "github", "branch", "owner", "username", "actions", "commit", "auto-push", "ci", "cd", "pms")) {
        item {
            var defaultOwnerDraft by remember(settings.defaultGithubOwner) { mutableStateOf(settings.defaultGithubOwner) }
            var defaultBranchDraft by remember(settings.defaultGithubBranch) { mutableStateOf(settings.defaultGithubBranch) }
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, tint = primaryColor)
                        Text("3D. Project & Git Automation", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    }
                    Text(
                        "Configure defaults for the zero-dependency Atomic GitHub Git Data API engine, live GitHub Actions build monitor, and AI commit messages.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )

                    OutlinedTextField(
                        value = defaultOwnerDraft,
                        onValueChange = {
                            defaultOwnerDraft = it
                            viewModel.setDefaultGithubOwner(it)
                        },
                        label = { Text("Default GitHub Username / Org") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    OutlinedTextField(
                        value = defaultBranchDraft,
                        onValueChange = {
                            defaultBranchDraft = it
                            viewModel.setDefaultGithubBranch(it)
                        },
                        label = { Text("Default Branch (e.g. main)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Watch GitHub Actions After Push", style = MaterialTheme.typography.bodyMedium)
                            Text("Automatically poll workflow run status every 15s and surface OTA APK install on success", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.autoWatchActionsAfterPush,
                            onCheckedChange = { viewModel.setAutoWatchActionsAfterPush(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("AI Auto-Commit Message", style = MaterialTheme.typography.bodyMedium)
                            Text("Generate Conventional Commit messages automatically from local SHA-256 diffs", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = settings.aiAutoCommitMessageEnabled,
                            onCheckedChange = { viewModel.setAiAutoCommitMessageEnabled(it) }
                        )
                    }
                }
            }
        }
        }

        // =====================================================================
        // SECTION 4: SECURITY & PRIVACY CONTROLS
        // =====================================================================
        if (matchesSearch("security", "privacy", "biometric", "pin", "lock", "decoy", "duress", "flag_secure", "wipe", "panic", "backup", "audit")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("4. Security & Privacy Controls", style = MaterialTheme.typography.titleMedium, color = primaryColor)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Biometric / PIN App Lock", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.togglePinnedSetting("app_lock") }) {
                            val pinned = "app_lock" in uiConfig.pinnedSettingIds
                            Icon(
                                if (pinned) Icons.Filled.Star else Icons.Filled.StarOutline,
                                contentDescription = "Pin App Lock",
                                tint = if (pinned) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = vaultSnapshot.appLockEnabled,
                            onCheckedChange = { viewModel.setAppLockEnabled(it) }
                        )
                    }

                    Text("Auto-Lock Timeout", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutoLockTimeout.entries.forEach { timeout ->
                            FilterChip(
                                selected = vaultSnapshot.autoLockTimeout == timeout.name,
                                onClick = { viewModel.setAutoLockTimeout(timeout) },
                                label = { Text(timeout.label) }
                            )
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Privacy Screen Shield (FLAG_SECURE)", style = MaterialTheme.typography.bodyMedium, modifier = Modifier.weight(1f))
                        IconButton(onClick = { viewModel.togglePinnedSetting("flag_secure") }) {
                            val pinned = "flag_secure" in uiConfig.pinnedSettingIds
                            Icon(
                                if (pinned) Icons.Filled.Star else Icons.Filled.StarOutline,
                                contentDescription = "Pin FLAG_SECURE",
                                tint = if (pinned) primaryColor else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Switch(
                            checked = vaultSnapshot.flagSecureEnabled,
                            onCheckedChange = { viewModel.setFlagSecure(it) }
                        )
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("Duress / Decoy PIN Mode (Salted SHA-256)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Entering your Decoy PIN unlocks a harmless Quick Notes sandbox with zero access to real Vault keys, chats, or videos — without wiping real data.",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "Master PIN: ${if (hasMasterPin) "Configured" else "Not set"}  ·  Decoy PIN: ${if (hasDecoyPin) "Configured" else "Not set"}",
                        style = MaterialTheme.typography.labelMedium,
                        color = if (hasDecoyPin) EmeraldAccent else MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = masterPinInput,
                            onValueChange = { masterPinInput = it.filter(Char::isDigit).take(10); pinStatusError = null },
                            label = { Text("Master PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                pinStatusError = viewModel.setMasterPin(masterPinInput)
                                if (pinStatusError == null) masterPinInput = ""
                            },
                            enabled = masterPinInput.length >= 4
                        ) { Text("Save") }
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedTextField(
                            value = decoyPinInput,
                            onValueChange = { decoyPinInput = it.filter(Char::isDigit).take(10); pinStatusError = null },
                            label = { Text("Decoy PIN") },
                            visualTransformation = PasswordVisualTransformation(),
                            keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.NumberPassword),
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        OutlinedButton(
                            onClick = {
                                pinStatusError = viewModel.setDecoyPin(decoyPinInput)
                                if (pinStatusError == null) decoyPinInput = ""
                            },
                            enabled = decoyPinInput.length >= 4
                        ) { Text("Save") }
                    }
                    if (pinStatusError != null) {
                        Text(pinStatusError.orEmpty(), style = MaterialTheme.typography.labelMedium, color = CrimsonDanger)
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.lockApp() },
                            enabled = hasMasterPin || vaultSnapshot.appLockEnabled,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Lock, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Lock & Test")
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearAllPins() },
                            enabled = hasMasterPin || hasDecoyPin
                        ) { Text("Clear PINs") }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("Failed PIN Audit Log & Emergency Auto-Wipe", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Automatically trigger full Panic Wipe if an intruder exceeds consecutive failed PIN attempts:",
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf(0 to "Off", 5 to "5 Failures", 10 to "10 Failures").forEach { (limit, label) ->
                            FilterChip(
                                selected = autoWipeThreshold == limit,
                                onClick = { viewModel.setAutoWipeThreshold(limit) },
                                label = { Text(label) }
                            )
                        }
                    }
                    Surface(
                        color = MaterialTheme.colorScheme.surfaceVariant,
                        border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Text(
                                    "Audit Entries (${failedPinAuditLog.size})",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = if (failedPinAuditLog.isNotEmpty()) CrimsonDanger else EmeraldAccent
                                )
                                if (failedPinAuditLog.isNotEmpty()) {
                                    TextButton(onClick = { viewModel.clearFailedPinAuditLog() }) {
                                        Text("Clear Log")
                                    }
                                }
                            }
                            if (failedPinAuditLog.isEmpty()) {
                                Text(
                                    "No unauthorized PIN attempts recorded.",
                                    style = MaterialTheme.typography.labelMedium,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            } else {
                                failedPinAuditLog.take(6).forEach { entry ->
                                    Text(
                                        text = entry,
                                        fontFamily = FontFamily.Monospace,
                                        style = MaterialTheme.typography.labelMedium,
                                        color = CrimsonDanger
                                    )
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)
                    Text("AES-256 Encrypted .omniai Backup (SAF)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { pendingBackupMode = "EXPORT" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export")
                        }
                        OutlinedButton(
                            onClick = { pendingBackupMode = "IMPORT" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore")
                        }
                    }

                    Button(
                        onClick = { panicStep = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Double-Confirmed Panic Wipe")
                    }
                }
            }
        }
        }

        // =====================================================================
        // SECTION 5: STORAGE, OTA UPDATES & CRASH DIAGNOSTICS
        // =====================================================================
        if (matchesSearch("storage", "cache", "ota", "update", "github", "crash", "diagnostics", "log", "version")) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("5. Storage, OTA Updates & Crash Diagnostics", style = MaterialTheme.typography.titleMedium, color = primaryColor)

                    Text(
                        text = "Videos: %.2f MB  ·  Merged: %.2f MB  ·  Cache: %.2f MB  ·  Room DB: %.2f MB"
                            .format(storage.videosMb, storage.mergedMb, storage.tempCacheMb, storage.dbMb),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = { viewModel.cleanTempCache() },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("1-Tap Clear Temp & OTA Cache")
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Text(
                        otaState.message ?: "Current Build: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (otaState.isDownloading) {
                        LinearProgressIndicator(
                            progress = { (otaState.downloadProgress / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.checkGitHubOtaUpdate() },
                            enabled = !otaState.isChecking && !otaState.isDownloading,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.SystemUpdate, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Check GitHub OTA")
                        }
                        if (otaState.latestRelease != null) {
                            Button(
                                onClick = {
                                    viewModel.downloadLatestOtaApk { apkFile ->
                                        val intent = StorageGalleryHelper.createInstallApkIntent(activity, apkFile)
                                        activity.startActivity(intent)
                                    }
                                },
                                enabled = !otaState.isDownloading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Install ${otaState.latestRelease?.tagName}")
                            }
                        }
                    }

                    HorizontalDivider(color = MaterialTheme.colorScheme.outline)

                    Text("Local Crash Diagnostics (filesDir/crash_log.txt)", style = MaterialTheme.typography.labelMedium, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = {
                                viewModel.refreshCrashLog()
                                showCrashLogDialog = true
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.BugReport, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("View Crash Log")
                        }
                        OutlinedButton(
                            onClick = { viewModel.clearCrashLog() },
                            enabled = crashLog.isNotBlank()
                        ) {
                            Text("Clear Log")
                        }
                    }
                }
            }
        }
        }

        // =====================================================================
        // MEDIA & EXTERNAL APPS: ADOBE EXPRESS & FIREFLY APP BINDING (v5.4)
        // =====================================================================
        if (matchesSearch("media", "external", "apps", "adobe", "express", "firefly", "thumbnail", "bind", "package")) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Apps, contentDescription = null, tint = primaryColor)
                                Column {
                                    Text("Media & External Apps", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                                    Text(
                                        "Adobe Express & Firefly App Binding (Strictly Native Apps)",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = MaterialTheme.colorScheme.onSurfaceVariant
                                    )
                                }
                            }
                            TextButton(onClick = { viewModel.refreshAdobeAppBindings() }) {
                                Text("Rescan")
                            }
                        }

                        // Adobe Express Row
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(10.dp),
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
                                    Text("Adobe Express", style = MaterialTheme.typography.titleSmall)
                                    val expressAccent = if (adobeExpressStatus.isInstalled) EmeraldAccent else CrimsonDanger
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (adobeExpressStatus.isInstalled) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                            contentDescription = null,
                                            tint = expressAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (adobeExpressStatus.isInstalled) "Installed" else "Not Found",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = expressAccent
                                        )
                                    }
                                }
                                Text(
                                    text = "Package: ${adobeExpressStatus.resolvedPackage ?: adobeExpressStatus.configuredPackage}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { adobePickerTarget = AdobeTargetApp.ADOBE_EXPRESS },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Change Bound App")
                                }
                            }
                        }

                        // Adobe Firefly Row
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, MaterialTheme.colorScheme.outline),
                            shape = RoundedCornerShape(10.dp),
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
                                    Text("Adobe Firefly", style = MaterialTheme.typography.titleSmall)
                                    val fireflyAccent = if (adobeFireflyStatus.isInstalled) EmeraldAccent else CrimsonDanger
                                    Row(
                                        verticalAlignment = Alignment.CenterVertically,
                                        horizontalArrangement = Arrangement.spacedBy(4.dp)
                                    ) {
                                        Icon(
                                            imageVector = if (adobeFireflyStatus.isInstalled) Icons.Filled.CheckCircle else Icons.Filled.ErrorOutline,
                                            contentDescription = null,
                                            tint = fireflyAccent,
                                            modifier = Modifier.size(16.dp)
                                        )
                                        Text(
                                            text = if (adobeFireflyStatus.isInstalled) "Installed" else "Not Found",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = fireflyAccent
                                        )
                                    }
                                }
                                Text(
                                    text = "Package: ${adobeFireflyStatus.resolvedPackage ?: adobeFireflyStatus.configuredPackage}",
                                    fontFamily = FontFamily.Monospace,
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                                OutlinedButton(
                                    onClick = { adobePickerTarget = AdobeTargetApp.ADOBE_FIREFLY },
                                    modifier = Modifier.fillMaxWidth()
                                ) {
                                    Text("Change Bound App")
                                }
                            }
                        }
                    }
                }
            }
        }
    }

    // Confirmation Dialog for Dynamic App Icon Switch
    pendingIconVariant?.let { target ->
        AlertDialog(
            onDismissRequest = { pendingIconVariant = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Apply Launcher Icon Switch?") },
            text = {
                Text(
                    "Your home screen icon will change to '${target.displayTitle}' and the app label will appear as \"${target.launcherLabel}\". Some launchers take 2–5 seconds to refresh."
                )
            },
            confirmButton = {
                Button(onClick = {
                    val chosen = target
                    pendingIconVariant = null
                    viewModel.switchAppIcon(chosen)
                }) {
                    Text("Apply Icon")
                }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingIconVariant = null }) {
                    Text("Cancel")
                }
            }
        )
    }

    // Crash Log Viewer & Copy Dialog
    if (showCrashLogDialog) {
        AlertDialog(
            onDismissRequest = { showCrashLogDialog = false },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Crash Diagnostics Log") },
            text = {
                Column(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(280.dp)
                        .verticalScroll(rememberScrollState())
                ) {
                    Text(
                        text = crashLog.ifBlank { "No uncaught crashes recorded in filesDir/crash_log.txt." },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = {
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    if (crashLog.isNotBlank()) {
                        TextButton(onClick = {
                            clipboard.setText(AnnotatedString(crashLog))
                            viewModel.postToast("Crash log copied to clipboard.")
                        }) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Copy")
                        }
                    }
                    OutlinedButton(onClick = { showCrashLogDialog = false }) {
                        Text("Close")
                    }
                }
            }
        )
    }

    // Encrypted .omniai Backup Passphrase Dialog
    if (pendingBackupMode != null) {
        val isExport = pendingBackupMode == "EXPORT"
        AlertDialog(
            onDismissRequest = { pendingBackupMode = null },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text(if (isExport) "Encrypt .omniai Backup" else "Decrypt .omniai Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a passphrase (min 6 chars) for PBKDF2-SHA256 + AES-256-GCM.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = backupPassword,
                        onValueChange = { backupPassword = it },
                        label = { Text("Backup Passphrase") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        val mode = pendingBackupMode
                        pendingBackupMode = null
                        if (mode == "EXPORT") {
                            exportBackupLauncher.launch("OmniAI_Backup_${System.currentTimeMillis()}.omniai")
                        } else {
                            importBackupLauncher.launch(arrayOf("*/*"))
                        }
                    },
                    enabled = backupPassword.length >= 6
                ) { Text("Continue") }
            },
            dismissButton = {
                OutlinedButton(onClick = { pendingBackupMode = null; backupPassword = "" }) {
                    Text("Cancel")
                }
            }
        )
    }

    if (panicStep == 1) {
        AlertDialog(
            onDismissRequest = { panicStep = 0 },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("Confirm Panic Wipe (1/2)") },
            text = { Text("Shred EncryptedSharedPreferences, clear Room database, and purge all local renders & crash logs?") },
            confirmButton = {
                Button(
                    onClick = { panicStep = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) { Text("Proceed") }
            },
            dismissButton = {
                OutlinedButton(onClick = { panicStep = 0 }) { Text("Cancel") }
            }
        )
    }

    if (panicStep == 2) {
        AlertDialog(
            onDismissRequest = { panicStep = 0 },
            containerColor = MaterialTheme.colorScheme.surface,
            title = { Text("FINAL WARNING: Shred All Data (2/2)") },
            text = { Text("Action cannot be undone. Confirm immediate cryptographic shred?") },
            confirmButton = {
                Button(
                    onClick = {
                        panicStep = 0
                        viewModel.executePanicWipe()
                    },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) { Text("SHRED NOW") }
            },
            dismissButton = {
                OutlinedButton(onClick = { panicStep = 0 }) { Text("Abort") }
            }
        )
    }

    activeGuideId?.takeIf {
        it != PlatformGuideId.SUPABASE_CLOUD || BuildConfig.DEBUG || cloudRole == CloudUserRole.OWNER
    }?.let { guideId ->
        PlatformGuideBottomSheet(
            guideId = guideId,
            onDismiss = { activeGuideId = null },
            onToast = { viewModel.postToast(it) },
            onRunInTerminal = { command ->
                activeGuideId = null
                onRunGuideCommandInTerminal(command)
            }
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
