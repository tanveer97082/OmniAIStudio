package com.omniai.studio.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import android.net.Uri
import android.provider.OpenableColumns
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.itemsIndexed
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowDownward
import androidx.compose.material.icons.filled.ArrowUpward
import androidx.compose.material.icons.filled.Audiotrack
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.FolderOpen
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.MergeType
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Stop
import androidx.compose.material.icons.filled.Tune
import androidx.core.content.ContextCompat
import androidx.compose.material3.Button
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
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.AmoledSurfaceVariant
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.media.VideoCompressionPreset
import com.omniai.studio.viewmodel.MergeClipItem
import com.omniai.studio.viewmodel.OmniAiViewModel
import java.io.File
import java.util.UUID

@Composable
fun VideoMergerScreen(viewModel: OmniAiViewModel) {
    val context = LocalContext.current
    val mergeState by viewModel.mergeState.collectAsState()
    val compressState by viewModel.compressState.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val isRecordingVoiceover by viewModel.isRecordingVoiceover.collectAsState()
    val isExtractingAudio by viewModel.isExtractingAudio.collectAsState()
    val completedRenders = downloads.filter { it.status == "COMPLETED" }

    fun queryDisplayName(uri: Uri): String {
        context.contentResolver.query(uri, arrayOf(OpenableColumns.DISPLAY_NAME), null, null, null)?.use { cursor ->
            if (cursor.moveToFirst()) {
                val col = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (col >= 0) return cursor.getString(col)
            }
        }
        return uri.lastPathSegment ?: "clip.mp4"
    }

    val pickStorageVideosLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenMultipleDocuments()
    ) { uris ->
        if (uris.isNotEmpty()) {
            val items = uris.map { uri ->
                MergeClipItem(
                    id = UUID.randomUUID().toString(),
                    displayName = queryDisplayName(uri),
                    uri = uri
                )
            }
            viewModel.addMergeClips(items)
        }
    }

    val pickBgAudioLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.setBackgroundAudio(uri, queryDisplayName(uri))
        }
    }

    val extractAudioVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.extractAudioFromVideoUri(uri)
        }
    }

    val compressVideoLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            viewModel.compressVideoFromUri(uri)
        }
    }

    val recordAudioPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        if (granted) {
            viewModel.toggleVoiceoverRecording()
        } else {
            viewModel.postToast("RECORD_AUDIO permission is required to record voiceovers.")
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
            Text("On-Device Video Merger", style = MaterialTheme.typography.headlineSmall)
            Spacer(modifier = Modifier.height(4.dp))
            Text(
                "Hardware-accelerated Media3 Transformer stitching (H.264 + AAC). Saves directly to Movies/OmniAIStudio/.",
                style = MaterialTheme.typography.bodyMedium,
                color = TextSecondary
            )
        }

        // Source Pickers
        item {
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = { pickStorageVideosLauncher.launch(arrayOf("video/mp4")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.FolderOpen, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Pick MP4s from Phone")
                }

                OutlinedButton(
                    onClick = { pickBgAudioLauncher.launch(arrayOf("audio/mpeg", "audio/mp4", "audio/*")) },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.Audiotrack, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text("Add Music (.mp3/.m4a)")
                }
            }
            Spacer(modifier = Modifier.height(8.dp))
            // v2.0 Audio Studio: In-App Voiceover Recorder (.m4a) & Media3 Audio Extractor
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                OutlinedButton(
                    onClick = {
                        if (isRecordingVoiceover) {
                            viewModel.toggleVoiceoverRecording()
                        } else if (ContextCompat.checkSelfPermission(context, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED) {
                            viewModel.toggleVoiceoverRecording()
                        } else {
                            recordAudioPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        }
                    },
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(
                        imageVector = if (isRecordingVoiceover) Icons.Filled.Stop else Icons.Filled.Mic,
                        contentDescription = null,
                        tint = if (isRecordingVoiceover) CrimsonDanger else EmeraldAccent
                    )
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isRecordingVoiceover) "Stop Voiceover" else "Record Voiceover")
                }

                OutlinedButton(
                    onClick = { extractAudioVideoLauncher.launch(arrayOf("video/*")) },
                    enabled = !isExtractingAudio,
                    modifier = Modifier.weight(1f)
                ) {
                    Icon(Icons.Filled.GraphicEq, contentDescription = null)
                    Spacer(modifier = Modifier.width(6.dp))
                    Text(if (isExtractingAudio) "Extracting..." else "Extract Video Audio")
                }
            }
        }

        // Quick-Add from App Downloaded Renders
        if (completedRenders.isNotEmpty()) {
            item {
                Text("Add from Downloaded Renders", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
            }
            items(completedRenders.take(6), key = { it.id }) { dl ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                    border = BorderStroke(1.dp, AmoledBorder)
                ) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(dl.fileName, style = MaterialTheme.typography.bodyMedium)
                            Text(dl.category, style = MaterialTheme.typography.labelMedium)
                        }
                        OutlinedButton(onClick = {
                            val file = File(dl.filePath)
                            if (file.exists()) {
                                viewModel.addMergeClips(
                                    listOf(
                                        MergeClipItem(
                                            id = UUID.randomUUID().toString(),
                                            displayName = dl.fileName,
                                            uri = Uri.fromFile(file)
                                        )
                                    )
                                )
                            } else {
                                viewModel.postToast("File no longer exists on disk.")
                            }
                        }) {
                            Text("+ Queue")
                        }
                    }
                }
            }
        }

        // Ordered Clip Timeline
        item {
            HorizontalDivider(color = AmoledBorder)
            Spacer(modifier = Modifier.height(6.dp))
            Text("Merge Sequence (${mergeState.selectedClips.size} clips)", style = MaterialTheme.typography.titleMedium)
        }

        if (mergeState.selectedClips.isEmpty()) {
            item {
                Text(
                    "Select 2 or more MP4 clips above to build a merge timeline.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = TextSecondary
                )
            }
        } else {
            itemsIndexed(mergeState.selectedClips, key = { _, item -> item.id }) { idx, clip ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmoledSurfaceVariant),
                    border = BorderStroke(1.dp, AmoledBorder)
                ) {
                    Column(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(10.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            verticalAlignment = Alignment.CenterVertically,
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Column(modifier = Modifier.weight(1f)) {
                                Text("#${idx + 1}  ${clip.displayName}", style = MaterialTheme.typography.bodyMedium)
                            }
                            Row {
                                IconButton(onClick = { viewModel.moveMergeClip(idx, -1) }, enabled = idx > 0) {
                                    Icon(Icons.Filled.ArrowUpward, contentDescription = "Move Up")
                                }
                                IconButton(onClick = { viewModel.moveMergeClip(idx, 1) }, enabled = idx < mergeState.selectedClips.lastIndex) {
                                    Icon(Icons.Filled.ArrowDownward, contentDescription = "Move Down")
                                }
                                IconButton(onClick = { viewModel.removeMergeClip(idx) }) {
                                    Icon(Icons.Filled.Close, contentDescription = "Remove Clip", tint = CrimsonDanger)
                                }
                            }
                        }

                        // Per-clip Media3 ClippingConfiguration Trim Inputs (Start / End in seconds)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.spacedBy(8.dp)
                        ) {
                            OutlinedTextField(
                                value = clip.startSecText,
                                onValueChange = { viewModel.updateMergeClipTrim(idx, it, clip.endSecText) },
                                label = { Text("Start (sec)") },
                                placeholder = { Text("0.0") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                            OutlinedTextField(
                                value = clip.endSecText,
                                onValueChange = { viewModel.updateMergeClipTrim(idx, clip.startSecText, it) },
                                label = { Text("End (sec)") },
                                placeholder = { Text("Full") },
                                singleLine = true,
                                keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }

        // Audio & Output Settings
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
                        Text("Mute Original Clip Audio", style = MaterialTheme.typography.bodyMedium)
                        Switch(
                            checked = mergeState.muteOriginalAudio,
                            onCheckedChange = { viewModel.setMuteOriginalAudio(it) }
                        )
                    }

                    if (mergeState.backgroundAudioName != null) {
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text(
                                "BG Audio: ${mergeState.backgroundAudioName}",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldAccent
                            )
                            IconButton(onClick = { viewModel.setBackgroundAudio(null, null) }) {
                                Icon(Icons.Filled.Close, contentDescription = "Clear Audio")
                            }
                        }
                    }

                    OutlinedTextField(
                        value = mergeState.outputFileName,
                        onValueChange = { viewModel.setMergeOutputFileName(it) },
                        label = { Text("Output Filename (.mp4)") },
                        modifier = Modifier.fillMaxWidth(),
                        singleLine = true
                    )

                    if (mergeState.isMerging || mergeState.progressPercent > 0) {
                        LinearProgressIndicator(
                            progress = { (mergeState.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                        Text(
                            text = mergeState.lastStatus ?: "Progress: ${mergeState.progressPercent}%",
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldAccent
                        )
                    }

                    Button(
                        onClick = { viewModel.startOnDeviceMerge() },
                        enabled = mergeState.selectedClips.size >= 2 && !mergeState.isMerging,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.MergeType, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(if (mergeState.isMerging) "Merging (${mergeState.progressPercent}%)..." else "Merge Clips with Media3 Transformer")
                    }
                }
            }
        }

        // v2.5 On-Device Media3 Video Compressor (3 Bitrate/Resolution Presets + Before/After Comparison)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Compress Video (Media3 Transformer)", style = MaterialTheme.typography.titleMedium)
                    Text(
                        "Hardware H.264 re-encoding via DefaultEncoderFactory + VideoEncoderSettings. Zero external dependencies.",
                        style = MaterialTheme.typography.labelMedium,
                        color = TextSecondary
                    )

                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        VideoCompressionPreset.entries.forEach { preset ->
                            FilterChip(
                                selected = compressState.preset == preset,
                                onClick = { viewModel.setCompressionPreset(preset) },
                                label = { Text(preset.label) },
                                modifier = Modifier.fillMaxWidth()
                            )
                        }
                    }

                    if (compressState.isCompressing || compressState.progressPercent > 0) {
                        LinearProgressIndicator(
                            progress = { (compressState.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    if (compressState.beforeBytes > 0L && compressState.afterBytes > 0L) {
                        val beforeMb = compressState.beforeBytes / (1024.0 * 1024.0)
                        val afterMb = compressState.afterBytes / (1024.0 * 1024.0)
                        val savedPct = (100.0 - (afterMb / beforeMb.coerceAtLeast(0.01) * 100.0)).coerceAtLeast(0.0)
                        Text(
                            text = "Before: %.2f MB -> After: %.2f MB (Saved %.0f%%)".format(beforeMb, afterMb, savedPct),
                            style = MaterialTheme.typography.labelMedium,
                            color = EmeraldAccent
                        )
                    }

                    compressState.lastStatus?.let { status ->
                        Text(status, style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    }

                    OutlinedButton(
                        onClick = { compressVideoLauncher.launch(arrayOf("video/mp4", "video/*")) },
                        enabled = !compressState.isCompressing,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.Tune, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            if (compressState.isCompressing) {
                                "Compressing (${compressState.progressPercent}%)..."
                            } else {
                                "Pick Video & Compress"
                            }
                        )
                    }
                }
            }
        }
    }
}
