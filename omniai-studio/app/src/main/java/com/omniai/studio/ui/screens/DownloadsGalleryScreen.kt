package com.omniai.studio.ui.screens

import android.content.Intent
import android.net.Uri
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.defaultMinSize
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CleaningServices
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.ExpandLess
import androidx.compose.material.icons.filled.ExpandMore
import androidx.compose.material.icons.filled.Image
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.SaveAlt
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Storage
import androidx.compose.material3.Button
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.viewinterop.AndroidView
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import androidx.media3.common.MediaItem
import androidx.media3.exoplayer.ExoPlayer
import androidx.media3.ui.PlayerView
import com.omniai.studio.data.remote.SocialMetadata
import com.omniai.studio.media.StorageGalleryHelper
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.OmniAccentBadge
import com.omniai.studio.ui.theme.OmniDesignTokens
import com.omniai.studio.ui.theme.OmniEmptyState
import com.omniai.studio.ui.theme.OmniOverflowAction
import com.omniai.studio.ui.theme.OmniOverflowMenuButton
import com.omniai.studio.ui.theme.OmniSectionHeader
import com.omniai.studio.ui.theme.OmniSurfaceCard
import com.omniai.studio.viewmodel.OmniAiViewModel
import java.io.File

@Composable
fun DownloadsGalleryScreen(
    viewModel: OmniAiViewModel,
    onOpenContentHub: () -> Unit = {},
    onOpenStudio: () -> Unit = {}
) {
    val context = LocalContext.current
    val clipboard = LocalClipboardManager.current
    val colorScheme = MaterialTheme.colorScheme
    val downloads by viewModel.downloads.collectAsState()
    val storage by viewModel.storageUsage.collectAsState()

    var playingFilePath by remember { mutableStateOf<String?>(null) }
    var expandedPublishingKitIds by remember { mutableStateOf<Set<Long>>(emptySet()) }

    LaunchedEffect(Unit) {
        viewModel.refreshStorageUsage()
    }

    LazyColumn(
        modifier = Modifier
            .fillMaxSize()
            .background(colorScheme.background),
        contentPadding = PaddingValues(
            horizontal = OmniDesignTokens.ScreenHorizontalPadding,
            vertical = OmniDesignTokens.SectionVerticalGap
        ),
        verticalArrangement = Arrangement.spacedBy(OmniDesignTokens.SectionVerticalGap)
    ) {
        item {
            OmniSectionHeader(
                title = "Offline Gallery & Downloads",
                subtitle = "Total: %.1f MB · Renders %.1f MB · Merged %.1f MB".format(
                    storage.totalMb,
                    storage.videosMb,
                    storage.mergedMb
                ),
                trailing = {
                    OutlinedButton(
                        onClick = onOpenContentHub,
                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                    ) {
                        Icon(Icons.Filled.Storage, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Content DB")
                    }
                }
            )
        }

        // Compact Storage & Cache Cleaner Card
        item {
            OmniSurfaceCard {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text("Storage & Cache Telemetry", style = MaterialTheme.typography.titleSmall)
                        Text(
                            text = "Cache: %.2f MB · Room DB: %.2f MB".format(storage.tempCacheMb, storage.dbMb),
                            style = MaterialTheme.typography.labelMedium,
                            color = colorScheme.onSurfaceVariant
                        )
                    }
                    OutlinedButton(
                        onClick = { viewModel.cleanTempCache() },
                        shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                    ) {
                        Icon(Icons.Filled.CleaningServices, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Clean Cache")
                    }
                }
            }
        }

        if (downloads.isEmpty()) {
            item {
                OmniEmptyState(
                    icon = Icons.Filled.MovieCreation,
                    title = "No rendered videos yet",
                    helperText = "Queue a Dual-GPU Kaggle render in Studio or merge clips in Media Studio.",
                    actionLabel = "Open Video Studio",
                    onActionClick = onOpenStudio
                )
            }
        } else {
            items(downloads, key = { it.id }) { item ->
                val sizeMb = item.sizeBytes.toDouble() / (1024.0 * 1024.0)
                OmniSurfaceCard {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = item.fileName,
                                style = MaterialTheme.typography.titleSmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = "${item.category} · %.2f MB".format(sizeMb),
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                        OmniAccentBadge(
                            text = item.status,
                            accent = if (item.status == "FAILED") CrimsonDanger else EmeraldAccent
                        )
                        OmniOverflowMenuButton(
                            actions = buildList {
                                if (item.status == "COMPLETED") {
                                    add(
                                        OmniOverflowAction(
                                            label = "Save to Public Gallery",
                                            icon = Icons.Filled.SaveAlt,
                                            onClick = { viewModel.saveDownloadToPublicGallery(item) }
                                        )
                                    )
                                    add(
                                        OmniOverflowAction(
                                            label = "Save HD Thumbnail",
                                            icon = Icons.Filled.Image,
                                            onClick = { viewModel.saveVideoThumbnail(item) }
                                        )
                                    )
                                }
                                add(
                                    OmniOverflowAction(
                                        label = "Delete Video",
                                        icon = Icons.Filled.Delete,
                                        isDestructive = true,
                                        onClick = { viewModel.deleteDownload(item) }
                                    )
                                )
                            }
                        )
                    }

                    if (item.status == "DOWNLOADING") {
                        LinearProgressIndicator(
                            progress = { (item.progressPercent / 100f).coerceIn(0f, 1f) },
                            modifier = Modifier.fillMaxWidth()
                        )
                    }

                    // Max 2 Primary Actions Visible per Card
                    Row(
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        if (item.status == "COMPLETED") {
                            Button(
                                onClick = {
                                    if (File(item.filePath).exists()) {
                                        playingFilePath = item.filePath
                                    } else {
                                        viewModel.postToast("File not found on disk.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                Icon(Icons.Filled.PlayArrow, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Preview")
                            }
                            OutlinedButton(
                                onClick = {
                                    val file = File(item.filePath)
                                    if (file.exists()) {
                                        val shareIntent = StorageGalleryHelper.createShareVideoIntent(context, file)
                                        context.startActivity(Intent.createChooser(shareIntent, "Share MP4"))
                                    } else {
                                        viewModel.postToast("Video missing on disk.")
                                    }
                                },
                                modifier = Modifier
                                    .weight(1f)
                                    .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                Icon(Icons.Filled.Share, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Share")
                            }
                        } else if (item.status == "FAILED") {
                            OutlinedButton(
                                onClick = { viewModel.retryDownload(item) },
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                                shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                            ) {
                                Icon(Icons.Filled.Refresh, contentDescription = null)
                                Spacer(modifier = Modifier.width(6.dp))
                                Text("Retry Download")
                            }
                        }
                    }

                    // Collapsible Publishing Kit (SEO & Copy)
                    if (item.status == "COMPLETED") {
                        val seoMeta = remember(item.socialMetadataJson, item.fileName) {
                            viewModel.parseSocialMetadata(item.socialMetadataJson)
                                ?: SocialMetadata(
                                    viralTitle = item.fileName.substringBeforeLast('.').replace('_', ' '),
                                    caption = "Rendered offline with OmniAI Studio Dual-GPU Wan 2.1 Director.",
                                    hashtags = listOf("#OmniAI", "#AIVideo", "#Shorts", "#Cinematic"),
                                    pinnedComment = "What visual concept should we render next? Drop your prompt!"
                                )
                        }
                        val isKitExpanded = expandedPublishingKitIds.contains(item.id)
                        HorizontalDivider(color = colorScheme.outline)
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Text("Publishing Kit (SEO & Copy)", style = MaterialTheme.typography.labelMedium, color = EmeraldAccent)
                            TextButton(onClick = {
                                expandedPublishingKitIds = if (isKitExpanded) {
                                    expandedPublishingKitIds - item.id
                                } else {
                                    expandedPublishingKitIds + item.id
                                }
                            }) {
                                Text(if (isKitExpanded) "Hide" else "Expand")
                                Icon(
                                    imageVector = if (isKitExpanded) Icons.Filled.ExpandLess else Icons.Filled.ExpandMore,
                                    contentDescription = null
                                )
                            }
                        }
                        if (isKitExpanded) {
                            Surface(
                                color = colorScheme.surfaceVariant,
                                border = BorderStroke(1.dp, colorScheme.outline),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Column(
                                    modifier = Modifier.padding(12.dp),
                                    verticalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    Text("Viral Title: ${seoMeta.viralTitle}", style = MaterialTheme.typography.bodyMedium)
                                    Text(
                                        text = "${seoMeta.caption}\n${seoMeta.hashtags.joinToString(" ")}",
                                        style = MaterialTheme.typography.labelMedium,
                                        color = colorScheme.onSurfaceVariant
                                    )
                                    if (seoMeta.pinnedComment.isNotBlank()) {
                                        Text(
                                            text = "Pinned Comment: ${seoMeta.pinnedComment}",
                                            style = MaterialTheme.typography.labelMedium,
                                            color = colorScheme.onSurfaceVariant
                                        )
                                    }
                                    Row(
                                        modifier = Modifier.fillMaxWidth(),
                                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                                    ) {
                                        OutlinedButton(
                                            onClick = {
                                                clipboard.setText(AnnotatedString(seoMeta.viralTitle))
                                                viewModel.postToast("Copied Viral Title.")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Title")
                                        }
                                        OutlinedButton(
                                            onClick = {
                                                val fullDesc = "${seoMeta.caption}\n\n${seoMeta.hashtags.joinToString(" ")}"
                                                clipboard.setText(AnnotatedString(fullDesc))
                                                viewModel.postToast("Copied Description + Hashtags.")
                                            },
                                            modifier = Modifier.weight(1f)
                                        ) {
                                            Icon(Icons.Filled.ContentCopy, contentDescription = null)
                                            Spacer(modifier = Modifier.width(4.dp))
                                            Text("Desc + Tags")
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

    playingFilePath?.let { path ->
        FullScreenExoPlayerDialog(
            videoPath = path,
            onDismiss = { playingFilePath = null }
        )
    }
}

@Composable
private fun FullScreenExoPlayerDialog(
    videoPath: String,
    onDismiss: () -> Unit
) {
    val context = LocalContext.current
    var playbackSpeed by remember { mutableFloatStateOf(1.0f) }
    val exoPlayer = remember(videoPath) {
        ExoPlayer.Builder(context).build().apply {
            setMediaItem(MediaItem.fromUri(Uri.fromFile(File(videoPath))))
            prepare()
            playWhenReady = true
        }
    }

    LaunchedEffect(playbackSpeed) {
        exoPlayer.setPlaybackSpeed(playbackSpeed)
    }

    DisposableEffect(exoPlayer) {
        onDispose {
            exoPlayer.release()
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Box(
            modifier = Modifier
                .fillMaxSize()
                .background(AmoledBlack)
        ) {
            AndroidView(
                factory = { ctx ->
                    PlayerView(ctx).apply {
                        player = exoPlayer
                        useController = true
                    }
                },
                modifier = Modifier.fillMaxSize()
            )
            Row(
                modifier = Modifier
                    .align(Alignment.TopStart)
                    .padding(12.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(0.5f, 1.0f, 1.25f, 1.5f, 2.0f).forEach { speed ->
                    FilterChip(
                        selected = playbackSpeed == speed,
                        onClick = { playbackSpeed = speed },
                        label = { Text("${speed}x") }
                    )
                }
            }
            IconButton(
                onClick = onDismiss,
                modifier = Modifier
                    .align(Alignment.TopEnd)
                    .padding(16.dp)
            ) {
                Icon(Icons.Filled.Close, contentDescription = "Close Player")
            }
        }
    }
}
