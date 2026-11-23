package com.omniai.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.viewmodel.OmniAiViewModel

private data class ProviderChartStat(
    val name: String,
    val calls: Int,
    val avgLatencyMs: Long,
    val successRatePct: Int,
    val totalTokens: Long,
    val barColor: Color
)

/**
 * Token, Latency & Quota Analytics Dashboard rendered with native Jetpack Compose Canvas bar charts.
 */
@Composable
fun AnalyticsScreen(
    viewModel: OmniAiViewModel,
    onBack: () -> Unit
) {
    val metrics by viewModel.apiMetrics.collectAsState()
    val weeklyUsedGpuHours by viewModel.weeklyUsedGpuHours.collectAsState()
    val recentRenderLogs by viewModel.recentRenderLogs.collectAsState()
    val ytStats by viewModel.youTubeChannelStats.collectAsState()
    val ytRecentVideos by viewModel.youTubeRecentVideos.collectAsState()
    val isRefreshingYt by viewModel.isRefreshingYouTubeStats.collectAsState()
    val ytDailyQuota by viewModel.youTubeTodayQuotaUnits.collectAsState()
    val primaryColor = MaterialTheme.colorScheme.primary

    val totalCalls = metrics.size
    val totalTokens = metrics.sumOf { it.estimatedTokens.toLong() }
    val successCount = metrics.count { it.success }
    val overallSuccessRate = if (totalCalls > 0) (successCount * 100 / totalCalls) else 100
    val avgLatencyAll = if (totalCalls > 0) metrics.map { it.latencyMs }.average().toLong() else 0L

    val palette = listOf(EmeraldAccent, Color(0xFF06B6D4), Color(0xFFA855F7), AmberWarning)
    val providerStats = metrics
        .groupBy { it.providerName.ifBlank { "Unknown" } }
        .entries
        .mapIndexed { index, (provider, list) ->
            val ok = list.count { it.success }
            ProviderChartStat(
                name = provider,
                calls = list.size,
                avgLatencyMs = list.map { it.latencyMs }.average().toLong(),
                successRatePct = if (list.isNotEmpty()) (ok * 100 / list.size) else 0,
                totalTokens = list.sumOf { it.estimatedTokens.toLong() },
                barColor = palette[index % palette.size]
            )
        }
        .sortedByDescending { it.calls }

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
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    IconButton(onClick = onBack) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                    Column {
                        Text("Analytics Dashboard", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Token usage · Provider latency · Failover rate · Kaggle GPU",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
                if (metrics.isNotEmpty()) {
                    IconButton(onClick = { viewModel.clearApiMetrics() }) {
                        Icon(Icons.Filled.Delete, contentDescription = "Clear Metrics", tint = CrimsonDanger)
                    }
                }
            }
        }

        // KPI Summary Row
        item {
            Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                MetricKpiCard("Total Calls", "$totalCalls", EmeraldAccent, Modifier.weight(1f))
                MetricKpiCard("Est. Tokens", "%,d".format(totalTokens), primaryColor, Modifier.weight(1f))
                MetricKpiCard("Avg Latency", "${avgLatencyAll}ms", Color(0xFF06B6D4), Modifier.weight(1f))
                MetricKpiCard(
                    "Success Rate",
                    "$overallSuccessRate%",
                    if (overallSuccessRate >= 90) EmeraldAccent else AmberWarning,
                    Modifier.weight(1f)
                )
            }
        }

        // Chart 1: Provider Usage Share (Native Compose Canvas)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Provider Usage Share (Calls & Tokens)", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    if (providerStats.isEmpty()) {
                        Text(
                            "No AI calls recorded yet. Send a chat message or run a workflow to populate telemetry.",
                            style = MaterialTheme.typography.bodyMedium,
                            color = TextSecondary
                        )
                    } else {
                        val maxCalls = providerStats.maxOf { it.calls }.coerceAtLeast(1)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height((providerStats.size * 34).coerceAtLeast(80).dp)
                        ) {
                            val rowHeight = size.height / providerStats.size
                            providerStats.forEachIndexed { idx, stat ->
                                val topY = idx * rowHeight + 6f
                                val barH = (rowHeight - 14f).coerceAtLeast(14f)
                                val fraction = stat.calls.toFloat() / maxCalls.toFloat()
                                // Track
                                drawRoundRect(
                                    color = Color(0xFF1A1A24),
                                    topLeft = Offset(0f, topY),
                                    size = Size(size.width, barH),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                                // Active bar
                                drawRoundRect(
                                    color = stat.barColor,
                                    topLeft = Offset(0f, topY),
                                    size = Size(size.width * fraction.coerceIn(0.05f, 1f), barH),
                                    cornerRadius = CornerRadius(8f, 8f)
                                )
                            }
                        }
                        providerStats.forEach { stat ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(stat.name, style = MaterialTheme.typography.bodyMedium, color = stat.barColor)
                                Text(
                                    "${stat.calls} calls · %,d tok · %d%% ok".format(stat.totalTokens, stat.successRatePct),
                                    style = MaterialTheme.typography.labelMedium,
                                    color = TextSecondary
                                )
                            }
                        }
                    }
                }
            }
        }

        // Chart 2: Average Response Latency (ms) Bar Chart (Native Compose Canvas)
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Average Response Latency (ms)", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    if (providerStats.isEmpty()) {
                        Text("Waiting for latency samples...", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    } else {
                        val maxLatency = providerStats.maxOf { it.avgLatencyMs }.coerceAtLeast(100L)
                        Canvas(
                            modifier = Modifier
                                .fillMaxWidth()
                                .height(140.dp)
                        ) {
                            val count = providerStats.size
                            val slotW = size.width / count
                            val barW = (slotW * 0.55f).coerceAtMost(96f)
                            providerStats.forEachIndexed { idx, stat ->
                                val hRatio = (stat.avgLatencyMs.toFloat() / maxLatency.toFloat()).coerceIn(0.08f, 1f)
                                val barH = size.height * hRatio
                                val left = idx * slotW + (slotW - barW) / 2f
                                drawRoundRect(
                                    color = stat.barColor,
                                    topLeft = Offset(left, size.height - barH),
                                    size = Size(barW, barH),
                                    cornerRadius = CornerRadius(10f, 10f)
                                )
                            }
                        }
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceAround
                        ) {
                            providerStats.forEach { stat ->
                                Column(horizontalAlignment = Alignment.CenterHorizontally) {
                                    Text(stat.name.take(12), style = MaterialTheme.typography.labelMedium, color = stat.barColor)
                                    Text("${stat.avgLatencyMs} ms", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                                }
                            }
                        }
                    }
                }
            }
        }

        // Chart 3: Weekly Kaggle GPU Quota Consumption
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                border = BorderStroke(1.dp, AmoledBorder)
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    val remaining = (30.0 - weeklyUsedGpuHours).coerceAtLeast(0.0)
                    Text("Weekly Kaggle T4 x2 GPU Quota", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                    Text(
                        "Used: %.2f hrs / 30.0 hrs  ·  Remaining: %.2f hrs".format(weeklyUsedGpuHours, remaining),
                        style = MaterialTheme.typography.labelMedium,
                        color = if (remaining < 2.0) CrimsonDanger else EmeraldAccent
                    )
                    LinearProgressIndicator(
                        progress = { (weeklyUsedGpuHours / 30.0).toFloat().coerceIn(0f, 1f) },
                        modifier = Modifier.fillMaxWidth(),
                        color = if (remaining < 2.0) CrimsonDanger else primaryColor
                    )
                    Spacer(modifier = Modifier.height(4.dp))
                    Text("Recent Render Jobs (${recentRenderLogs.size})", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                    recentRenderLogs.take(5).forEach { log ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween
                        ) {
                            Text(log.title.take(26), style = MaterialTheme.typography.bodyMedium)
                            Text(
                                "${log.status} · ${log.durationSeconds}s · ${log.accountLabel}",
                                style = MaterialTheme.typography.labelMedium,
                                color = if (log.status == "COMPLETED") EmeraldAccent else CrimsonDanger
                            )
                        }
                    }
                }
            }
        }

        // Chart 4 (v5.3): Closed-Loop YouTube Channel Performance & Daily API Quota Meter
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
                        Column(modifier = Modifier.weight(1f)) {
                            Text(
                                text = ytStats?.channelTitle?.ifBlank { "YouTube Channel Analytics" } ?: "YouTube Channel Analytics",
                                style = MaterialTheme.typography.titleMedium,
                                color = primaryColor
                            )
                            Text(
                                text = "Closed-loop feedback automatically guides ContinuityEngine hooks",
                                style = MaterialTheme.typography.labelMedium,
                                color = TextSecondary
                            )
                        }
                        OutlinedButton(
                            enabled = !isRefreshingYt,
                            onClick = { viewModel.refreshYouTubeAnalytics() }
                        ) {
                            Icon(Icons.Filled.Refresh, contentDescription = null)
                            Text(if (isRefreshingYt) "Sync..." else "Sync")
                        }
                    }

                    // Daily Quota Tracker (10,000 units/day free limit)
                    val quotaPct = (ytDailyQuota / 10000f).coerceIn(0f, 1f)
                    val quotaColor = when {
                        ytDailyQuota >= 8500 -> CrimsonDanger
                        ytDailyQuota >= 6000 -> AmberWarning
                        else -> EmeraldAccent
                    }
                    Text(
                        text = "YouTube API Daily Quota: %,d / 10,000 units (~1,650 per upload)".format(ytDailyQuota),
                        style = MaterialTheme.typography.labelMedium,
                        color = quotaColor
                    )
                    LinearProgressIndicator(
                        progress = { quotaPct },
                        modifier = Modifier.fillMaxWidth(),
                        color = quotaColor
                    )

                    if (ytStats != null) {
                        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            MetricKpiCard("Subscribers", "%,d".format(ytStats!!.subscriberCount), EmeraldAccent, Modifier.weight(1f))
                            MetricKpiCard("Total Views", "%,d".format(ytStats!!.viewCount), Color(0xFF06B6D4), Modifier.weight(1f))
                            MetricKpiCard("Videos", "${ytStats!!.videoCount}", primaryColor, Modifier.weight(1f))
                        }
                    } else {
                        Text(
                            text = "Tap Sync after saving your YouTube OAuth2 credentials in API Vault to pull live channel stats & recent video performance.",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }

                    if (ytRecentVideos.isNotEmpty()) {
                        Text("Recent 5 Videos Performance", style = MaterialTheme.typography.labelMedium, color = TextSecondary)
                        ytRecentVideos.take(5).forEach { vid ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                horizontalArrangement = Arrangement.SpaceBetween
                            ) {
                                Text(vid.title.take(28), style = MaterialTheme.typography.bodySmall, modifier = Modifier.weight(1f))
                                Text(
                                    "%,d views · %d likes · %d cmt".format(vid.viewCount, vid.likeCount, vid.commentCount),
                                    style = MaterialTheme.typography.labelSmall,
                                    color = EmeraldAccent
                                )
                            }
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun MetricKpiCard(
    label: String,
    value: String,
    accent: Color,
    modifier: Modifier = Modifier
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledSurface),
        border = BorderStroke(1.dp, AmoledBorder),
        modifier = modifier
    ) {
        Column(modifier = Modifier.padding(10.dp), verticalArrangement = Arrangement.spacedBy(4.dp)) {
            Text(label, style = MaterialTheme.typography.labelMedium, color = TextSecondary, maxLines = 1)
            Text(value, style = MaterialTheme.typography.titleMedium, color = accent, maxLines = 1)
        }
    }
}
