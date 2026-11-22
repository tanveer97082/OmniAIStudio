package com.omniai.studio.ui.screens

import android.content.Intent
import android.net.Uri
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
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.CloudSync
import androidx.compose.material.icons.filled.Code
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DeleteForever
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.OpenInBrowser
import androidx.compose.material.icons.filled.Radar
import androidx.compose.material.icons.filled.Save
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.SystemUpdate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
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
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
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
import androidx.fragment.app.FragmentActivity
import com.omniai.studio.BuildConfig
import com.omniai.studio.media.StorageGalleryHelper
import com.omniai.studio.security.AutoLockTimeout
import com.omniai.studio.security.BiometricAuthManager
import com.omniai.studio.security.VaultManager
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.viewmodel.OmniAiViewModel

private data class QuickProviderPreset(
    val label: String,
    val baseUrl: String,
    val modelId: String
)

private val QUICK_PROVIDER_PRESETS = listOf(
    QuickProviderPreset("Local Termux/Ollama (127.0.0.1:8080/v1)", "http://127.0.0.1:8080/v1", "llama3.1:8b"),
    QuickProviderPreset("Cloudflare Tunnel", "https://omniai-tunnel.trycloudflare.com/v1", "mistral-nemo"),
    QuickProviderPreset("Hugging Face Router", "https://router.huggingface.co/v1", "Qwen/Qwen2.5-72B-Instruct"),
    QuickProviderPreset("Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    QuickProviderPreset("OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat"),
    QuickProviderPreset("Gemini API", "https://generativelanguage.googleapis.com/v1beta/openai", "gemini-2.5-flash")
)

@Composable
fun VaultSettingsScreen(
    activity: FragmentActivity,
    biometricManager: BiometricAuthManager,
    viewModel: OmniAiViewModel,
    onOpenSettingsHub: () -> Unit = {},
    onOpenBackupCenter: () -> Unit = {}
) {
    val clipboard = LocalClipboardManager.current
    val snapshot by viewModel.vaultSnapshot.collectAsState()
    val pingResults by viewModel.pingResults.collectAsState()
    val isPinging by viewModel.isPinging.collectAsState()
    val otaState by viewModel.otaState.collectAsState()
    val localCandidates by viewModel.localServerCandidates.collectAsState()
    val isScanningLocalhost by viewModel.isScanningLocalhost.collectAsState()
    val kaggleAccounts by viewModel.kaggleAccounts.collectAsState()
    val activeKaggleAccountId by viewModel.activeKaggleAccountId.collectAsState()
    val tunnelWatchdogEnabled by viewModel.tunnelWatchdogEnabled.collectAsState()
    val remoteWorkerSource by viewModel.remoteWorkerSource.collectAsState()
    val isRemoteWorkerBusy by viewModel.isRemoteWorkerBusy.collectAsState()

    var draft by remember(snapshot) { mutableStateOf(snapshot) }
    var secretsUnlocked by remember { mutableStateOf(false) }
    var urlError by remember { mutableStateOf<String?>(null) }

    var poolLabel by remember { mutableStateOf("") }
    var poolUsername by remember { mutableStateOf("") }
    var poolApiKey by remember { mutableStateOf("") }
    var workerCommitMsg by remember { mutableStateOf("Update video_worker.py from OmniAI-Studio Android") }

    var backupPassword by remember { mutableStateOf("") }
    var pendingBackupMode by remember { mutableStateOf<String?>(null) } // "EXPORT" | "IMPORT"
    var showHfExplorer by remember { mutableStateOf(false) }
    var panicStep by remember { mutableStateOf(0) } // 0=closed, 1=first confirm, 2=final confirm

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

    fun requireBiometricThen(reason: String, onVerified: () -> Unit) {
        if (!snapshot.vaultLockEnabled) {
            onVerified()
            return
        }
        biometricManager.authenticate(
            activity = activity,
            title = "Vault Verification",
            subtitle = reason,
            onSuccess = onVerified,
            onFailure = { err -> viewModel.postToast(err) }
        )
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
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Column {
                    Text("Encrypted Vault & OTA", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "AES-256-GCM Hardware Keystore · Build v${BuildConfig.VERSION_NAME}",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                }
                OutlinedButton(onClick = {
                    if (secretsUnlocked) {
                        secretsUnlocked = false
                    } else {
                        requireBiometricThen("Verify to reveal & edit API credentials") {
                            secretsUnlocked = true
                        }
                    }
                }) {
                    Icon(
                        if (secretsUnlocked) Icons.Filled.VisibilityOff else Icons.Filled.Visibility,
                        contentDescription = null
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (secretsUnlocked) "Mask Keys" else "Reveal")
                }
            }
        }

        // Multi-Profile AI, Kaggle & GitHub Config
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("AI Provider Failover Chain", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(onClick = onOpenSettingsHub) {
                            Icon(Icons.Filled.Settings, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Settings Hub")
                        }
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Quick Provider Presets", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        OutlinedButton(
                            onClick = { viewModel.scanLocalhostPorts() },
                            enabled = !isScanningLocalhost
                        ) {
                            Icon(Icons.Filled.Radar, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isScanningLocalhost) "Scanning..." else "Scan Localhost")
                        }
                    }

                    if (localCandidates.isNotEmpty()) {
                        Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                            localCandidates.forEach { candidate ->
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween,
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = "${candidate.baseUrl} (${candidate.serviceHint})",
                                            style = MaterialTheme.typography.labelMedium
                                        )
                                        Text(
                                            text = if (candidate.reachable) {
                                                "ONLINE · ${candidate.latencyMs}ms" +
                                                    if (candidate.detectedModel.isNotBlank()) " · ${candidate.detectedModel}" else ""
                                            } else {
                                                "OFFLINE (1s probe)"
                                            },
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (candidate.reachable) EmeraldAccent else TextSecondary
                                        )
                                    }
                                    if (candidate.reachable) {
                                        OutlinedButton(onClick = {
                                            draft = draft.copy(
                                                primaryTunnelUrl = candidate.baseUrl,
                                                primaryModelName = candidate.detectedModel.ifBlank { draft.primaryModelName }
                                            )
                                            viewModel.applyDetectedLocalServer(candidate)
                                        }) {
                                            Text("Use")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        items(QUICK_PROVIDER_PRESETS) { preset ->
                            FilterChip(
                                selected = draft.primaryTunnelUrl.equals(preset.baseUrl, ignoreCase = true),
                                onClick = {
                                    draft = draft.copy(
                                        primaryTunnelUrl = preset.baseUrl,
                                        primaryModelName = preset.modelId
                                    )
                                    urlError = null
                                    viewModel.postToast("Applied preset: ${preset.label}")
                                },
                                label = { Text(preset.label, maxLines = 1) }
                            )
                        }
                    }

                    OutlinedTextField(
                        value = draft.primaryTunnelUrl,
                        onValueChange = { draft = draft.copy(primaryTunnelUrl = it); urlError = null },
                        label = { Text("Primary Uncensored Tunnel URL (HTTPS or localhost)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )
                    if (urlError != null) {
                        Text(urlError.orEmpty(), color = CrimsonDanger, style = MaterialTheme.typography.labelMedium)
                    }

                    SecretVaultField(
                        label = "Primary Tunnel API Key",
                        value = draft.primaryTunnelApiKey,
                        unlocked = secretsUnlocked,
                        onValueChange = { draft = draft.copy(primaryTunnelApiKey = it) },
                        onCopy = {
                            requireBiometricThen("Authenticate to copy Primary Tunnel Key") {
                                clipboard.setText(AnnotatedString(draft.primaryTunnelApiKey))
                                viewModel.postToast("Copied Primary Tunnel Key.")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = draft.primaryModelName,
                        onValueChange = { draft = draft.copy(primaryModelName = it) },
                        label = { Text("Primary Model ID") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    SecretVaultField(
                        label = "Secondary Hugging Face Token",
                        value = draft.huggingFaceToken,
                        unlocked = secretsUnlocked,
                        onValueChange = { draft = draft.copy(huggingFaceToken = it) },
                        onCopy = {
                            requireBiometricThen("Authenticate to copy Hugging Face Token") {
                                clipboard.setText(AnnotatedString(draft.huggingFaceToken))
                                viewModel.postToast("Copied Hugging Face Token.")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = draft.huggingFaceModel,
                        onValueChange = { draft = draft.copy(huggingFaceModel = it) },
                        label = { Text("Secondary Hugging Face Model") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    OutlinedButton(
                        onClick = { showHfExplorer = true },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Browse HF Models (Downloads · GGUF)")
                    }

                    SecretVaultField(
                        label = "Tertiary Gemini 2.5 Flash Key",
                        value = draft.geminiApiKey,
                        unlocked = secretsUnlocked,
                        onValueChange = { draft = draft.copy(geminiApiKey = it) },
                        onCopy = {
                            requireBiometricThen("Authenticate to copy Gemini API Key") {
                                clipboard.setText(AnnotatedString(draft.geminiApiKey))
                                viewModel.postToast("Copied Gemini API Key.")
                            }
                        }
                    )

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Auto-Fallback on Timeout / 429 / 5xx", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = draft.autoFallbackEnabled,
                            onCheckedChange = { draft = draft.copy(autoFallbackEnabled = it) }
                        )
                    }

                    HorizontalDivider(color = AmoledBorder)
                    Text("Kaggle Dual-GPU & GitHub OTA", style = MaterialTheme.typography.titleMedium)

                    OutlinedTextField(
                        value = draft.kaggleUsername,
                        onValueChange = { draft = draft.copy(kaggleUsername = it) },
                        label = { Text("Kaggle Username") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    SecretVaultField(
                        label = "Kaggle API Key",
                        value = draft.kaggleApiKey,
                        unlocked = secretsUnlocked,
                        onValueChange = { draft = draft.copy(kaggleApiKey = it) },
                        onCopy = {
                            requireBiometricThen("Authenticate to copy Kaggle Key") {
                                clipboard.setText(AnnotatedString(draft.kaggleApiKey))
                                viewModel.postToast("Copied Kaggle API Key.")
                            }
                        }
                    )

                    OutlinedTextField(
                        value = draft.githubRepo,
                        onValueChange = { draft = draft.copy(githubRepo = it) },
                        label = { Text("GitHub Repo (owner/OmniAI-Studio)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    SecretVaultField(
                        label = "GitHub PAT (Optional for Private Repos)",
                        value = draft.githubToken,
                        unlocked = secretsUnlocked,
                        onValueChange = { draft = draft.copy(githubToken = it) },
                        onCopy = {
                            requireBiometricThen("Authenticate to copy GitHub Token") {
                                clipboard.setText(AnnotatedString(draft.githubToken))
                                viewModel.postToast("Copied GitHub Token.")
                            }
                        }
                    )

                    Button(
                        onClick = {
                            urlError = viewModel.saveVaultConfiguration(draft)
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Save, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Encrypted Vault")
                    }
                }
            }
        }

        // Live Ping & Auth Connection Tester
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Live Connection & Auth Tester", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { viewModel.runConnectionPings() },
                            enabled = !isPinging
                        ) {
                            Icon(Icons.Filled.NetworkCheck, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isPinging) "Pinging..." else "Test All")
                        }
                    }

                    pingResults.forEach { res ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(res.name, style = MaterialTheme.typography.bodyMedium)
                            Text(
                                text = "${res.detail} · ${res.latencyMs} ms",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (res.reachable) EmeraldAccent else AmberWarning
                            )
                        }
                    }

                    HorizontalDivider(color = AmoledBorder)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Tunnel Health Watchdog (WorkManager)", style = MaterialTheme.typography.bodyMedium)
                            Text("Alerts if Primary Tunnel /models drops offline", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        }
                        Switch(
                            checked = tunnelWatchdogEnabled,
                            onCheckedChange = { viewModel.setTunnelWatchdogEnabled(it) }
                        )
                    }

                    HorizontalDivider(color = AmoledBorder)
                    Text("Quick-Launch Workspace Bookmarks", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        val bookmarks = listOf(
                            "Termux App" to "termux://",
                            "Cloudflare Zero Trust" to "https://one.dash.cloudflare.com",
                            "Kaggle Kernels" to "https://www.kaggle.com/code",
                            "GitHub Actions" to "https://github.com/${draft.githubRepo.ifBlank { "" }}/actions"
                        )
                        items(bookmarks) { (title, url) ->
                            OutlinedButton(onClick = {
                                if (url == "termux://") {
                                    val launchIntent = activity.packageManager.getLaunchIntentForPackage("com.termux")
                                    if (launchIntent != null) {
                                        activity.startActivity(launchIntent)
                                    } else {
                                        viewModel.postToast("Termux is not installed on this device.")
                                    }
                                } else {
                                    runCatching {
                                        activity.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(url)))
                                    }.onFailure {
                                        viewModel.postToast("No browser available to open $title.")
                                    }
                                }
                            }) {
                                Icon(Icons.Filled.OpenInBrowser, contentDescription = null)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text(title)
                            }
                        }
                    }
                }
            }
        }

        // v2.0 Multi-Account Kaggle GPU Pool (Auto-Rotates < 1.0 hr remaining)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("Multi-Account Kaggle Pool (Auto-Rotate < 1h)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Store multiple Kaggle accounts in EncryptedSharedPreferences. When weekly GPU quota drops below 1.0 hr, the render queue rotates automatically.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    kaggleAccounts.forEach { acct ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            FilterChip(
                                selected = acct.id == activeKaggleAccountId,
                                onClick = { viewModel.selectKaggleAccount(acct.id) },
                                label = { Text("${acct.label} (${acct.username})") }
                            )
                            IconButton(onClick = { viewModel.removeKaggleAccount(acct.id) }) {
                                Icon(Icons.Filled.Delete, contentDescription = "Remove Account", tint = CrimsonDanger)
                            }
                        }
                    }
                    OutlinedTextField(
                        value = poolLabel,
                        onValueChange = { poolLabel = it },
                        label = { Text("Account Label (e.g. Backup GPU #2)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = poolUsername,
                        onValueChange = { poolUsername = it },
                        label = { Text("Kaggle Username") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = poolApiKey,
                        onValueChange = { poolApiKey = it },
                        label = { Text("Kaggle API Key") },
                        visualTransformation = PasswordVisualTransformation(),
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedButton(
                        onClick = {
                            viewModel.saveKaggleAccount(
                                label = poolLabel,
                                username = poolUsername,
                                apiKey = poolApiKey,
                                datasetSlug = draft.kaggleDatasetSlug,
                                kernelSlug = draft.kaggleKernelSlug
                            )
                            poolLabel = ""
                            poolUsername = ""
                            poolApiKey = ""
                        },
                        enabled = poolUsername.isNotBlank() && poolApiKey.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Add Kaggle Account to Pool")
                    }
                }
            }
        }

        // v2.0 In-App GitHub Remote Worker Editor (GET / PUT kaggle_worker/video_worker.py)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("GitHub Remote Worker Editor", style = MaterialTheme.typography.titleMedium)
                        OutlinedButton(
                            onClick = { viewModel.fetchRemoteKaggleWorkerScript() },
                            enabled = !isRemoteWorkerBusy
                        ) {
                            Icon(Icons.Filled.Code, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text(if (isRemoteWorkerBusy) "Syncing..." else "Fetch Worker")
                        }
                    }
                    Text(
                        "Edit kaggle_worker/video_worker.py directly on GitHub via Contents REST API and trigger sync-kaggle-worker.yml.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )
                    if (remoteWorkerSource.isNotBlank()) {
                        OutlinedTextField(
                            value = remoteWorkerSource,
                            onValueChange = { viewModel.updateRemoteWorkerDraft(it) },
                            label = { Text("kaggle_worker/video_worker.py") },
                            minLines = 6,
                            maxLines = 12,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = workerCommitMsg,
                            onValueChange = { workerCommitMsg = it },
                            label = { Text("Git Commit Message") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = { viewModel.commitRemoteKaggleWorkerScript(workerCommitMsg) },
                            enabled = !isRemoteWorkerBusy,
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Save, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Commit & Trigger Kaggle Sync")
                        }
                    }
                }
            }
        }

        // GitHub OTA Updater
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("In-App GitHub OTA Updater", style = MaterialTheme.typography.titleMedium)
                    Text(
                        otaState.message ?: "Installed version: v${BuildConfig.VERSION_NAME} (code ${BuildConfig.VERSION_CODE})",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
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
                            Text("Check Release")
                        }
                        if (otaState.latestRelease != null) {
                            Button(
                                onClick = {
                                    viewModel.downloadLatestOtaApk { apkFile ->
                                        val installIntent = StorageGalleryHelper.createInstallApkIntent(activity, apkFile)
                                        activity.startActivity(installIntent)
                                    }
                                },
                                enabled = !otaState.isDownloading,
                                modifier = Modifier.weight(1f)
                            ) {
                                Text("Install ${otaState.latestRelease?.tagName}")
                            }
                        }
                    }
                }
            }
        }

        // Hardware Security Controls & Encrypted Backup
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Security & Privacy Shield", style = MaterialTheme.typography.titleMedium)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Privacy Screen Shield (FLAG_SECURE)", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = snapshot.flagSecureEnabled,
                            onCheckedChange = { viewModel.setFlagSecure(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Biometric / PIN App Lock", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = snapshot.appLockEnabled,
                            onCheckedChange = { viewModel.setAppLockEnabled(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Require Biometric for Vault Reveal/Copy", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = snapshot.vaultLockEnabled,
                            onCheckedChange = { viewModel.setVaultLockEnabled(it) }
                        )
                    }

                    Text("Auto-Lock Timeout", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        AutoLockTimeout.entries.forEach { timeout ->
                            FilterChip(
                                selected = snapshot.autoLockTimeout == timeout.name,
                                onClick = { viewModel.setAutoLockTimeout(timeout) },
                                label = { Text(timeout.label) }
                            )
                        }
                    }

                    HorizontalDivider(color = AmoledBorder)
                    Text("4-Layer Encrypted Backup & Cloud Sync", style = MaterialTheme.typography.titleMedium)
                    Button(
                        onClick = onOpenBackupCenter,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.CloudSync, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Open Backup & Sync Center (GitHub · Telegram · Local)")
                    }
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                        OutlinedButton(
                            onClick = { pendingBackupMode = "EXPORT" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileUpload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Export .omniai")
                        }
                        OutlinedButton(
                            onClick = { pendingBackupMode = "IMPORT" },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.FileDownload, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("Import .omniai")
                        }
                    }

                    HorizontalDivider(color = AmoledBorder)
                    Button(
                        onClick = { panicStep = 1 },
                        colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.DeleteForever, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Panic Wipe Vault, DB & Cache")
                    }
                }
            }
        }
    }

    if (showHfExplorer) {
        HfExplorerSheet(
            viewModel = viewModel,
            onSelectActiveModel = { modelId ->
                draft = draft.copy(huggingFaceModel = modelId)
                viewModel.setActiveHuggingFaceModel(modelId)
            },
            onDismiss = { showHfExplorer = false }
        )
    }

    if (pendingBackupMode != null) {
        val isExport = pendingBackupMode == "EXPORT"
        AlertDialog(
            onDismissRequest = { pendingBackupMode = null },
            containerColor = AmoledSurface,
            title = { Text(if (isExport) "Encrypt .omniai Backup" else "Decrypt .omniai Backup") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Enter a passphrase (min 6 chars) for PBKDF2-SHA256 + AES-256-GCM key derivation.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = TextSecondary
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
                ) {
                    Text("Continue")
                }
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
            containerColor = AmoledSurface,
            title = { Text("Confirm Panic Wipe (1/2)") },
            text = {
                Text("This permanently shreds EncryptedSharedPreferences, wipes all Room chat threads, presets, storyboards, and deletes all cached local renders.")
            },
            confirmButton = {
                Button(
                    onClick = { panicStep = 2 },
                    colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                ) { Text("Proceed to Final Step") }
            },
            dismissButton = {
                OutlinedButton(onClick = { panicStep = 0 }) { Text("Cancel") }
            }
        )
    }

    if (panicStep == 2) {
        AlertDialog(
            onDismissRequest = { panicStep = 0 },
            containerColor = AmoledSurface,
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
}

@Composable
private fun SecretVaultField(
    label: String,
    value: String,
    unlocked: Boolean,
    onValueChange: (String) -> Unit,
    onCopy: () -> Unit
) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.CenterVertically
    ) {
        OutlinedTextField(
            value = if (unlocked) value else VaultManager.maskSecret(value),
            onValueChange = { if (unlocked) onValueChange(it) },
            readOnly = !unlocked,
            label = { Text(label) },
            modifier = Modifier.weight(1f),
            singleLine = true
        )
        IconButton(onClick = onCopy, enabled = value.isNotBlank()) {
            Icon(Icons.Filled.ContentCopy, contentDescription = "Copy $label")
        }
    }
}
