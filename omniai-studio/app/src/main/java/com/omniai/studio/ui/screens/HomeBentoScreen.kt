package com.omniai.studio.ui.screens

import androidx.compose.foundation.Canvas
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
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.ChatBubble
import androidx.compose.material.icons.filled.CloudUpload
import androidx.compose.material.icons.filled.FolderSpecial
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayCircle
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Terminal
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.remember
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.OmniAccentBadge
import com.omniai.studio.ui.theme.OmniDesignTokens
import com.omniai.studio.ui.theme.OmniEmptyState
import com.omniai.studio.ui.theme.OmniSectionHeader
import com.omniai.studio.ui.theme.OmniSurfaceCard
import com.omniai.studio.ui.theme.omniAccentBorder
import com.omniai.studio.ui.theme.omniAccentContainer
import com.omniai.studio.viewmodel.OmniAiViewModel

/**
 * v5.1 Bento Command Dashboard (Default Home Tab).
 * - Hero Card: Active Niche + Next Suggested Video Episode with 1-tap "Auto-Type & Create".
 * - 2-Column Row 1: Kaggle GPU Quota (circular progress ring X/30h) + Active Project (pending diff + 1-tap Push).
 * - 2-Column Row 2: Quick AI Voice + Open Terminal.
 * - Recent Activity Strip: Last 2 chats or rendered videos.
 */
