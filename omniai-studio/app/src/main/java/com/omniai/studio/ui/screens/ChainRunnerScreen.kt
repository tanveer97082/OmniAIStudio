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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.MovieCreation
import androidx.compose.material.icons.filled.PlayArrow
import androidx.compose.material3.AlertDialog
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
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.PromptChainEntity
import com.omniai.studio.data.remote.PromptChainStep
import com.omniai.studio.ui.theme.AmoledBlack
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.AmoledSurfaceVariant
import com.omniai.studio.ui.theme.CrimsonDanger
import com.omniai.studio.ui.theme.EmeraldAccent
import com.omniai.studio.ui.theme.TextSecondary
import com.omniai.studio.viewmodel.OmniAiViewModel

/**
 * Multi-Step AI Prompt Chainer ("Workflows") accessible from Tab 1 top bar.
 * Executes sequential steps ({{input}} -> {{prev_output}}) and can push final JSON storyboards
 * directly into Tab 2 (Video Director).
 */
@Composable
fun ChainRunnerScreen(
    viewModel: OmniAiViewModel,
    onBack: () -> Unit,
    onOpenDirector: () -> Unit
) {
    val chains by viewModel.promptChains.collectAsState()
    val isRunning by viewModel.isRunningChain.collectAsState()
    val activeStepIndex by viewModel.chainActiveStepIndex.collectAsState()
    val stepOutputs by viewModel.chainStepOutputs.collectAsState()

    var selectedChainId by remember(chains) { mutableStateOf(chains.firstOrNull()?.id) }
    val selectedChain = chains.firstOrNull { it.id == selectedChainId } ?: chains.firstOrNull()
    var initialInput by remember { mutableStateOf("") }
    var showCreateDialog by remember { mutableStateOf(false) }

    val primaryColor = MaterialTheme.colorScheme.primary

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
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to Chat")
                    }
                    Column {
                        Text("AI Prompt Workflows", style = MaterialTheme.typography.headlineSmall)
                        Text(
                            "Sequential {{input}} -> {{prev_output}} multi-step pipelines",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
                OutlinedButton(onClick = { showCreateDialog = true }) {
                    Icon(Icons.Filled.Add, contentDescription = null)
                    Spacer(modifier = Modifier.width(4.dp))
                    Text("New")
                }
            }
        }

        // Saved / Seeded Workflows
        items(chains, key = { it.id }) { chain ->
            val steps = remember(chain.stepsJson) { viewModel.parsePromptChainSteps(chain.stepsJson) }
            val isSelected = selectedChain?.id == chain.id
            Card(
                colors = CardDefaults.cardColors(
                    containerColor = if (isSelected) AmoledSurfaceVariant else AmoledSurface
                ),
                border = BorderStroke(
                    width = if (isSelected) 2.dp else 1.dp,
                    color = if (isSelected) primaryColor else AmoledBorder
                )
            ) {
                Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween,
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Column(modifier = Modifier.weight(1f)) {
                            Text(chain.name, style = MaterialTheme.typography.titleMedium, color = if (isSelected) primaryColor else MaterialTheme.colorScheme.onSurface)
                            Text(
                                "${steps.size} steps · Target: ${chain.outputTarget}",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldAccent
                            )
                        }
                        Row {
                            FilterChip(
                                selected = isSelected,
                                onClick = { selectedChainId = chain.id },
                                label = { Text(if (isSelected) "Selected" else "Select") }
                            )
                            if (!chain.isBuiltIn) {
                                IconButton(onClick = { viewModel.deletePromptChain(chain) }) {
                                    Icon(Icons.Filled.Delete, contentDescription = "Delete Workflow", tint = CrimsonDanger)
                                }
                            }
                        }
                    }
                    Text(chain.description, style = MaterialTheme.typography.bodyMedium, color = TextSecondary)
                    steps.forEach { step ->
                        Text(
                            text = "Step ${step.stepIndex}: ${step.title}",
                            style = MaterialTheme.typography.labelMedium,
                            color = TextSecondary
                        )
                    }
                }
            }
        }

        // Runner Controls
        if (selectedChain != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = AmoledSurface),
                    border = BorderStroke(1.dp, AmoledBorder)
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                        Text("Run: ${selectedChain.name}", style = MaterialTheme.typography.titleMedium, color = primaryColor)
                        OutlinedTextField(
                            value = initialInput,
                            onValueChange = { initialInput = it },
                            label = { Text("Initial {{input}} Topic or Idea") },
                            placeholder = { Text("e.g., How quantum tunneling powers modern flash memory") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        if (isRunning) {
                            LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                            Text(
                                "Running Step ${activeStepIndex + 1}...",
                                style = MaterialTheme.typography.labelMedium,
                                color = EmeraldAccent
                            )
                        }
                        Button(
                            onClick = { viewModel.runPromptChain(selectedChain, initialInput) },
                            enabled = !isRunning && initialInput.isNotBlank(),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Icon(Icons.Filled.PlayArrow, contentDescription = null)
                            Spacer(modifier = Modifier.width(6.dp))
                            Text(if (isRunning) "Executing Chain..." else "Execute Multi-Step Pipeline")
                        }
                    }
                }
            }
        }

        // Live Step Outputs
        if (stepOutputs.isNotEmpty()) {
            item {
                HorizontalDivider(color = AmoledBorder)
                Text("Pipeline Outputs (${stepOutputs.size} completed)", style = MaterialTheme.typography.titleMedium)
            }
            items(stepOutputs) { (stepTitle, output) ->
                Surface(
                    color = AmoledSurface,
                    border = BorderStroke(1.dp, AmoledBorder),
                    shape = RoundedCornerShape(10.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(stepTitle, style = MaterialTheme.typography.titleSmall, color = EmeraldAccent)
                        Text(
                            text = output,
                            style = MaterialTheme.typography.bodyMedium,
                            fontFamily = if (output.trim().startsWith("{")) FontFamily.Monospace else FontFamily.Default
                        )
                    }
                }
            }

            val finalOutput = stepOutputs.lastOrNull()?.second.orEmpty()
            if (selectedChain?.outputTarget == "STORYBOARD" || finalOutput.contains("\"scenes\"")) {
                item {
                    Button(
                        onClick = {
                            val imported = viewModel.importStoryboardJsonToDirector(finalOutput)
                            if (imported) {
                                onOpenDirector()
                            }
                        },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Icon(Icons.Filled.MovieCreation, contentDescription = null)
                        Spacer(modifier = Modifier.width(6.dp))
                        Text("Load Final JSON Storyboard into Tab 2 (Video Director)")
                    }
                }
            }
        }
    }

    if (showCreateDialog) {
        CreatePromptChainDialog(
            onDismiss = { showCreateDialog = false },
            onSave = { name, desc, target, steps ->
                viewModel.saveCustomPromptChain(name, desc, target, steps)
                showCreateDialog = false
            }
        )
    }
}

