package com.omniai.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.RestartAlt
import androidx.compose.material.icons.filled.Upload
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
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
import androidx.compose.ui.unit.dp
import com.omniai.studio.settings.AppFontFamily
import com.omniai.studio.settings.ChatBubbleStyle
import com.omniai.studio.settings.QuickFabAction
import com.omniai.studio.settings.StudioTabId
import com.omniai.studio.settings.UiConfigRepository
import com.omniai.studio.settings.UiDensity
import com.omniai.studio.settings.UiThemePreset
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.parseHexColorOrFallback
import com.omniai.studio.viewmodel.OmniAiViewModel
import kotlin.math.roundToInt

@Composable
fun UiThemeStudioScreen(
    viewModel: OmniAiViewModel,
    onBack: () -> Unit
) {
    val uiConfig by viewModel.uiConfig.collectAsState()
    val clipboard = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme

    var hexInput by remember(uiConfig.customAccentHex) { mutableStateOf(uiConfig.customAccentHex) }
    val parsedAccent = remember(uiConfig.customAccentHex) {
        parseHexColorOrFallback(uiConfig.customAccentHex, colorScheme.primary)
    }
    var redChannel by remember(uiConfig.customAccentHex) { mutableFloatStateOf(parsedAccent.red * 255f) }
    var greenChannel by remember(uiConfig.customAccentHex) { mutableFloatStateOf(parsedAccent.green * 255f) }
    var blueChannel by remember(uiConfig.customAccentHex) { mutableFloatStateOf(parsedAccent.blue * 255f) }

    var showImportJsonDialog by remember { mutableStateOf(false) }
    var importJsonText by remember { mutableStateOf("") }

    val wallpaperPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            viewModel.saveChatWallpaper(uri)
        }
    }

    fun applyRgbChannels(r: Float, g: Float, b: Float) {
        val hex = String.format("#%02X%02X%02X", r.roundToInt().coerceIn(0, 255), g.roundToInt().coerceIn(0, 255), b.roundToInt().coerceIn(0, 255))
        hexInput = hex
        viewModel.setCustomAccentHex(hex, enabled = true)
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background)
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        // Header
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
                    Text("UI Theme & Layout Studio", style = MaterialTheme.typography.headlineSmall)
                    Text(
                        "Live Presets · Hex Accent · Bubbles · Wallpaper · Dynamic Tabs",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }
                IconButton(onClick = { viewModel.resetUiCustomizationDefaults() }) {
                    Icon(Icons.Filled.RestartAlt, contentDescription = "Reset UI Defaults", tint = CrimsonDanger)
                }
            }
        }

        // 1. 6 Theme Presets
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        Icon(Icons.Filled.Palette, contentDescription = null, tint = colorScheme.primary)
                        Text("1. Theme Engine Presets (Live Reactive)", style = MaterialTheme.typography.titleMedium, color = colorScheme.primary)
                    }
                    UiThemePreset.entries.chunked(2).forEach { pair ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            pair.forEach { preset ->
                                val selected = uiConfig.themePreset == preset
                                Card(
                                    colors = CardDefaults.cardColors(
                                        containerColor = if (selected) colorScheme.primaryContainer else colorScheme.surfaceVariant
                                    ),
                                    border = BorderStroke(
                                        width = if (selected) 2.dp else 1.dp,
                                        color = if (selected) colorScheme.primary else colorScheme.outline
                                    ),
                                    modifier = Modifier
                                        .weight(1f)
                                        .clickable { viewModel.setThemePreset(preset) }
                                ) {
                                    Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                        Row(
                                            modifier = Modifier.fillMaxWidth(),
                                            horizontalArrangement = Arrangement.SpaceBetween,
                                            verticalAlignment = Alignment.CenterVertically
                                        ) {
                                            Text(preset.label, style = MaterialTheme.typography.titleMedium)
                                            if (selected) {
                                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = colorScheme.primary, modifier = Modifier.size(16.dp))
                                            }
                                        }
                                        Text(
                                            preset.tagline,
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colorScheme.outline)

                    // Live Custom Accent Color Picker (RGB Sliders + #RRGGBB Hex + Swatch)
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Custom Hex Accent Override", style = MaterialTheme.typography.titleMedium)
                            Text("Override primary accent across all screens live", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = uiConfig.useCustomAccent,
                            onCheckedChange = { viewModel.setUseCustomAccent(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(44.dp)
                                .clip(CircleShape)
                                .background(parseHexColorOrFallback(hexInput, colorScheme.primary))
                        )
                        OutlinedTextField(
                            value = hexInput,
                            onValueChange = { value ->
                                val filtered = value.uppercase().take(7)
                                hexInput = filtered
                                if (Regex("^#[0-9A-F]{6}$").matches(filtered)) {
                                    viewModel.setCustomAccentHex(filtered, enabled = true)
                                }
                            },
                            label = { Text("Hex Accent (#RRGGBB)") },
                            singleLine = true,
                            textStyle = MaterialTheme.typography.bodyMedium.copy(fontFamily = FontFamily.Monospace),
                            modifier = Modifier.weight(1f)
                        )
                    }

                    Text("R: ${redChannel.roundToInt()}  ·  G: ${greenChannel.roundToInt()}  ·  B: ${blueChannel.roundToInt()}", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    Slider(
                        value = redChannel,
                        onValueChange = { redChannel = it; applyRgbChannels(redChannel, greenChannel, blueChannel) },
                        valueRange = 0f..255f
                    )
                    Slider(
                        value = greenChannel,
                        onValueChange = { greenChannel = it; applyRgbChannels(redChannel, greenChannel, blueChannel) },
                        valueRange = 0f..255f
                    )
                    Slider(
                        value = blueChannel,
                        onValueChange = { blueChannel = it; applyRgbChannels(redChannel, greenChannel, blueChannel) },
                        valueRange = 0f..255f
                    )
                }
            }
        }

        // 2. Surface, Typography, Bubbles & Chat Wallpaper
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("2. Surface, Typography & Chat Styling", style = MaterialTheme.typography.titleMedium, color = colorScheme.primary)

                    Text("Chat Bubble Style", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        ChatBubbleStyle.entries.forEach { style ->
                            FilterChip(
                                selected = uiConfig.chatBubbleStyle == style,
                                onClick = { viewModel.setChatBubbleStyle(style) },
                                label = { Text(style.label, maxLines = 1) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Card Corner Radius", style = MaterialTheme.typography.bodyMedium)
                        Text("${uiConfig.cardCornerRadiusDp}dp", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    }
                    Slider(
                        value = uiConfig.cardCornerRadiusDp.toFloat(),
                        onValueChange = { viewModel.setCardCornerRadiusDp(it.roundToInt()) },
                        valueRange = 0f..24f,
                        steps = 23
                    )

                    Text("UI Density", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        UiDensity.entries.forEach { density ->
                            FilterChip(
                                selected = uiConfig.uiDensity == density,
                                onClick = { viewModel.setUiDensity(density) },
                                label = { Text(density.label) }
                            )
                        }
                    }

                    Text("App Font Family", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        AppFontFamily.entries.forEach { family ->
                            FilterChip(
                                selected = uiConfig.appFontFamily == family,
                                onClick = { viewModel.setAppFontFamily(family) },
                                label = { Text(family.label) }
                            )
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Terminal Font Size", style = MaterialTheme.typography.bodyMedium)
                        Text("${uiConfig.terminalFontSizeSp}sp", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    }
                    Slider(
                        value = uiConfig.terminalFontSizeSp.toFloat(),
                        onValueChange = { viewModel.setTerminalFontSizeSp(it.roundToInt()) },
                        valueRange = 10f..18f,
                        steps = 7
                    )

                    HorizontalDivider(color = colorScheme.outline)

                    Text("Custom Chat Background Wallpaper", style = MaterialTheme.typography.titleMedium)
                    Text(
                        text = if (uiConfig.chatWallpaperPath.isNotBlank()) "Wallpaper saved locally: filesDir/wallpaper.jpg" else "No wallpaper set (solid theme surface).",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                wallpaperPicker.launch(
                                    PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)
                                )
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Image, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Pick Wallpaper")
                        }
                        if (uiConfig.chatWallpaperPath.isNotBlank()) {
                            OutlinedButton(onClick = { viewModel.clearChatWallpaper() }) {
                                Icon(Icons.Filled.Delete, contentDescription = null, tint = CrimsonDanger)
                                Spacer(modifier = Modifier.width(4.dp))
                                Text("Clear")
                            }
                        }
                    }

                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                        Text("Wallpaper Dim Overlay Opacity", style = MaterialTheme.typography.bodyMedium)
                        Text("${(uiConfig.wallpaperDimOpacity * 100).roundToInt()}%", style = MaterialTheme.typography.labelMedium, color = colorScheme.primary)
                    }
                    Slider(
                        value = uiConfig.wallpaperDimOpacity,
                        onValueChange = { viewModel.setWallpaperDimOpacity(it) },
                        valueRange = 0.2f..0.95f
                    )
                }
            }
        }

        // 3. Layout, Navigation Customizer & Quick FAB
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("3. Bottom Navigation, Quick FAB & Interactions", style = MaterialTheme.typography.titleMedium, color = colorScheme.primary)
                    Text(
                        "Pin between ${UiConfigRepository.MIN_NAV_TABS} and ${UiConfigRepository.MAX_NAV_TABS} bottom tabs and reorder them. Unpinned tabs stay reachable in the TopAppBar menu.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )

                    val activeTabs = uiConfig.bottomNavTabs
                    val orderedAll = activeTabs + StudioTabId.entries.filterNot { it in activeTabs }
                    orderedAll.forEach { tab ->
                        val isActive = tab in activeTabs
                        val activeIndex = activeTabs.indexOf(tab)
                        Surface(
                            color = colorScheme.surfaceVariant,
                            border = BorderStroke(1.dp, colorScheme.outline),
                            shape = RoundedCornerShape(8.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Checkbox(
                                    checked = isActive,
                                    onCheckedChange = { viewModel.toggleBottomNavTab(tab) }
                                )
                                Text(
                                    text = tab.label,
                                    style = MaterialTheme.typography.bodyMedium,
                                    modifier = Modifier.weight(1f)
                                )
                                if (isActive) {
                                    IconButton(
                                        onClick = { viewModel.moveBottomNavTab(tab, -1) },
                                        enabled = activeIndex > 0
                                    ) {
                                        Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up")
                                    }
                                    IconButton(
                                        onClick = { viewModel.moveBottomNavTab(tab, +1) },
                                        enabled = activeIndex < activeTabs.lastIndex
                                    ) {
                                        Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down")
                                    }
                                }
                            }
                        }
                    }

                    HorizontalDivider(color = colorScheme.outline)

                    Text("Default Startup Tab", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    StudioTabId.entries.chunked(3).forEach { rowItems ->
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                            rowItems.forEach { tab ->
                                FilterChip(
                                    selected = uiConfig.defaultStartupTab == tab,
                                    onClick = { viewModel.setDefaultStartupTab(tab) },
                                    label = { Text(tab.label, maxLines = 1) },
                                    modifier = Modifier.weight(1f)
                                )
                            }
                        }
                    }

                    Text("Quick Floating Action Button (FAB)", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                    QuickFabAction.entries
                        .filterNot { it == QuickFabAction.QUICK_TERMINAL }
                        .chunked(3)
                        .forEach { rowItems ->
                            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                                rowItems.forEach { action ->
                                    FilterChip(
                                        selected = uiConfig.quickFabAction == action,
                                        onClick = { viewModel.setQuickFabAction(action) },
                                        label = { Text(action.label, maxLines = 1) },
                                        modifier = Modifier.weight(1f)
                                    )
                                }
                            }
                    }

                    HorizontalDivider(color = colorScheme.outline)

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Haptic Feedback", style = MaterialTheme.typography.bodyMedium)
                            Text("Tactile confirmation on sends, switches & terminal keys", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = uiConfig.hapticFeedbackEnabled,
                            onCheckedChange = { viewModel.setHapticFeedbackEnabled(it) }
                        )
                    }

                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Reduced Animations", style = MaterialTheme.typography.bodyMedium)
                            Text("Snappier instant UI transitions & lower battery usage", style = MaterialTheme.typography.labelMedium, color = colorScheme.onSurfaceVariant)
                        }
                        Switch(
                            checked = uiConfig.reducedAnimations,
                            onCheckedChange = { viewModel.setReducedAnimations(it) }
                        )
                    }
                }
            }
        }

        // 4. Theme JSON Export / Import
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = colorScheme.surface),
                border = BorderStroke(1.dp, colorScheme.outline)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text("4. Portable Theme JSON Export / Import", style = MaterialTheme.typography.titleMedium, color = colorScheme.primary)
                    Text(
                        "Share your exact OmniAI-Studio theme preset, custom hex accent, bubble style, and navigation layout as JSON.",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                    Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = {
                                val exported = viewModel.exportThemeConfigJson()
                                clipboard.setText(AnnotatedString(exported))
                                viewModel.postToast("Copied Theme Studio JSON to clipboard.")
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Export JSON")
                        }
                        OutlinedButton(
                            onClick = { showImportJsonDialog = true },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Upload, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Import JSON")
                        }
                    }
                }
            }
        }

        item { Spacer(modifier = Modifier.height(24.dp)) }
    }

    if (showImportJsonDialog) {
        AlertDialog(
            onDismissRequest = { showImportJsonDialog = false },
            containerColor = colorScheme.surface,
            title = { Text("Import Theme Config JSON") },
            text = {
                OutlinedTextField(
                    value = importJsonText,
                    onValueChange = { importJsonText = it },
                    label = { Text("Paste UiCustomizationConfig JSON") },
                    minLines = 5,
                    maxLines = 10,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        viewModel.importThemeConfigJson(importJsonText)
                        importJsonText = ""
                        showImportJsonDialog = false
                    },
                    enabled = importJsonText.isNotBlank()
                ) { Text("Apply Theme") }
            },
            dismissButton = {
                OutlinedButton(onClick = { showImportJsonDialog = false }) { Text("Cancel") }
            }
        )
    }
}
