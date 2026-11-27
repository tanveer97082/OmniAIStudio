package com.omniai.studio.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.compose.LifecycleEventEffect
import com.omniai.studio.research.ResearchCsvGraph
import com.omniai.studio.ui.theme.LocalUiConfig
import com.omniai.studio.viewmodel.OmniAiViewModel

private enum class ResearchToolTab(val label: String) {
    DOCUMENTS("PDF / Notebook"),
    WEB("Web Research"),
    LATEX("LaTeX"),
    GRAPHS("Graphs"),
    TRENDS("Trends & Niches")
}

@Composable
fun ResearchLabScreen(viewModel: OmniAiViewModel) {
    val uiConfig = LocalUiConfig.current
    val clipboard = LocalClipboardManager.current
    val appSettings by viewModel.appSettings.collectAsState()
    val profiles by viewModel.workspaces.collectAsState()
    val activeWorkspaceId by viewModel.activeWorkspaceId.collectAsState()
    val activeNiche by viewModel.activeNiche.collectAsState()
    val researchResult by viewModel.researchResultText.collectAsState()
    val researchSource by viewModel.researchSourceLabel.collectAsState()
    val latexOutput by viewModel.researchLatexOutput.collectAsState()
    val csvGraph by viewModel.researchCsvGraph.collectAsState()
    val isBusy by viewModel.isResearchTaskRunning.collectAsState()
    val trendItems by viewModel.trendFeedItems.collectAsState()
    val trendReport by viewModel.trendDiscoveryReport.collectAsState()
    val trendSourceErrors by viewModel.trendSourceErrors.collectAsState()
    val isTrendSearchRunning by viewModel.isDiscoveringNicheTrends.collectAsState()
    val rssFeeds by viewModel.rssFeeds.collectAsState()

    var selectedTool by rememberSaveable { mutableStateOf(ResearchToolTab.DOCUMENTS) }
    var researchQuestion by rememberSaveable {
        mutableStateOf("Summarize key findings, methods, assumptions, and limitations.")
    }
    var webUrl by rememberSaveable { mutableStateOf("") }
    var latexPrompt by rememberSaveable { mutableStateOf("") }
    var selectedSeriesName by rememberSaveable { mutableStateOf("") }
    var trendFocus by rememberSaveable { mutableStateOf("") }
    var trendRegion by rememberSaveable { mutableStateOf("IN") }
    var trendRequestNicheId by remember { mutableStateOf<Long?>(null) }

    val documentPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.analyzeResearchDocument(uri, researchQuestion)
    }
    val csvPicker = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) viewModel.loadResearchCsvGraph(uri)
    }

    LaunchedEffect(csvGraph) {
        selectedSeriesName = csvGraph?.series?.firstOrNull()?.name.orEmpty()
    }

    LaunchedEffect(selectedTool) {
        if (selectedTool != ResearchToolTab.TRENDS) viewModel.cancelTrendDiscovery()
    }

    LaunchedEffect(activeNiche?.id) {
        if (trendRequestNicheId != activeNiche?.id) viewModel.cancelTrendDiscovery()
    }

    LifecycleEventEffect(Lifecycle.Event.ON_STOP) {
        viewModel.cancelTrendDiscovery()
    }

    DisposableEffect(Unit) {
        onDispose { viewModel.cancelTrendDiscovery() }
    }

    val activeProfile = profiles.firstOrNull { it.id == activeWorkspaceId }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(MaterialTheme.colorScheme.background)
            .padding(horizontal = uiConfig.uiDensity.cardPaddingDp.dp, vertical = 10.dp)
            .verticalScroll(rememberScrollState()),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        Card(
            colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
            shape = RoundedCornerShape(18.dp),
            modifier = Modifier.fillMaxWidth()
        ) {
            Column(
                modifier = Modifier.padding(14.dp),
                verticalArrangement = Arrangement.spacedBy(5.dp)
            ) {
                Text("Research Lab", style = MaterialTheme.typography.headlineSmall)
                Text(
                    "${activeProfile?.name ?: activeWorkspaceId} profile · ${appSettings.aiPurposeMode.label} mode",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.primary
                )
                Text(
                    "Analyze documents and sources, draft mathematical notation, and graph numeric CSV data. Notebook code is inspected as text only and is never executed.",
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
        }

        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            ResearchToolTab.entries.forEach { tab ->
                FilterChip(
                    selected = selectedTool == tab,
                    onClick = { selectedTool = tab },
                    label = { Text(tab.label) }
                )
            }
        }

        if (isBusy) {
            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
        }

        when (selectedTool) {
            ResearchToolTab.DOCUMENTS -> {
                ResearchCard(title = "PDF and notebook analysis") {
                    Text(
                        "PDF text is extracted with on-device OCR (up to 12 pages). Jupyter .ipynb source and saved outputs are parsed without running notebook code.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = researchQuestion,
                        onValueChange = { researchQuestion = it },
                        label = { Text("Research question / analysis goal") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            documentPicker.launch(
                                arrayOf("application/pdf", "application/json", "text/*", "application/octet-stream")
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose PDF or .ipynb")
                    }
                }
            }

            ResearchToolTab.WEB -> {
                ResearchCard(title = "Web and data source extraction") {
                    Text(
                        "Fetches one HTTPS page at a time and extracts readable article text; it does not crawl linked pages.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = webUrl,
                        onValueChange = { webUrl = it },
                        label = { Text("HTTPS URL") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = researchQuestion,
                        onValueChange = { researchQuestion = it },
                        label = { Text("Research question") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = { viewModel.analyzeResearchUrl(webUrl, researchQuestion) },
                        enabled = webUrl.isNotBlank() && !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Extract and analyze source")
                    }
                }
            }

            ResearchToolTab.LATEX -> {
                ResearchCard(title = "LaTeX equation workspace") {
                    Text(
                        "Describe an equation to generate LaTeX, or paste LaTeX and ask for an explanation. Preview is copyable source text.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf(
                            "Quadratic formula" to "Write the quadratic formula using a, b, and c.",
                            "Bayes theorem" to "Write Bayes' theorem and define P(A|B).",
                            "Integral" to "Write the definite integral of f(x) from a to b."
                        ).forEach { (label, prompt) ->
                            FilterChip(
                                selected = false,
                                onClick = { latexPrompt = prompt },
                                label = { Text(label) }
                            )
                        }
                    }
                    OutlinedTextField(
                        value = latexPrompt,
                        onValueChange = { latexPrompt = it },
                        label = { Text("Equation request or LaTeX source") },
                        minLines = 3,
                        maxLines = 6,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        Button(
                            onClick = { viewModel.generateResearchLatex(latexPrompt) },
                            enabled = latexPrompt.isNotBlank() && !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Generate")
                        }
                        OutlinedButton(
                            onClick = { viewModel.explainResearchLatex(latexPrompt) },
                            enabled = latexPrompt.isNotBlank() && !isBusy,
                            modifier = Modifier.weight(1f)
                        ) {
                            Text("Explain")
                        }
                    }
                    if (latexOutput.isNotBlank()) {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant,
                            shape = RoundedCornerShape(10.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(10.dp),
                                verticalArrangement = Arrangement.spacedBy(6.dp)
                            ) {
                                Text("LaTeX source preview", style = MaterialTheme.typography.labelLarge)
                                Text(
                                    latexOutput,
                                    fontFamily = FontFamily.Monospace,
                                    fontSize = 13.sp
                                )
                                OutlinedButton(
                                    onClick = {
                                        clipboard.setText(AnnotatedString(latexOutput))
                                        viewModel.postToast("Copied LaTeX source.")
                                    }
                                ) {
                                    Text("Copy LaTeX")
                                }
                            }
                        }
                    }
                }
            }

            ResearchToolTab.GRAPHS -> {
                ResearchCard(title = "CSV data graphing") {
                    Text(
                        "Select a CSV with a category column and one or more numeric columns. Up to 1,000 data rows and five numeric series are graphed locally.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedButton(
                        onClick = {
                            csvPicker.launch(
                                arrayOf("text/csv", "text/comma-separated-values", "application/vnd.ms-excel", "text/plain")
                            )
                        },
                        enabled = !isBusy,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text("Choose CSV data")
                    }
                    csvGraph?.let { graph ->
                        CsvGraphPanel(
                            graph = graph,
                            selectedSeriesName = selectedSeriesName,
                            onSelectSeries = { selectedSeriesName = it }
                        )
                    }
                }
            }

            ResearchToolTab.TRENDS -> {
                ResearchCard(title = "Live trends for ${activeNiche?.name ?: "your selected niche"}") {
                    Text(
                        "Search is manual and only runs when you tap the button. It checks Google Trends RSS, optional Brave Search, and enabled RSS feeds; it does not schedule background scans.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    OutlinedTextField(
                        value = trendFocus,
                        onValueChange = { trendFocus = it },
                        label = { Text("Optional focus (e.g. AI video, fitness)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Text("Region", style = MaterialTheme.typography.labelLarge)
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        listOf("IN" to "India", "US" to "US", "GB" to "UK", "CA" to "Canada", "AU" to "Australia")
                            .forEach { (region, label) ->
                                FilterChip(
                                    selected = trendRegion == region,
                                    onClick = { trendRegion = region },
                                    label = { Text(label) }
                                )
                            }
                    }
                    Text(
                        "Sources: Google Trends, optional Brave Search, and up to four enabled RSS feeds (${rssFeeds.count { it.enabled }} enabled).",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    Button(
                        onClick = {
                            trendRequestNicheId = activeNiche?.id
                            viewModel.discoverTrendsForActiveNiche(trendFocus, trendRegion)
                        },
                        enabled = activeNiche != null && !isTrendSearchRunning,
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(if (isTrendSearchRunning) "Searching..." else "Find trends and related ideas")
                    }
                }

                trendSourceErrors.forEach { error ->
                    Text(
                        error,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }

                if (trendItems.isNotEmpty()) {
                    ResearchCard(title = "Trend headlines (${trendItems.size})") {
                        trendItems.forEach { item ->
                            Column(verticalArrangement = Arrangement.spacedBy(3.dp)) {
                                Text(item.title, style = MaterialTheme.typography.titleSmall)
                                Text(
                                    "${item.source}${if (item.url.isNotBlank()) " · ${item.url}" else ""}",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.primary
                                )
                                if (item.summary.isNotBlank()) {
                                    Text(item.summary, style = MaterialTheme.typography.bodySmall)
                                }
                            }
                        }
                    }
                }

                trendReport?.let { report ->
                    if (report.relevantTrends.isNotEmpty()) {
                        ResearchCard(title = "Relevant to ${activeNiche?.name ?: "this niche"}") {
                            report.relevantTrends.forEach { Text("- $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                    if (report.nicheIdeas.isNotEmpty()) {
                        ResearchCard(title = "New niche ideas") {
                            report.nicheIdeas.forEach { suggestion ->
                                Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
                                    Text(
                                        "${suggestion.emoji} ${suggestion.name}",
                                        style = MaterialTheme.typography.titleSmall
                                    )
                                    if (suggestion.description.isNotBlank()) {
                                        Text(suggestion.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                    OutlinedButton(
                                        onClick = { viewModel.saveTrendNiche(suggestion) },
                                        modifier = Modifier.fillMaxWidth()
                                    ) {
                                        Text("Save as niche profile")
                                    }
                                }
                            }
                        }
                    }
                    if (report.themeIdeas.isNotEmpty()) {
                        ResearchCard(title = "Related themes") {
                            report.themeIdeas.forEach { Text("- $it", style = MaterialTheme.typography.bodyMedium) }
                        }
                    }
                }
            }
        }

        if (researchResult.isNotBlank()) {
            ResearchCard(title = "Analysis · ${researchSource.ifBlank { "Research result" }}") {
                Text(
                    researchResult,
                    style = MaterialTheme.typography.bodyMedium
                )
                OutlinedButton(onClick = viewModel::clearResearchResults) {
                    Text("Clear research results")
                }
            }
        }
    }
}

@Composable
private fun ResearchCard(
    title: String,
    content: @Composable () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(
            modifier = Modifier.padding(14.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            Text(title, style = MaterialTheme.typography.titleMedium)
            content()
        }
    }
}

@Composable
private fun CsvGraphPanel(
    graph: ResearchCsvGraph,
    selectedSeriesName: String,
    onSelectSeries: (String) -> Unit
) {
    val colorScheme = MaterialTheme.colorScheme
    val selectedSeries = graph.series.firstOrNull { it.name == selectedSeriesName } ?: graph.series.first()
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text("${graph.fileName} · X: ${graph.categoryLabel}", style = MaterialTheme.typography.labelMedium)
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .horizontalScroll(rememberScrollState()),
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            graph.series.forEach { series ->
                FilterChip(
                    selected = selectedSeries.name == series.name,
                    onClick = { onSelectSeries(series.name) },
                    label = { Text(series.name) }
                )
            }
        }

        val lineColor = colorScheme.primary
        Canvas(
            modifier = Modifier
                .fillMaxWidth()
                .height(220.dp)
        ) {
            val left = 34.dp.toPx()
            val right = size.width - 12.dp.toPx()
            val top = 14.dp.toPx()
            val bottom = size.height - 22.dp.toPx()
            val plotWidth = (right - left).coerceAtLeast(1f)
            val plotHeight = (bottom - top).coerceAtLeast(1f)
            val values = selectedSeries.points.map { it.value }
            val low = minOf(0.0, values.minOrNull() ?: 0.0)
            val high = maxOf(0.0, values.maxOrNull() ?: 1.0)
            val spread = (high - low).takeIf { it > 0.0 } ?: 1.0

            repeat(4) { line ->
                val y = top + plotHeight * line / 3f
                drawLine(
                    color = colorScheme.outlineVariant,
                    start = Offset(left, y),
                    end = Offset(right, y),
                    strokeWidth = 1.dp.toPx()
                )
            }
            drawLine(
                color = colorScheme.outline,
                start = Offset(left, top),
                end = Offset(left, bottom),
                strokeWidth = 1.dp.toPx()
            )
            drawLine(
                color = colorScheme.outline,
                start = Offset(left, bottom),
                end = Offset(right, bottom),
                strokeWidth = 1.dp.toPx()
            )

            val path = Path()
            selectedSeries.points.forEachIndexed { index, point ->
                val x = left + plotWidth * index / (selectedSeries.points.size - 1).coerceAtLeast(1)
                val normalized = ((point.value - low) / spread).toFloat()
                val y = bottom - normalized * plotHeight
                if (index == 0) path.moveTo(x, y) else path.lineTo(x, y)
            }
            drawPath(
                path = path,
                color = lineColor,
                style = Stroke(width = 3.dp.toPx(), cap = StrokeCap.Round)
            )
            selectedSeries.points.forEachIndexed { index, point ->
                val x = left + plotWidth * index / (selectedSeries.points.size - 1).coerceAtLeast(1)
                val normalized = ((point.value - low) / spread).toFloat()
                val y = bottom - normalized * plotHeight
                drawCircle(color = lineColor, radius = 4.dp.toPx(), center = Offset(x, y))
            }
        }

        Text(
            "Y: ${selectedSeries.name} · ${selectedSeries.points.size} points · range ${
                "%.3g".format(selectedSeries.points.minOf { it.value })
            } to ${"%.3g".format(selectedSeries.points.maxOf { it.value })}",
            style = MaterialTheme.typography.bodySmall,
            color = colorScheme.onSurfaceVariant
        )
        Text(
            "Categories: ${selectedSeries.points.take(8).joinToString(", ") { it.label }}",
            style = MaterialTheme.typography.labelSmall,
            color = colorScheme.onSurfaceVariant
        )
    }
}