@Composable
private fun CreatePromptChainDialog(
    onDismiss: () -> Unit,
    onSave: (name: String, description: String, outputTarget: String, steps: List<PromptChainStep>) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var outputTarget by remember { mutableStateOf("CHAT") }
    var step1Title by remember { mutableStateOf("Step 1: Outline") }
    var step1Prompt by remember { mutableStateOf("Create a concise structured outline for: {{input}}") }
    var step2Title by remember { mutableStateOf("Step 2: Refine & Expand") }
    var step2Prompt by remember { mutableStateOf("Expand this outline into polished actionable output:\n{{prev_output}}") }
    var step3Title by remember { mutableStateOf("Step 3: Final Deliverable") }
    var step3Prompt by remember { mutableStateOf("Turn the draft below into a punchy final summary with key takeaways:\n{{prev_output}}") }

    AlertDialog(
        onDismissRequest = onDismiss,
        containerColor = AmoledSurface,
        title = { Text("Create Prompt Chain Workflow") },
        text = {
            LazyColumn(
                modifier = Modifier.height(380.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                item {
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Workflow Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        FilterChip(
                            selected = outputTarget == "CHAT",
                            onClick = { outputTarget = "CHAT" },
                            label = { Text("Text Output") }
                        )
                        FilterChip(
                            selected = outputTarget == "STORYBOARD",
                            onClick = { outputTarget = "STORYBOARD" },
                            label = { Text("JSON -> Tab 2") }
                        )
                    }
                }
                item {
                    OutlinedTextField(
                        value = step1Title,
                        onValueChange = { step1Title = it },
                        label = { Text("Step 1 Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = step1Prompt,
                        onValueChange = { step1Prompt = it },
                        label = { Text("Step 1 Prompt (use {{input}})") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = step2Title,
                        onValueChange = { step2Title = it },
                        label = { Text("Step 2 Title") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = step2Prompt,
                        onValueChange = { step2Prompt = it },
                        label = { Text("Step 2 Prompt (use {{prev_output}})") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                item {
                    OutlinedTextField(
                        value = step3Title,
                        onValueChange = { step3Title = it },
                        label = { Text("Step 3 Title (Optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = step3Prompt,
                        onValueChange = { step3Prompt = it },
                        label = { Text("Step 3 Prompt (use {{prev_output}})") },
                        minLines = 2,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val steps = buildList {
                        if (step1Prompt.isNotBlank()) add(PromptChainStep(1, step1Title.ifBlank { "Step 1" }, promptTemplate = step1Prompt))
                        if (step2Prompt.isNotBlank()) add(PromptChainStep(2, step2Title.ifBlank { "Step 2" }, promptTemplate = step2Prompt))
                        if (step3Prompt.isNotBlank()) add(PromptChainStep(3, step3Title.ifBlank { "Step 3" }, promptTemplate = step3Prompt))
                    }
                    if (name.isNotBlank() && steps.isNotEmpty()) {
                        onSave(name.trim(), description.trim().ifBlank { "Custom prompt pipeline" }, outputTarget, steps)
                    }
                },
                enabled = name.isNotBlank() && step1Prompt.isNotBlank()
            ) {
                Text("Save Workflow")
            }
        },
        dismissButton = {
            OutlinedButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
