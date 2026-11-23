package com.omniai.studio.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Compress
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Layers
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.CharacterProfileEntity
import com.omniai.studio.data.local.HashtagPackEntity
import com.omniai.studio.data.local.NicheEntity
import com.omniai.studio.data.remote.NicheBlueprintDto
import com.omniai.studio.data.remote.YouTubeChannelProfile
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary

/**
 * Compact interactive Master Niche Selector Pill for Tab 1 (Chat), Tab 2 (Video Director),
 * and Tab 4 (Content Hub). Supports rapid niche switching, "All Niches" filter toggle,
 * editing the active niche, and launching the Custom Niche creator.
 */
@Composable
fun NicheSelectorPill(
    niches: List<NicheEntity>,
    activeNiche: NicheEntity?,
    filterAllNiches: Boolean,
    showAllNichesToggle: Boolean = true,
    onSelectNiche: (Long) -> Unit,
    onToggleAllNiches: (Boolean) -> Unit,
    onCreateNicheClick: () -> Unit,
    onEditActiveNicheClick: (NicheEntity) -> Unit,
    modifier: Modifier = Modifier
) {
    var expanded by remember { mutableStateOf(false) }
    val primaryColor = MaterialTheme.colorScheme.primary

    Box(modifier = modifier) {
        Surface(
            onClick = { expanded = true },
            shape = RoundedCornerShape(10.dp),
            color = AmoledSurface,
            border = BorderStroke(
                1.dp,
                if (filterAllNiches && showAllNichesToggle) AmoledBorder else primaryColor.copy(alpha = 0.65f)
            )
        ) {
            Row(
                modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                val label = when {
                    filterAllNiches && showAllNichesToggle -> "All Niches"
                    activeNiche != null -> "${activeNiche.emoji} ${activeNiche.name}"
                    else -> "Select Niche"
                }
                Text(
                    text = label,
                    style = MaterialTheme.typography.labelMedium,
                    fontWeight = FontWeight.SemiBold,
                    color = if (filterAllNiches && showAllNichesToggle) TextSecondary else primaryColor,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis
                )
                if (activeNiche?.autoNextEnabled == true && !(filterAllNiches && showAllNichesToggle)) {
                    Text(
                        text = "AUTO",
                        style = MaterialTheme.typography.labelSmall,
                        color = EmeraldAccent,
                        fontWeight = FontWeight.Bold
                    )
                }
                Icon(
                    imageVector = Icons.Filled.ExpandMore,
                    contentDescription = "Switch Niche",
                    tint = TextSecondary,
                    modifier = Modifier.size(16.dp)
                )
            }
        }

        DropdownMenu(
            expanded = expanded,
            onDismissRequest = { expanded = false }
        ) {
            if (showAllNichesToggle) {
                DropdownMenuItem(
                    text = {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.Layers, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(8.dp))
                            Text("All Niches (Unfiltered View)")
                        }
                    },
                    trailingIcon = {
                        if (filterAllNiches) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = EmeraldAccent)
                        }
                    },
                    onClick = {
                        expanded = false
                        onToggleAllNiches(!filterAllNiches)
                    }
                )
                HorizontalDivider(color = AmoledBorder)
            }

            niches.forEach { niche ->
                val isSelected = !filterAllNiches && niche.id == activeNiche?.id
                DropdownMenuItem(
                    text = {
                        Column {
                            Text(
                                "${niche.emoji} ${niche.name}",
                                fontWeight = if (isSelected) FontWeight.Bold else FontWeight.Normal
                            )
                            Text(
                                "${niche.visualStylePreset} · ${niche.defaultAspectRatio} · Ep ${niche.episodeCount}",
                                style = MaterialTheme.typography.labelSmall,
                                color = TextSecondary
                            )
                        }
                    },
                    trailingIcon = {
                        if (isSelected) {
                            Icon(Icons.Filled.Check, contentDescription = null, tint = primaryColor)
                        }
                    },
                    onClick = {
                        expanded = false
                        onToggleAllNiches(false)
                        onSelectNiche(niche.id)
                    }
                )
            }

            HorizontalDivider(color = AmoledBorder)
            if (activeNiche != null) {
                DropdownMenuItem(
                    text = { Text("Edit Active Niche (${activeNiche.name})") },
                    leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null, modifier = Modifier.size(16.dp)) },
                    onClick = {
                        expanded = false
                        onEditActiveNicheClick(activeNiche)
                    }
                )
            }
            DropdownMenuItem(
                text = { Text("Create Custom Niche", color = primaryColor, fontWeight = FontWeight.SemiBold) },
                leadingIcon = { Icon(Icons.Filled.Add, contentDescription = null, tint = primaryColor, modifier = Modifier.size(16.dp)) },
                onClick = {
                    expanded = false
                    onCreateNicheClick()
                }
            )
        }
    }
}

