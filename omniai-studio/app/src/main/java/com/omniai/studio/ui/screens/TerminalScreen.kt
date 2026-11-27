package com.omniai.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Bookmark
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.NetworkCheck
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.hapticfeedback.HapticFeedbackType
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalHapticFeedback
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.text.withStyle
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.fragment.app.FragmentActivity
import com.omniai.studio.security.BiometricAuthManager
import com.omniai.studio.terminal.TerminalLine
import com.omniai.studio.terminal.TerminalLineType
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.CyanSecondary
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.LocalUiConfig
import com.omniai.studio.viewmodel.OmniAiViewModel

private val ANSI_PATTERN = Regex("\u001B\\[([0-9;]*)m")

private data class TerminalCommandSuggestion(
    val platformAliases: List<String>,
    val command: String
)

private val TERMINAL_COMMAND_SUGGESTIONS = listOf(
    TerminalCommandSuggestion(listOf("general"), "help"),
    TerminalCommandSuggestion(listOf("ai"), "ai "),
    TerminalCommandSuggestion(listOf("supabase"), "supabase schema"),
    TerminalCommandSuggestion(listOf("supabase"), "supabase keys push"),
    TerminalCommandSuggestion(listOf("supabase"), "supabase keys pull"),
    TerminalCommandSuggestion(listOf("supabase"), "supabase status"),
    TerminalCommandSuggestion(listOf("cloud"), "cloud status"),
    TerminalCommandSuggestion(listOf("cloud"), "cloud sync"),
    TerminalCommandSuggestion(listOf("cloud"), "cloud users"),
    TerminalCommandSuggestion(listOf("github", "git"), "git status"),
    TerminalCommandSuggestion(listOf("github", "git"), "git push "),
    TerminalCommandSuggestion(listOf("github", "git"), "git pull"),
    TerminalCommandSuggestion(listOf("github", "git"), "git actions"),
    TerminalCommandSuggestion(listOf("kaggle"), "kaggle status"),
    TerminalCommandSuggestion(listOf("huggingface", "hugging face", "hf"), "hf search "),
    TerminalCommandSuggestion(listOf("youtube", "yt"), "yt stats"),
    TerminalCommandSuggestion(listOf("youtube", "yt"), "yt upload "),
    TerminalCommandSuggestion(listOf("youtube", "yt"), "yt comments "),
    TerminalCommandSuggestion(listOf("youtube", "yt"), "yt compliance "),
    TerminalCommandSuggestion(listOf("database", "db"), "db stats"),
    TerminalCommandSuggestion(listOf("database", "db"), "db search "),
    TerminalCommandSuggestion(listOf("database", "db"), "db export csv"),
    TerminalCommandSuggestion(listOf("backup"), "backup now"),
    TerminalCommandSuggestion(listOf("system"), "sysinfo"),
    TerminalCommandSuggestion(listOf("network"), "netlog")
)

private fun terminalCommandSuggestions(input: String): List<TerminalCommandSuggestion> {
    val query = input.trim().lowercase()
    if (query.isBlank()) return emptyList()
    return TERMINAL_COMMAND_SUGGESTIONS.filter { suggestion ->
        suggestion.command.trimEnd().startsWith(query) ||
            suggestion.platformAliases.any { alias ->
                query == alias ||
                    query == "$alias " ||
                    (query.startsWith("$alias ") &&
                        suggestion.command.substringAfter(' ', "").startsWith(query.removePrefix("$alias ").trim()))
            }
    }.distinctBy { it.command }
}

