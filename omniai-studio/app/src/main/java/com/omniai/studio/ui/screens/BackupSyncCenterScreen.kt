package com.omniai.studio.ui.screens

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
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.CloudDownload
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.ContentPaste
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import com.omniai.studio.backup.BackupSelectionOptions
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.viewmodel.OmniAiViewModel
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Dedicated 4-Layer Encrypted Backup & Cloud Sync Center accessible from Settings & Tab 5 (Vault):
 * 1) Selective AES-256-GCM Backup Engine (.omniai.enc SAF + Copy/Paste Migration Text)
 * 2) Zero-Cost Private GitHub Cloud Auto-Sync (Push/Restore + Daily/Weekly Wi-Fi WorkManager)
 * 3) Telegram Bot Cloud Archive (Encrypted snapshots + Auto-Archive rendered videos <= 49 MB)
 * 4) Rolling Local Auto-Snapshots (Nightly WorkManager, prunes to keep latest 5 files)
 */
@Composable
fun BackupSyncCenterScreen(
    viewModel: OmniAiViewModel,
    onBack: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val settings by viewModel.appSettings.collectAsState()
    val vaultSnapshot by viewModel.vaultSnapshot.collectAsState()
    val isBackupBusy by viewModel.isBackupCenterBusy.collectAsState()
    val backupStatus by viewModel.backupCenterStatus.collectAsState()
    val localSnapshots by viewModel.localBackupSnapshots.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    var passphrase by remember { mutableStateOf(viewModel.vault.autoBackupPassphrase) }
    var selection by remember { mutableStateOf(BackupSelectionOptions()) }
    var migrationPasteInput by remember { mutableStateOf("") }
    var tgBotToken by remember(vaultSnapshot) { mutableStateOf(vaultSnapshot.telegramBotToken) }
    var tgChatId by remember(vaultSnapshot) { mutableStateOf(vaultSnapshot.telegramChatId) }

    LaunchedEffect(Unit) {
        viewModel.refreshLocalBackupSnapshots()
    }

    val exportSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.CreateDocument("application/octet-stream")
    ) { uri ->
        if (uri != null && passphrase.length >= 6) {
            viewModel.exportSelectiveBackupToUri(uri, passphrase, selection)
        }
    }

    val importSafLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null && passphrase.length >= 6) {
            viewModel.importSelectiveBackupFromUri(uri, passphrase)
        }
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(AmoledBlack)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                verticalAlignment = Alignment.CenterVertically
            ) {
                IconButton(onClick = onBack) {
                    Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                }
                Column {
                    Text("4-Layer Backup & Sync Center", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "PBKDF2-SHA256 (100k) + AES-256-GCM · Local · GitHub · Telegram",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
            }
        }

        if (isBackupBusy || backupStatus.isNotBlank()) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                    border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.6f))
                ) {
                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        if (isBackupBusy) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                        }
                        if (backupStatus.isNotBlank()) {
                            Text(backupStatus, style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                        }
                    }
                }
            }
        }

        // LAYER 1: SELECTIVE AES-256-GCM BACKUP ENGINE
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Layer 1: Selective AES-256-GCM Engine (.omniai.enc)", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    OutlinedTextField(
                        value = passphrase,
                        onValueChange = { passphrase = it },
                        label = { Text("Encryption Passphrase (min 6 chars)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = { viewModel.saveAutoBackupPassphrase(passphrase) },
                        enabled = passphrase.length >= 6,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Store Passphrase in Keystore (For Scheduled Backups)")
                    }

                    HorizontalDivider(color = AmoledBorder)
                    Text("Select Modules to Include", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    BackupCheckboxRow("API Vault & Kaggle Pool", selection.includeVaultAndKagglePool) {
                        selection = selection.copy(includeVaultAndKagglePool = it)
                    }
                    BackupCheckboxRow("AI Chat History & Memory", selection.includeChatsAndMemory) {
                        selection = selection.copy(includeChatsAndMemory = it)
                    }
                    BackupCheckboxRow("Storyboards & Character DNA", selection.includeStoryboardsAndCharacterDna) {
                        selection = selection.copy(includeStoryboardsAndCharacterDna = it)
                    }
                    BackupCheckboxRow("Video Scripts, Scenes & YT/IG/FB SEO Database", selection.includeContentScriptsAndSocialDb) {
                        selection = selection.copy(includeContentScriptsAndSocialDb = it)
                    }
                    BackupCheckboxRow("Niches, Auto-Next Commands & Series Planner", selection.includeNichesContinuityAndSeries) {
                        selection = selection.copy(includeNichesContinuityAndSeries = it)
                    }
                    BackupCheckboxRow("Projects, Kanban Tasks & Git Commit Logs", selection.includeProjectsAndGitLogs) {
                        selection = selection.copy(includeProjectsAndGitLogs = it)
                    }
                    BackupCheckboxRow("Prompt Chains & Settings", selection.includePromptChainsAndSettings) {
                        selection = selection.copy(includePromptChainsAndSettings = it)
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { exportSafLauncher.launch("OmniAI_v45_${System.currentTimeMillis()}.omniai.enc") },
                            enabled = !isBackupBusy && passphrase.length >= 6,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export File")
                        }
                        OutlinedButton(
                            onClick = { importSafLauncher.launch(arrayOf("*/*")) },
                            enabled = !isBackupBusy && passphrase.length >= 6,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import File")
                        }
                    }

                    HorizontalDivider(color = AmoledBorder)
                    Text("Encrypted Migration Text (Instant Device-to-Device)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    OutlinedButton(
                        onClick = {
                            viewModel.copyEncryptedMigrationText(passphrase, selection) { encryptedText ->
                                clipboard.setText(AnnotatedString(encryptedText))
                            }
                        },
                        enabled = !isBackupBusy && passphrase.length >= 6,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentCopy, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Copy Encrypted Migration Text")
                    }
                    OutlinedTextField(
                        value = migrationPasteInput,
                        onValueChange = { migrationPasteInput = it },
                        label = { Text("Paste OMNIAI_ENC_V25:... Migration Text") },
                        maxLines = 3,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.restoreFromEncryptedMigrationText(migrationPasteInput, passphrase)
                            migrationPasteInput = ""
                        },
                        enabled = !isBackupBusy && passphrase.length >= 6 && migrationPasteInput.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.ContentPaste, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Restore from Pasted Text")
                    }
                }
            }
        }

        // LAYER 2: ZERO-COST PRIVATE GITHUB CLOUD AUTO-SYNC
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Layer 2: Private GitHub Cloud Sync", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    Text(
                        "Repo: ${vaultSnapshot.githubRepo.ifBlank { "Configure owner/repo in Vault" }} -> backups/omniai_vault.enc",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        Button(
                            onClick = { viewModel.pushEncryptedBackupToGitHub(passphrase, selection) },
                            enabled = !isBackupBusy && passphrase.length >= 6,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CloudUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Push to GitHub")
                        }
                        OutlinedButton(
                            onClick = { viewModel.restoreLatestBackupFromGitHub(passphrase) },
                            enabled = !isBackupBusy && passphrase.length >= 6,
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.CloudDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Restore Latest")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Scheduled GitHub Auto-Backup (Wi-Fi Only)", style = MaterialTheme.typography.bodyMedium)
                            Text("Runs via WorkManager using stored keystore passphrase", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.githubAutoBackupEnabled,
                            onCheckedChange = { enabled ->
                                viewModel.setGitHubAutoBackupSchedule(enabled, settings.githubAutoBackupIntervalDays)
                            }
                        )
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = settings.githubAutoBackupIntervalDays == 1,
                            onClick = { viewModel.setGitHubAutoBackupSchedule(settings.githubAutoBackupEnabled, 1) },
                            label = { Text("Daily") }
                        )
                        FilterChip(
                            selected = settings.githubAutoBackupIntervalDays == 7,
                            onClick = { viewModel.setGitHubAutoBackupSchedule(settings.githubAutoBackupEnabled, 7) },
                            label = { Text("Weekly") }
                        )
                    }
                }
            }
        }

        // LAYER 3: TELEGRAM BOT CLOUD ARCHIVE
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Layer 3: Telegram Bot Cloud Archive", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    OutlinedTextField(
                        value = tgBotToken,
                        onValueChange = { tgBotToken = it },
                        label = { Text("TG_BOT_TOKEN (Encrypted in Vault)") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = tgChatId,
                        onValueChange = { tgChatId = it },
                        label = { Text("TG_CHAT_ID (Private Chat or Channel ID)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { viewModel.saveTelegramCredentials(tgBotToken, tgChatId) },
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Save Bot Keys")
                        }
                        Button(
                            onClick = {
                                viewModel.saveTelegramCredentials(tgBotToken, tgChatId)
                                viewModel.testTelegramBot()
                            },
                            enabled = !isBackupBusy && tgBotToken.isNotBlank() && tgChatId.isNotBlank(),
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Test Bot")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Auto-Archive Rendered Videos to Telegram", style = MaterialTheme.typography.bodyMedium)
                            Text("Uploads final_video.mp4 (<= 49 MB) + sends render alerts", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.telegramAutoArchiveVideos,
                            onCheckedChange = { viewModel.setTelegramAutoArchiveVideos(it) }
                        )
                    }

                    Button(
                        onClick = { viewModel.sendEncryptedSnapshotToTelegram(passphrase, selection) },
                        enabled = !isBackupBusy && passphrase.length >= 6 && tgBotToken.isNotBlank() && tgChatId.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Send, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Send Encrypted .omniai.enc Snapshot to Telegram")
                    }
                }
            }
        }

        // LAYER 4: ROLLING LOCAL AUTO-SNAPSHOTS
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Layer 4: Rolling Local Auto-Snapshots (Keep Latest 5)", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Nightly Rolling Local Snapshot", style = MaterialTheme.typography.bodyMedium)
                            Text("Saves encrypted .omniai.enc to ExternalFilesDir/Backups & prunes old files", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = settings.rollingLocalBackupEnabled,
                            onCheckedChange = { viewModel.setRollingLocalBackupEnabled(it) }
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.createRollingLocalSnapshotNow(passphrase, selection) },
                        enabled = !isBackupBusy && passphrase.length >= 6,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Create Local Snapshot Now (Prune > 5)")
                    }

                    if (localSnapshots.isEmpty()) {
                        Text("No local rolling snapshots written yet.", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    } else {
                        val fmt = remember { SimpleDateFormat("MMM dd HH:mm", Locale.US) }
                        localSnapshots.forEach { snap ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(snap.name, style = MaterialTheme.typography.labelMedium)
                                    Text(
                                        "${snap.sizeBytes / 1024} KB · ${fmt.format(Date(snap.lastModifiedMs))}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = TextSecondary
                                    )
                                }
                                OutlinedButton(
                                    onClick = { viewModel.restoreFromLocalSnapshot(snap.file, passphrase) },
                                    enabled = !isBackupBusy && passphrase.length >= 6
                                ) {
                                    Text("Restore")
                                }
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun BackupCheckboxRow(
    label: String,
    checked: Boolean,
    onCheckedChange: (Boolean) -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checked, onCheckedChange = onCheckedChange)
        Text(label, style = MaterialTheme.typography.bodyMedium)
    }
}