/**
 * Full Create / Edit Custom Niche dialog with 1-tap "AI Auto-Fill Niche Blueprint",
 * Character DNA & Hashtag Pack linking, Auto-Next toggle, and Rolling Memory inspection.
 */
@Composable
fun NicheEditorDialog(
    initialNiche: NicheEntity?,
    characters: List<CharacterProfileEntity>,
    hashtagPacks: List<HashtagPackEntity>,
    isAutoFilling: Boolean,
    onAutoFillBlueprint: (nicheName: String, onResult: (NicheBlueprintDto) -> Unit) -> Unit,
    onCompressMemoryNow: ((Long) -> Unit)? = null,
    onSave: (NicheEntity) -> Unit,
    onDelete: ((NicheEntity) -> Unit)? = null,
    onDismiss: () -> Unit,
    youtubeProfiles: List<YouTubeChannelProfile> = emptyList()
) {
    var name by remember(initialNiche) { mutableStateOf(initialNiche?.name.orEmpty()) }
    var emoji by remember(initialNiche) { mutableStateOf(initialNiche?.emoji ?: "🎬") }
    var description by remember(initialNiche) { mutableStateOf(initialNiche?.description.orEmpty()) }
    var systemRules by remember(initialNiche) { mutableStateOf(initialNiche?.systemPromptRules.orEmpty()) }
    var visualStyle by remember(initialNiche) { mutableStateOf(initialNiche?.visualStylePreset ?: "Cinematic Realism") }
    var defaultVoice by remember(initialNiche) { mutableStateOf(initialNiche?.defaultVoiceId ?: "English Male") }
    var aspectRatio by remember(initialNiche) { mutableStateOf(initialNiche?.defaultAspectRatio ?: "9:16") }
    var linkedCharacterId by remember(initialNiche) { mutableStateOf(initialNiche?.linkedCharacterId) }
    var linkedHashtagPackId by remember(initialNiche) { mutableStateOf(initialNiche?.linkedHashtagPackId) }
    var linkedYoutubeProfileId by remember(initialNiche) { mutableStateOf(initialNiche?.linkedYoutubeProfileId.orEmpty()) }
    var autoNextEnabled by remember(initialNiche) { mutableStateOf(initialNiche?.autoNextEnabled ?: false) }

    val styleOptions = listOf(
        "Cinematic Realism",
        "3D Pixar Animated",
        "Cyberpunk Neon Noir",
        "Sci-Fi Volumetric Cinema",
        "Anime Cel-Shaded",
        "Clean Minimalist Editorial"
    )
    val voiceOptions = listOf("English Male", "English Female", "Hindi Male", "Hindi Female")
    val ratioOptions = listOf("9:16", "16:9", "1:1")

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Text(if (initialNiche == null) "Create Custom Niche" else "Edit Niche: ${initialNiche.name}")
        },
        text = {
            Column(
                modifier = Modifier
                    .fillMaxWidth()
                    .heightIn(max = 480.dp)
                    .verticalScroll(rememberScrollState()),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    OutlinedTextField(
                        value = emoji,
                        onValueChange = { emoji = it.take(4) },
                        label = { Text("Icon") },
                        singleLine = true,
                        modifier = Modifier.width(74.dp)
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Niche Name") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                }

                OutlinedButton(
                    onClick = {
                        if (name.isNotBlank()) {
                            onAutoFillBlueprint(name) { bp ->
                                emoji = bp.emoji.ifBlank { emoji }
                                description = bp.description.ifBlank { description }
                                systemRules = bp.systemPromptRules.ifBlank { systemRules }
                                visualStyle = bp.visualStylePreset.ifBlank { visualStyle }
                                defaultVoice = bp.defaultVoiceId.ifBlank { defaultVoice }
                                aspectRatio = bp.defaultAspectRatio.ifBlank { aspectRatio }
                            }
                        }
                    },
                    enabled = name.isNotBlank() && !isAutoFilling,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    if (isAutoFilling) {
                        CircularProgressIndicator(modifier = Modifier.size(16.dp), strokeWidth = 2.dp)
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("Generating Niche Blueprint...")
                    } else {
                        Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(modifier = Modifier.width(8.dp))
                        Text("AI Auto-Fill Niche Blueprint")
                    }
                }

                OutlinedTextField(
                    value = description,
                    onValueChange = { description = it },
                    label = { Text("Description") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )

                OutlinedTextField(
                    value = systemRules,
                    onValueChange = { systemRules = it },
                    label = { Text("System Prompt & Style Rules (Injected into Chat + Video)") },
                    minLines = 3,
                    maxLines = 5,
                    modifier = Modifier.fillMaxWidth()
                )

                Text("Visual Style Preset", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    styleOptions.forEach { st ->
                        FilterChip(
                            selected = visualStyle == st,
                            onClick = { visualStyle = st },
                            label = { Text(st) }
                        )
                    }
                }

                Text("Default Voice", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    voiceOptions.forEach { v ->
                        FilterChip(
                            selected = defaultVoice == v,
                            onClick = { defaultVoice = v },
                            label = { Text(v) }
                        )
                    }
                }

                Text("Default Aspect Ratio", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    ratioOptions.forEach { r ->
                        FilterChip(
                            selected = aspectRatio == r,
                            onClick = { aspectRatio = r },
                            label = { Text(r) }
                        )
                    }
                }

                if (characters.isNotEmpty()) {
                    Text("Linked Character DNA", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = linkedCharacterId == null,
                            onClick = { linkedCharacterId = null },
                            label = { Text("None") }
                        )
                        characters.forEach { ch ->
                            FilterChip(
                                selected = linkedCharacterId == ch.id,
                                onClick = { linkedCharacterId = ch.id },
                                label = { Text(ch.name) }
                            )
                        }
                    }
                }

                if (hashtagPacks.isNotEmpty()) {
                    Text("Linked Hashtag Pack", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = linkedHashtagPackId == null,
                            onClick = { linkedHashtagPackId = null },
                            label = { Text("None") }
                        )
                        hashtagPacks.forEach { pack ->
                            FilterChip(
                                selected = linkedHashtagPackId == pack.id,
                                onClick = { linkedHashtagPackId = pack.id },
                                label = { Text(pack.packName) }
                            )
                        }
                    }
                }

                if (youtubeProfiles.isNotEmpty()) {
                    Text("Linked YouTube Channel Profile (Multi-Channel)", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        FilterChip(
                            selected = linkedYoutubeProfileId.isBlank(),
                            onClick = { linkedYoutubeProfileId = "" },
                            label = { Text("Default Active") }
                        )
                        youtubeProfiles.forEach { yt ->
                            FilterChip(
                                selected = linkedYoutubeProfileId == yt.id,
                                onClick = { linkedYoutubeProfileId = yt.id },
                                label = { Text(yt.label) }
                            )
                        }
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Hands-Free Auto-Next Countdown", style = MaterialTheme.typography.bodyMedium)
                        Text(
                            "Starts a 5s Cancel/Run countdown after Auto-Typing a next command",
                            style = MaterialTheme.typography.labelSmall,
                            color = TextSecondary
                        )
                    }
                    Switch(checked = autoNextEnabled, onCheckedChange = { autoNextEnabled = it })
                }

                if (initialNiche != null) {
                    HorizontalDivider(color = AmoledBorder)
                    Text(
                        "Rolling Niche Memory (${initialNiche.lastSummarizedMessageCount} msgs compressed · ${initialNiche.episodeCount} episodes)",
                        style = MaterialTheme.typography.labelMedium,
                        color = EmeraldAccent
                    )
                    Text(
                        initialNiche.rollingChatSummary.ifBlank { "No compressed niche memory yet — builds automatically as you chat and create scripts." },
                        style = MaterialTheme.typography.labelSmall,
                        color = TextSecondary
                    )
                    if (onCompressMemoryNow != null) {
                        OutlinedButton(
                            onClick = { onCompressMemoryNow(initialNiche.id) },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.Compress, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(modifier = Modifier.width(6.dp))
                            Text("Compress Niche Memory Now")
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val entity = (initialNiche ?: NicheEntity(name = name.trim())).copy(
                        name = name.trim(),
                        emoji = emoji.trim().ifBlank { "🎬" },
                        description = description.trim(),
                        systemPromptRules = systemRules.trim(),
                        visualStylePreset = visualStyle,
                        defaultVoiceId = defaultVoice,
                        defaultAspectRatio = aspectRatio,
                        linkedCharacterId = linkedCharacterId,
                        linkedHashtagPackId = linkedHashtagPackId,
                        linkedYoutubeProfileId = linkedYoutubeProfileId,
                        autoNextEnabled = autoNextEnabled
                    )
                    onSave(entity)
                },
                enabled = name.isNotBlank()
            ) {
                Text("Save Niche")
            }
        },
        dismissButton = {
            Row(horizontalArrangement = Arrangement.spacedBy(4.dp)) {
                if (initialNiche != null && initialNiche.id > 1L && onDelete != null) {
                    TextButton(onClick = { onDelete(initialNiche) }) {
                        Text("Delete", color = MaterialTheme.colorScheme.error)
                    }
                }
                TextButton(onClick = onDismiss) {
                    Text("Cancel")
                }
            }
        }
    )
}