@Composable
fun TerminalScreen(
    viewModel: OmniAiViewModel,
    activity: FragmentActivity,
    biometricManager: BiometricAuthManager
) {
    val uiConfig = LocalUiConfig.current
    val clipboard = LocalClipboardManager.current
    val haptics = LocalHapticFeedback.current

    val lines by viewModel.terminalLines.collectAsState()
    val vaultSnapshot by viewModel.vaultSnapshot.collectAsState()
    val cwdLabel by viewModel.terminalCwdLabel.collectAsState()
    val isRunning by viewModel.isTerminalRunning.collectAsState()
    val useTermuxBridge by viewModel.useTermuxBridge.collectAsState()
    val isTermuxInstalled by viewModel.isTermuxInstalled.collectAsState()
    val isNetlogEnabled by viewModel.isNetlogEnabled.collectAsState()
    val history by viewModel.terminalHistory.collectAsState()
    val snippets by viewModel.terminalSnippets.collectAsState()

    var commandInput by remember { mutableStateOf("") }
    var historyCursor by remember { mutableIntStateOf(-1) }
    var showSnippetsDialog by remember { mutableStateOf(false) }

    val listState = rememberLazyListState()
    val fontSizeSp = uiConfig.terminalFontSizeSp.coerceIn(10, 18).sp
    val lineHeightSp = (uiConfig.terminalFontSizeSp.coerceIn(10, 18) + 5).sp
    val commandSuggestions = remember(commandInput) { terminalCommandSuggestions(commandInput) }

    LaunchedEffect(Unit) {
        viewModel.refreshTermuxStatus()
    }

    LaunchedEffect(lines.size) {
        if (lines.isNotEmpty()) {
            listState.animateScrollToItem(lines.lastIndex)
        }
    }

    fun triggerHaptic() {
        if (uiConfig.hapticFeedbackEnabled) {
            haptics.performHapticFeedback(HapticFeedbackType.TextHandleMove)
        }
    }

    fun executeCommand(command: String, forceTermux: Boolean? = null) {
        val trimmed = command.trim()
        val requiresVaultVerification = vaultSnapshot.vaultLockEnabled &&
            (trimmed.equals("supabase keys push", ignoreCase = true) ||
                trimmed.equals("supabase keys pull", ignoreCase = true))
        if (requiresVaultVerification) {
            biometricManager.authenticate(
                activity = activity,
                title = "Vault Verification",
                subtitle = "Authenticate before syncing API credentials",
                onSuccess = { viewModel.executeTerminalCommand(command, forceTermux) },
                onFailure = viewModel::postToast
            )
        } else {
            viewModel.executeTerminalCommand(command, forceTermux)
        }
    }

    fun submitCommand() {
        val trimmed = commandInput.trim()
        if (trimmed.isBlank()) return
        triggerHaptic()
        executeCommand(trimmed)
        commandInput = ""
        historyCursor = -1
    }

    fun copyTerminalTranscript() {
        val transcript = viewModel.getTerminalTranscriptText()
        if (transcript.isBlank()) {
            viewModel.postToast("There is no terminal output to copy.")
            return
        }
        clipboard.setText(AnnotatedString(transcript))
        viewModel.postToast("Copied terminal transcript to clipboard.")
    }

    val colorScheme = MaterialTheme.colorScheme
    val terminalBg = if (uiConfig.themePreset.isLight) Color(0xFF0E141B) else Color(0xFF05070A)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(horizontal = uiConfig.uiDensity.cardPaddingDp.dp, vertical = 8.dp),
        verticalArrangement = Arrangement.spacedBy(8.dp)
    ) {
        // Header bar: Mode Toggle [Local Shell | Termux Bridge], Netlog indicator, Snippets Vault
        Surface(
            color = colorScheme.surface,
            border = BorderStroke(1.dp, colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
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
                        horizontalArrangement = Arrangement.spacedBy(6.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Icon(
                            Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = colorScheme.primary
                        )
                        Column {
                            Text(
                                "Hybrid Terminal & OmniCLI",
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (useTermuxBridge) {
                                    "Termux Bridge (${if (isTermuxInstalled) "Installed" else "Not Detected"})"
                                } else {
                                    "Local Sandbox · $cwdLabel"
                                },
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                        IconButton(
                            onClick = {
                                triggerHaptic()
                                copyTerminalTranscript()
                            },
                            enabled = lines.isNotEmpty()
                        ) {
                            Icon(
                                Icons.Filled.ContentCopy,
                                contentDescription = "Copy terminal output",
                                tint = colorScheme.primary
                            )
                        }
                        IconButton(onClick = {
                            triggerHaptic()
                            viewModel.toggleNetlogStreaming()
                        }) {
                            Icon(
                                Icons.Filled.NetworkCheck,
                                contentDescription = "Toggle Live Netlog",
                                tint = if (isNetlogEnabled) EmeraldAccent else colorScheme.onSurfaceVariant
                            )
                        }
                        IconButton(onClick = { showSnippetsDialog = true }) {
                            Icon(
                                Icons.Filled.Bookmark,
                                contentDescription = "Command Snippet Vault",
                                tint = colorScheme.primary
                            )
                        }
                    }
                }

                // Engine Toggle Row + Stop Process button
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    FilterChip(
                        selected = !useTermuxBridge,
                        onClick = {
                            triggerHaptic()
                            viewModel.setUseTermuxBridge(false)
                        },
                        label = { Text("Local Shell") }
                    )
                    FilterChip(
                        selected = useTermuxBridge,
                        onClick = {
                            triggerHaptic()
                            viewModel.setUseTermuxBridge(true)
                        },
                        label = { Text("Termux Bridge") }
                    )
                    Spacer(modifier = Modifier.weight(1f))
                    if (isRunning) {
                        Button(
                            onClick = {
                                triggerHaptic()
                                viewModel.stopTerminalProcess()
                            },
                            colors = ButtonDefaults.buttonColors(containerColor = CrimsonDanger)
                        ) {
                            Icon(Icons.Filled.Stop, contentDescription = null)
                            Spacer(modifier = Modifier.width(4.dp))
                            Text("CTRL+C")
                        }
                    } else if (isNetlogEnabled) {
                        Surface(
                            color = EmeraldAccent.copy(alpha = 0.14f),
                            shape = RoundedCornerShape(6.dp),
                            border = BorderStroke(1.dp, EmeraldAccent.copy(alpha = 0.5f))
                        ) {
                            Text(
                                "NETLOG ON",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldAccent,
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp)
                            )
                        }
                    }
                }
            }
        }

        // Terminal Output Viewport (Real-time streaming LazyColumn with ANSI tag highlighting)
        Surface(
            color = terminalBg,
            border = BorderStroke(1.dp, colorScheme.outline),
            shape = MaterialTheme.shapes.medium,
            modifier = Modifier
                .fillMaxWidth()
                .weight(1f)
        ) {
            LazyColumn(
                state = listState,
                modifier = Modifier
                    .fillMaxSize()
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalArrangement = Arrangement.spacedBy(3.dp)
            ) {
                items(lines, key = { it.id }) { line ->
                    Text(
                        text = formatTerminalLine(line, colorScheme.primary),
                        fontFamily = FontFamily.Monospace,
                        fontSize = fontSizeSp,
                        lineHeight = lineHeightSp
                    )
                }
            }
        }

        // Sticky Extra-Keys Helper Row (ESC, TAB, ↑, ↓, /, -, |, ~, CLEAR)
        val extraKeys = listOf("ESC", "TAB", "↑", "↓", "/", "-", "|", "~", "CLEAR")
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            extraKeys.forEach { keyLabel ->
                Surface(
                    color = colorScheme.surfaceVariant,
                    border = BorderStroke(1.dp, colorScheme.outline),
                    shape = RoundedCornerShape(6.dp),
                    modifier = Modifier.clickable {
                        triggerHaptic()
                        when (keyLabel) {
                            "ESC" -> {
                                if (isRunning) viewModel.stopTerminalProcess() else commandInput = ""
                            }
                            "TAB" -> {
                                val candidates = TERMINAL_COMMAND_SUGGESTIONS.map { it.command } + listOf("clear", "curl ")
                                val match = candidates.firstOrNull {
                                    it.startsWith(commandInput.trim(), ignoreCase = true)
                                }
                                commandInput = match ?: "$commandInput    "
                            }
                            "↑" -> {
                                if (history.isNotEmpty()) {
                                    val nextIdx = (historyCursor + 1).coerceAtMost(history.lastIndex)
                                    historyCursor = nextIdx
                                    commandInput = history[nextIdx]
                                }
                            }
                            "↓" -> {
                                if (historyCursor > 0) {
                                    historyCursor -= 1
                                    commandInput = history[historyCursor]
                                } else {
                                    historyCursor = -1
                                    commandInput = ""
                                }
                            }
                            "CLEAR" -> viewModel.clearTerminalOutput()
                            else -> {
                                commandInput += keyLabel
                            }
                        }
                    }
                ) {
                    Text(
                        text = keyLabel,
                        fontFamily = FontFamily.Monospace,
                        fontWeight = FontWeight.SemiBold,
                        fontSize = 11.sp,
                        color = if (keyLabel == "CLEAR") AmberWarning else colorScheme.onSurface,
                        modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp)
                    )
                }
            }
        }

        if (commandSuggestions.isNotEmpty()) {
            Column(verticalArrangement = Arrangement.spacedBy(2.dp)) {
                Text(
                    "Suggestions · tap to fill, then review before running",
                    style = MaterialTheme.typography.labelSmall,
                    color = colorScheme.onSurfaceVariant
                )
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    commandSuggestions.forEach { suggestion ->
                        FilterChip(
                            selected = false,
                            onClick = {
                                triggerHaptic()
                                commandInput = suggestion.command
                            },
                            label = {
                                Text(
                                    suggestion.command.trim(),
                                    fontFamily = FontFamily.Monospace,
                                    maxLines = 1
                                )
                            }
                        )
                    }
                }
            }
        }

        // Command Input Row
        Row(
            modifier = Modifier.fillMaxWidth(),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            OutlinedTextField(
                value = commandInput,
                onValueChange = { commandInput = it },
                placeholder = {
                    Text(
                        text = if (useTermuxBridge) "termux$ command..." else "omni$ help | ai <prompt> | sysinfo | netlog",
                        fontFamily = FontFamily.Monospace,
                        fontSize = 12.sp
                    )
                },
                singleLine = true,
                keyboardOptions = KeyboardOptions(imeAction = ImeAction.Send),
                keyboardActions = KeyboardActions(onSend = { submitCommand() }),
                textStyle = MaterialTheme.typography.bodyMedium.copy(
                    fontFamily = FontFamily.Monospace,
                    fontSize = fontSizeSp
                ),
                modifier = Modifier.weight(1f)
            )

            IconButton(
                onClick = { submitCommand() },
                enabled = commandInput.isNotBlank()
            ) {
                Icon(
                    Icons.AutoMirrored.Filled.Send,
                    contentDescription = "Execute Command",
                    tint = colorScheme.primary
                )
            }
        }
    }

    // Command Snippet Vault Dialog
    if (showSnippetsDialog) {
        var newSnippetName by remember { mutableStateOf("") }
        var newSnippetCmd by remember { mutableStateOf("") }
        var newSnippetTermux by remember { mutableStateOf(false) }

        AlertDialog(
            onDismissRequest = { showSnippetsDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Command Snippet Vault") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "One-tap commands stored in Room DB. Tap Run to execute immediately.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(220.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(snippets, key = { it.id }) { snippet ->
                            Card(
                                colors = CardDefaults.cardColors(containerColor = colorScheme.surfaceVariant),
                                border = BorderStroke(1.dp, colorScheme.outline)
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
                                            text = snippet.name,
                                            style = MaterialTheme.typography.titleMedium,
                                            modifier = Modifier.weight(1f)
                                        )
                                        Text(
                                            text = if (snippet.useTermuxBridge) "TERMUX" else "LOCAL",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = if (snippet.useTermuxBridge) AmberWarning else EmeraldAccent
                                        )
                                    }
                                    Text(
                                        text = snippet.command,
                                        fontFamily = FontFamily.Monospace,
                                        fontSize = 11.sp,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 2
                                    )
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.End
                                    ) {
                                        TextButton(onClick = {
                                            clipboard.setText(AnnotatedString(snippet.command))
                                            viewModel.postToast("Copied snippet command.")
                                        }) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                        }
                                        if (!snippet.isBuiltIn) {
                                            TextButton(onClick = { viewModel.deleteTerminalSnippet(snippet) }) {
                                                Icon(Icons.Filled.Delete, contentDescription = "Delete", tint = CrimsonDanger)
                                            }
                                        }
                                        Button(onClick = {
                                            showSnippetsDialog = false
                                            executeCommand(
                                                command = snippet.command,
                                                forceTermux = snippet.useTermuxBridge
                                            )
                                        }) {
                                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Run")
                                        }
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colorScheme.outline)
                    OutlinedTextField(
                        value = newSnippetName,
                        onValueChange = { newSnippetName = it },
                        label = { Text("New Snippet Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = newSnippetCmd,
                        onValueChange = { newSnippetCmd = it },
                        label = { Text("Shell or OmniCLI Command") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text("Run via Termux Bridge", style = MaterialTheme.typography.bodyMedium)
                        Switch(checked = newSnippetTermux, onCheckedChange = { newSnippetTermux = it })
                    }
                    Button(
                        onClick = {
                            viewModel.saveTerminalSnippet(newSnippetName, newSnippetCmd, newSnippetTermux)
                            newSnippetName = ""
                            newSnippetCmd = ""
                            newSnippetTermux = false
                        },
                        enabled = newSnippetName.isNotBlank() && newSnippetCmd.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Add, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Save Snippet")
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = { showSnippetsDialog = false }) { Text("Close") }
            }
        )
    }
}

private fun formatTerminalLine(line: TerminalLine, accentColor: Color): AnnotatedString {
    val defaultColor = when (line.type) {
        TerminalLineType.COMMAND -> accentColor
        TerminalLineType.STDOUT -> Color(0xFFE2E8F0)
        TerminalLineType.STDERR -> CrimsonDanger
        TerminalLineType.SYSTEM -> CyanSecondary
        TerminalLineType.AI_STREAM -> Color(0xFFB7FFD8)
        TerminalLineType.NETLOG -> AmberWarning
        TerminalLineType.SUCCESS -> EmeraldAccent
    }

    val raw = line.text
    if (!raw.contains("\u001B[")) {
        return buildAnnotatedString {
            withStyle(
                SpanStyle(
                    color = defaultColor,
                    fontWeight = if (line.type == TerminalLineType.COMMAND) FontWeight.Bold else FontWeight.Normal
                )
            ) {
                append(raw)
            }
        }
    }

    return buildAnnotatedString {
        var currentColor = defaultColor
        var isBold = false
        var lastIndex = 0
        ANSI_PATTERN.findAll(raw).forEach { match ->
            if (match.range.first > lastIndex) {
                withStyle(
                    SpanStyle(
                        color = currentColor,
                        fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                    )
                ) {
                    append(raw.substring(lastIndex, match.range.first))
                }
            }
            val codes = match.groupValues[1].split(";").mapNotNull { it.toIntOrNull() }
            if (codes.isEmpty() || 0 in codes) {
                currentColor = defaultColor
                isBold = false
            }
            codes.forEach { code ->
                when (code) {
                    1 -> isBold = true
                    31, 91 -> currentColor = CrimsonDanger
                    32, 92 -> currentColor = EmeraldAccent
                    33, 93 -> currentColor = AmberWarning
                    34, 94, 36, 96 -> currentColor = CyanSecondary
                    35, 95 -> currentColor = Color(0xFFBB86FC)
                    37, 97 -> currentColor = Color(0xFFF3F6FA)
                }
            }
            lastIndex = match.range.last + 1
        }
        if (lastIndex < raw.length) {
            withStyle(
                SpanStyle(
                    color = currentColor,
                    fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal
                )
            ) {
                append(raw.substring(lastIndex))
            }
        }
    }
}