@Composable
fun HomeBentoScreen(
    viewModel: OmniAiViewModel,
    onOpenChat: () -> Unit,
    onOpenQuickVoice: () -> Unit,
    onOpenStudio: () -> Unit,
    onOpenProjects: () -> Unit,
    onOpenTerminal: () -> Unit,
    onOpenAnalytics: () -> Unit,
    onOpenGallery: () -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val primaryColor = colorScheme.primary

    val activeNiche by viewModel.activeNiche.collectAsState()
    val nextCommands by viewModel.activeNicheNextCommands.collectAsState()
    val seriesEpisodes by viewModel.activeNicheSeriesEpisodes.collectAsState()
    val weeklyUsedGpuHours by viewModel.weeklyUsedGpuHours.collectAsState()
    val activeProject by viewModel.activeProject.collectAsState()
    val activeDiff by viewModel.activeProjectDiff.collectAsState()
    val isGitBusy by viewModel.isGitBusy.collectAsState()
    val chatThreads by viewModel.chatThreads.collectAsState()
    val downloads by viewModel.downloads.collectAsState()
    val renderJobs by viewModel.renderJobs.collectAsState()
    val ytStats by viewModel.youTubeChannelStats.collectAsState()
    val ytDailyQuota by viewModel.youTubeTodayQuotaUnits.collectAsState()
    val isRefreshingYt by viewModel.isRefreshingYouTubeStats.collectAsState()

    LaunchedEffect(activeProject?.id) {
        activeProject?.let { viewModel.refreshProjectWorkspace(it.id) }
    }

    val nextSuggestedCommand = remember(nextCommands) {
        nextCommands.firstOrNull { it.sourceType == "POST_VIDEO_SEQUEL" } ?: nextCommands.firstOrNull()
    }
    val nextPlannedEpisode = remember(seriesEpisodes) {
        seriesEpisodes.firstOrNull { it.status != "COMPLETED" } ?: seriesEpisodes.firstOrNull()
    }

    val heroTitle = remember(nextSuggestedCommand, nextPlannedEpisode, activeNiche) {
        when {
            nextSuggestedCommand != null -> nextSuggestedCommand.shortLabel
            nextPlannedEpisode != null -> "Ep #${nextPlannedEpisode.episodeNumber}: ${nextPlannedEpisode.title}"
            else -> "Episode #${(activeNiche?.episodeCount ?: 0) + 1} · Fresh Concept"
        }
    }
    val heroSubtitle = remember(nextSuggestedCommand, nextPlannedEpisode, activeNiche) {
        when {
            nextSuggestedCommand != null -> nextSuggestedCommand.fullPromptCommand
            nextPlannedEpisode != null -> nextPlannedEpisode.hookIdea.ifBlank { nextPlannedEpisode.premise }
            else -> activeNiche?.description?.ifBlank {
                "Auto-type a niche-tailored storyboard concept and render on Dual-GPU Kaggle."
            } ?: "Generate your next viral episode with Niche Context."
        }
    }

    val recentChats = remember(chatThreads) { chatThreads.take(2) }
    val recentVideos = remember(downloads) { downloads.filter { it.status == "COMPLETED" }.take(2) }
    val activeRenderCount = remember(renderJobs) {
        renderJobs.count { it.status in setOf("QUEUED", "PUSHED", "RENDERING", "DOWNLOADING") }
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
        // HERO CARD: Active Niche + Next Suggested Video Episode
        item {
            OmniSurfaceCard(
                borderColor = primaryColor.omniAccentBorder(),
                contentPadding = OmniDesignTokens.CardInnerPadding
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OmniAccentBadge(
                        text = "${activeNiche?.emoji ?: "🎬"} ${activeNiche?.name ?: "Active Niche"}",
                        accent = primaryColor
                    )
                    Text(
                        text = "Ep ${(activeNiche?.episodeCount ?: 0) + 1} Next",
                        style = MaterialTheme.typography.labelMedium,
                        color = colorScheme.onSurfaceVariant
                    )
                }

                Spacer(modifier = Modifier.height(2.dp))

                Text(
                    text = heroTitle,
                    style = MaterialTheme.typography.headlineSmall,
                    fontWeight = FontWeight.Bold,
                    color = colorScheme.onSurface,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Text(
                    text = heroSubtitle,
                    style = MaterialTheme.typography.bodyMedium,
                    color = colorScheme.onSurfaceVariant,
                    maxLines = 2,
                    overflow = TextOverflow.Ellipsis
                )

                Spacer(modifier = Modifier.height(4.dp))

                Button(
                    onClick = {
                        when {
                            nextSuggestedCommand != null -> {
                                viewModel.routeNextCommandToVideoDirector(nextSuggestedCommand)
                                onOpenStudio()
                            }
                            nextPlannedEpisode != null -> {
                                viewModel.loadSeriesEpisodeIntoDirector(nextPlannedEpisode)
                                onOpenStudio()
                            }
                            else -> {
                                viewModel.regenerateNextCommandsForActiveNiche(isChatContext = false)
                                onOpenStudio()
                            }
                        }
                    },
                    modifier = Modifier
                        .fillMaxWidth()
                        .defaultMinSize(minHeight = OmniDesignTokens.MinTouchTarget),
                    shape = RoundedCornerShape(OmniDesignTokens.ChipRadius)
                ) {
                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(modifier = Modifier.width(8.dp))
                    Text("Auto-Type & Create")
                }
            }
        }

        // 2-COLUMN BENTO ROW 1: Kaggle GPU Quota Ring + Active Project Push
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OmniDesignTokens.ItemGap)
            ) {
                // Card A: Kaggle GPU Quota (Circular Progress Ring X/30h)
                val gpuFraction = (weeklyUsedGpuHours / 30.0).toFloat().coerceIn(0f, 1f)
                val remainingHours = (30.0 - weeklyUsedGpuHours).coerceAtLeast(0.0)
                val ringColor = when {
                    remainingHours < 2.0 -> CrimsonDanger
                    remainingHours < 6.0 -> AmberWarning
                    else -> primaryColor
                }

                OmniSurfaceCard(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenAnalytics
                ) {
                    Text(
                        text = "Kaggle GPU Quota",
                        style = MaterialTheme.typography.labelLarge,
                        fontWeight = FontWeight.SemiBold,
                        color = colorScheme.onSurface,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                    Box(
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(vertical = 6.dp),
                        contentAlignment = Alignment.Center
                    ) {
                        Box(
                            modifier = Modifier.size(76.dp),
                            contentAlignment = Alignment.Center
                        ) {
                            val trackColor = colorScheme.surfaceVariant
                            Canvas(modifier = Modifier.size(76.dp)) {
                                val strokeWidth = 7.dp.toPx()
                                drawArc(
                                    color = trackColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                                drawArc(
                                    color = ringColor,
                                    startAngle = -90f,
                                    sweepAngle = 360f * gpuFraction,
                                    useCenter = false,
                                    style = Stroke(width = strokeWidth, cap = StrokeCap.Round)
                                )
                            }
                            Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                Text(
                                    text = "%.1f".format(weeklyUsedGpuHours),
                                    style = MaterialTheme.typography.titleMedium,
                                    fontWeight = FontWeight.Bold,
                                    color = ringColor
                                )
                                Text(
                                    text = "/ 30h",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            }
                        }
                    }
                    Text(
                        text = if (activeRenderCount > 0) "$activeRenderCount active render(s)" else "%.1fh left weekly".format(remainingHours),
                        style = MaterialTheme.typography.labelSmall,
                        color = if (activeRenderCount > 0) EmeraldAccent else colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }

                // Card B: Active Project + Pending Diff + 1-Tap Push
                val proj = activeProject
                val pendingChanges = activeDiff.totalPendingCount
                val canPush = proj != null && proj.githubOwner.isNotBlank() && proj.githubRepo.isNotBlank() && !isGitBusy

                OmniSurfaceCard(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenProjects
                ) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Text(
                            text = "Active Project",
                            style = MaterialTheme.typography.labelLarge,
                            fontWeight = FontWeight.SemiBold,
                            color = colorScheme.onSurface,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.weight(1f)
                        )
                        Icon(
                            imageVector = Icons.Filled.FolderSpecial,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(16.dp)
                        )
                    }

                    Text(
                        text = proj?.name ?: "No Project Yet",
                        style = MaterialTheme.typography.titleMedium,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Text(
                        text = when {
                            proj == null -> "Tap to create workspace"
                            pendingChanges > 0 -> "$pendingChanges pending change(s)"
                            proj.githubRepo.isNotBlank() -> "${proj.githubRepo}@${proj.githubBranch}"
                            else -> "Local workspace ready"
                        },
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = if (pendingChanges > 0) AmberWarning else EmeraldAccent,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )

                    Spacer(modifier = Modifier.height(2.dp))

                    OutlinedButton(
                        onClick = {
                            if (canPush && proj != null) {
                                viewModel.pushProjectAtomically(proj, "")
                            } else {
                                onOpenProjects()
                            }
                        },
                        modifier = Modifier
                            .fillMaxWidth()
                            .defaultMinSize(minHeight = 40.dp),
                        shape = RoundedCornerShape(10.dp),
                        contentPadding = PaddingValues(horizontal = 8.dp, vertical = 6.dp)
                    ) {
                        Icon(Icons.Filled.CloudUpload, contentDescription = null, modifier = Modifier.size(15.dp))
                        Spacer(modifier = Modifier.width(6.dp))
                        Text(
                            text = if (canPush) "Push" else "Open",
                            maxLines = 1
                        )
                    }
                }
            }
        }

        // 2-COLUMN BENTO ROW 2: Quick AI Voice + Open Terminal
        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(OmniDesignTokens.ItemGap)
            ) {
                OmniSurfaceCard(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenQuickVoice
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(primaryColor.omniAccentContainer()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Mic,
                            contentDescription = null,
                            tint = primaryColor,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Quick AI Voice",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "Speak in EN / HI",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }

                OmniSurfaceCard(
                    modifier = Modifier.weight(1f),
                    onClick = onOpenTerminal
                ) {
                    Box(
                        modifier = Modifier
                            .size(40.dp)
                            .clip(CircleShape)
                            .background(EmeraldAccent.omniAccentContainer()),
                        contentAlignment = Alignment.Center
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Terminal,
                            contentDescription = null,
                            tint = EmeraldAccent,
                            modifier = Modifier.size(20.dp)
                        )
                    }
                    Text(
                        text = "Open Terminal",
                        style = MaterialTheme.typography.titleSmall,
                        fontWeight = FontWeight.SemiBold,
                        maxLines = 1
                    )
                    Text(
                        text = "OmniCLI & Git Shell",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant,
                        maxLines = 1
                    )
                }
            }
        }

        // BENTO ROW 3 (v5.3): YouTube Channel Performance & Daily API Quota Card
        item {
            val quotaFrac = (ytDailyQuota / 10000f).coerceIn(0f, 1f)
            val quotaColor = when {
                ytDailyQuota >= 8500 -> CrimsonDanger
                ytDailyQuota >= 6000 -> AmberWarning
                else -> EmeraldAccent
            }
            OmniSurfaceCard(onClick = onOpenAnalytics) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp),
                        modifier = Modifier.weight(1f)
                    ) {
                        Box(
                            modifier = Modifier
                                .size(38.dp)
                                .clip(CircleShape)
                                .background(CrimsonDanger.omniAccentContainer()),
                            contentAlignment = Alignment.Center
                        ) {
                            Icon(
                                imageVector = Icons.Filled.PlayCircle,
                                contentDescription = null,
                                tint = CrimsonDanger,
                                modifier = Modifier.size(20.dp)
                            )
                        }
                        Column {
                            Text(
                                text = ytStats?.channelTitle?.ifBlank { "YouTube Studio Sync" } ?: "YouTube Studio Sync",
                                style = MaterialTheme.typography.titleSmall,
                                fontWeight = FontWeight.SemiBold,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            Text(
                                text = if (ytStats != null) {
                                    "%,d subs · %,d views · %d videos".format(
                                        ytStats!!.subscriberCount,
                                        ytStats!!.viewCount,
                                        ytStats!!.videoCount
                                    )
                                } else {
                                    "Tap Sync to pull channel stats & feed AI hooks"
                                },
                                style = MaterialTheme.typography.labelSmall,
                                color = colorScheme.onSurfaceVariant,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                        }
                    }
                    IconButton(
                        enabled = !isRefreshingYt,
                        onClick = { viewModel.refreshYouTubeAnalytics() }
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Refresh,
                            contentDescription = "Refresh YouTube Stats",
                            tint = primaryColor
                        )
                    }
                }
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Text(
                        text = "Daily YT API Quota",
                        style = MaterialTheme.typography.labelSmall,
                        color = colorScheme.onSurfaceVariant
                    )
                    Text(
                        text = "%,d / 10,000 units".format(ytDailyQuota),
                        fontFamily = FontFamily.Monospace,
                        style = MaterialTheme.typography.labelSmall,
                        color = quotaColor
                    )
                }
                LinearProgressIndicator(
                    progress = { quotaFrac },
                    modifier = Modifier
                        .fillMaxWidth()
                        .clip(RoundedCornerShape(4.dp)),
                    color = quotaColor
                )
            }
        }

        // RECENT ACTIVITY STRIP (Last 2 chats or rendered videos)
        item {
            OmniSectionHeader(
                title = "Recent Activity",
                subtitle = if (recentChats.isNotEmpty() || recentVideos.isNotEmpty()) {
                    "Jump back into your latest sessions"
                } else {
                    "Your latest chats & renders appear here"
                },
                trailing = {
                    if (recentVideos.isNotEmpty()) {
                        TextButton(onClick = onOpenGallery) { Text("Gallery") }
                    }
                }
            )
        }

        item {
            if (recentChats.isEmpty() && recentVideos.isEmpty()) {
                OmniEmptyState(
                    icon = Icons.Filled.ChatBubble,
                    title = "No recent activity yet",
                    helperText = "Start an uncensored AI chat or queue your first Dual-GPU storyboard render.",
                    actionLabel = "Start New Chat",
                    onActionClick = onOpenChat
                )
            } else {
                Column(verticalArrangement = Arrangement.spacedBy(OmniDesignTokens.ItemGap)) {
                    recentChats.forEach { thread ->
                        OmniSurfaceCard(
                            onClick = {
                                viewModel.selectThread(thread.id)
                                onOpenChat()
                            }
                        ) {
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(12.dp)
                            ) {
                                Box(
                                    modifier = Modifier
                                        .size(38.dp)
                                        .clip(CircleShape)
                                        .background(primaryColor.omniAccentContainer()),
                                    contentAlignment = Alignment.Center
                                ) {
                                    Icon(
                                        imageVector = Icons.Filled.ChatBubble,
                                        contentDescription = null,
                                        tint = primaryColor,
                                        modifier = Modifier.size(18.dp)
                                    )
                                }
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = thread.title,
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.SemiBold,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis
                                    )
                                    Text(
                                        text = "${thread.messageCount} msgs · ${thread.providerUsed.ifBlank { "OmniAI Chat" }}",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = colorScheme.onSurfaceVariant,
                                        maxLines = 1
                                    )
                                }
                            }
                        }
                    }

                    if (recentChats.size < 2) {
                        recentVideos.take(2 - recentChats.size).forEach { vid ->
                            val sizeMb = vid.sizeBytes.toDouble() / (1024.0 * 1024.0)
                            OmniSurfaceCard(onClick = onOpenGallery) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    verticalAlignment = Alignment.CenterVertically,
                                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                                ) {
                                    Box(
                                        modifier = Modifier
                                            .size(38.dp)
                                            .clip(CircleShape)
                                            .background(EmeraldAccent.omniAccentContainer()),
                                        contentAlignment = Alignment.Center
                                    ) {
                                        Icon(
                                            imageVector = Icons.Filled.MovieCreation,
                                            contentDescription = null,
                                            tint = EmeraldAccent,
                                            modifier = Modifier.size(18.dp)
                                        )
                                    }
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = vid.fileName,
                                            style = MaterialTheme.typography.bodyMedium,
                                            fontWeight = FontWeight.SemiBold,
                                            maxLines = 1,
                                            overflow = TextOverflow.Ellipsis
                                        )
                                        Text(
                                            text = "${vid.category} · %.1f MB · Ready to preview".format(sizeMb),
                                            style = MaterialTheme.typography.labelSmall,
                                            color = EmeraldAccent,
                                            maxLines = 1
                                        )
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
