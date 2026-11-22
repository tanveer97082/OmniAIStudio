package com.omniai.studio.ui.screens

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
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Search
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
import androidx.compose.material3.Surface
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.window.Dialog
import androidx.compose.ui.window.DialogProperties
import com.omniai.studio.data.remote.HfModelSummary
import com.omniai.studio.ui.theme.AmberWarning
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.viewmodel.OmniAiViewModel

/**
 * Built-in Hugging Face Model Explorer sheet/dialog inside Tab 5 (Vault).
 * Queries GET https://huggingface.co/api/models?search={query}&sort=downloads&direction=-1&limit=20
 * and allows 1-tap Active HF Model selection or GGUF Repo URL copy for Colab/Kaggle.
 */
@Composable
fun HfExplorerSheet(
    viewModel: OmniAiViewModel,
    onSelectActiveModel: (String) -> Unit,
    onDismiss: () -> Unit
) {
    val clipboard = LocalClipboardManager.current
    val results by viewModel.hfExplorerResults.collectAsState()
    val isLoading by viewModel.isSearchingHfModels.collectAsState()
    var query by remember { mutableStateOf("Qwen2.5") }

    LaunchedEffect(Unit) {
        if (results.isEmpty()) {
            viewModel.searchHuggingFaceModels(query)
        }
    }

    Dialog(
        onDismissRequest = onDismiss,
        properties = DialogProperties(usePlatformDefaultWidth = false)
    ) {
        Surface(
            color = AmoledBlack,
            modifier = Modifier.fillMaxSize()
        ) {
            Column(
                modifier = Modifier
                    .fillMaxSize()
                    .padding(12.dp),
                verticalArrangement = Arrangement.spacedBy(10.dp)
            ) {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column {
                        Text("Hugging Face Model Explorer", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Live Hub search sorted by downloads · GGUF & Instruct models",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                    IconButton(onClick = onDismiss) {
                        Icon(Icons.Filled.Close, contentDescription = "Close Explorer")
                    }
                }

                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.spacedBy(8.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    OutlinedTextField(
                        value = query,
                        onValueChange = { query = it },
                        label = { Text("Search HF Hub (e.g. Qwen, Llama, gguf, Wan)") },
                        singleLine = true,
                        modifier = Modifier.weight(1f)
                    )
                    Button(
                        onClick = { viewModel.searchHuggingFaceModels(query) },
                        enabled = !isLoading
                    ) {
                        Icon(Icons.Filled.Search, contentDescription = null)
                    }
                }

                LazyRow(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    val quickQueries = listOf("Qwen2.5", "gguf", "Llama-3", "DeepSeek", "Mistral", "Wan2.1")
                    items(quickQueries) { tag ->
                        FilterChip(
                            selected = query.equals(tag, ignoreCase = true),
                            onClick = {
                                query = tag
                                viewModel.searchHuggingFaceModels(tag)
                            },
                            label = { Text(tag) }
                        )
                    }
                }

                if (isLoading) {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }

                HorizontalDivider(color = AmoledBorder)

                LazyColumn(
                    modifier = Modifier.weight(1f),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    if (results.isEmpty() && !isLoading) {
                        item {
                            Text(
                                "No models found. Try searching 'Qwen2.5', 'gguf', or 'Llama'.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = TextSecondary
                            )
                        }
                    } else {
                        items(results, key = { it.id }) { model ->
                            HfModelCard(
                                model = model,
                                onUseModel = {
                                    onSelectActiveModel(model.id)
                                    onDismiss()
                                },
                                onCopyRepoUrl = {
                                    clipboard.setText(AnnotatedString(model.repoUrl))
                                    viewModel.postToast("Copied GGUF/HF Repo URL: ${model.repoUrl}")
                                }
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun HfModelCard(
    model: HfModelSummary,
    onUseModel: () -> Unit,
    onCopyRepoUrl: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(containerColor = AmoledSurface),
        border = BorderStroke(1.dp, AmoledBorder)
    ) {
        Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
            Text(model.id, style = MaterialTheme.typography.titleMedium)
            Text(
                text = "Downloads: %,d  ·  Likes: %,d".format(model.downloads, model.likes) +
                    if (model.pipelineTag.isNotBlank()) "  ·  ${model.pipelineTag}" else "",
                style = MaterialTheme.typography.labelMedium,
                color = EmeraldAccent
            )
            val displayTags = buildList {
                if (model.isGguf) add("gguf")
                if (model.pipelineTag.isNotBlank()) add(model.pipelineTag)
                addAll(model.tags.filter { it.equals("text-generation", true) || it.equals("gguf", true) || it.equals("safetensors", true) })
            }.distinct().take(4)

            if (displayTags.isNotEmpty()) {
                Text(
                    text = "Tags: ${displayTags.joinToString(" · ")}",
                    style = MaterialTheme.typography.labelMedium,
                    color = if (model.isGguf) AmberWarning else TextSecondary
                )
            }

            Spacer(modifier = Modifier.height(2.dp))
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                Button(
                    onClick = onUseModel,
                    modifier = Modifier.weight(1f)
                ) {
                    Text("Set as Active HF Model ID")
                }
                OutlinedButton(
                    onClick = onCopyRepoUrl
                ) {
                    Icon(Icons.Filled.ContentCopy, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("Copy GGUF Repo URL")
                }
            }
        }
    }
}
