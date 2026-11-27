package com.omniai.studio.ui.screens

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.PasswordVisualTransformation
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.AiPurposeMode
import com.omniai.studio.data.local.WorkspaceProfileEntity
import com.omniai.studio.viewmodel.OmniAiViewModel

private data class SetupProvider(
    val id: String,
    val label: String,
    val baseUrl: String,
    val model: String,
    val requiresApiKey: Boolean = true
)

private val setupProviders = listOf(
    SetupProvider("deepseek", "DeepSeek", "https://api.deepseek.com", "deepseek-chat"),
    SetupProvider("openai", "OpenAI", "https://api.openai.com/v1", "gpt-4o-mini"),
    SetupProvider(
        "gemini",
        "Gemini",
        "https://generativelanguage.googleapis.com/v1beta/openai",
        "gemini-2.5-flash"
    ),
    SetupProvider("openrouter", "OpenRouter", "https://openrouter.ai/api/v1", "deepseek/deepseek-chat"),
    SetupProvider("groq", "Groq", "https://api.groq.com/openai/v1", "llama-3.3-70b-versatile"),
    SetupProvider("huggingface", "Hugging Face", "https://router.huggingface.co/v1", "Qwen/Qwen2.5-72B-Instruct"),
    SetupProvider("ollama", "Local Ollama", "http://127.0.0.1:8080/v1", "llama3.1:8b", requiresApiKey = false),
    SetupProvider("custom", "Custom API", "", "", requiresApiKey = true)
)

private val setupProfiles = listOf(
    WorkspaceProfileEntity("default", "Personal", "General AI, chat, and everyday tasks."),
    WorkspaceProfileEntity("creator", "Creator Studio", "Video scripts, characters, and publishing."),
    WorkspaceProfileEntity("research", "Research Lab", "Research, documents, and data analysis.")
)

@Composable
fun InitialSetupScreen(
    viewModel: OmniAiViewModel,
    onCompleted: () -> Unit
) {
    val snapshot by viewModel.vaultSnapshot.collectAsState()
    var step by rememberSaveable { mutableIntStateOf(0) }
    var displayName by rememberSaveable { mutableStateOf("") }
    var workspaceId by rememberSaveable { mutableStateOf("default") }
    var purpose by rememberSaveable { mutableStateOf(AiPurposeMode.GENERAL.name) }
    val initialProviderId = setupProviders.firstOrNull { it.baseUrl == snapshot.primaryTunnelUrl }?.id
        ?: if (snapshot.primaryTunnelUrl.isBlank()) "deepseek" else "custom"
    var providerId by rememberSaveable { mutableStateOf(initialProviderId) }
    var endpoint by rememberSaveable { mutableStateOf(snapshot.primaryTunnelUrl.ifBlank { "https://api.deepseek.com" }) }
    var model by rememberSaveable {
        mutableStateOf(
            snapshot.primaryModelName.takeIf { snapshot.primaryTunnelUrl.isNotBlank() && it.isNotBlank() }
                ?: "deepseek-chat"
        )
    }
    var apiKey by remember { mutableStateOf("") }
    var error by rememberSaveable { mutableStateOf<String?>(null) }

    val selectedProvider = setupProviders.firstOrNull { it.id == providerId } ?: setupProviders.last()
    val selectedPurpose = AiPurposeMode.fromStoredName(purpose)

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = MaterialTheme.colorScheme.background
    ) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 20.dp, vertical = 28.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text(
                "Welcome to OmniAI Studio",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold,
                color = MaterialTheme.colorScheme.primary
            )
            Text(
                if (step == 0) "First, set up your profile and choose what you want to use the app for."
                else "Connect an AI provider. Your key is stored in the encrypted local vault.",
                style = MaterialTheme.typography.bodyLarge,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                SetupStepPill("1 · Profile", selected = step == 0)
                SetupStepPill("2 · AI provider", selected = step == 1)
            }

            if (step == 0) {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(14.dp)
                    ) {
                        OutlinedTextField(
                            value = displayName,
                            onValueChange = { displayName = it.take(80); error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Your name") },
                            singleLine = true
                        )
                        Text("Choose a workspace profile", style = MaterialTheme.typography.titleMedium)
                        setupProfiles.forEach { profile ->
                            val selected = workspaceId == profile.id
                            Surface(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .clickable { workspaceId = profile.id },
                                shape = RoundedCornerShape(14.dp),
                                color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surface,
                                border = BorderStroke(
                                    1.dp,
                                    if (selected) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.outline
                                )
                            ) {
                                Row(
                                    modifier = Modifier.padding(14.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(profile.name, fontWeight = FontWeight.SemiBold)
                                        Text(profile.description, style = MaterialTheme.typography.bodySmall)
                                    }
                                    if (selected) Icon(Icons.Filled.Check, contentDescription = "Selected")
                                }
                            }
                        }
                        HorizontalDivider()
                        Text("AI purpose", style = MaterialTheme.typography.titleMedium)
                        AiPurposeMode.entries.forEach { mode ->
                            FilterChip(
                                selected = selectedPurpose == mode,
                                onClick = { purpose = mode.name },
                                label = { Text(mode.label) }
                            )
                        }
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Button(
                    onClick = {
                        error = if (displayName.isBlank()) "Enter your name to continue." else null
                        if (error == null) step = 1
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text("Continue to API setup")
                }
            } else {
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface)) {
                    Column(
                        modifier = Modifier.padding(16.dp),
                        verticalArrangement = Arrangement.spacedBy(12.dp)
                    ) {
                        Text("Select your AI provider", style = MaterialTheme.typography.titleMedium)
                        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            items(setupProviders) { provider ->
                                FilterChip(
                                    selected = providerId == provider.id,
                                    onClick = {
                                        providerId = provider.id
                                        endpoint = provider.baseUrl
                                        model = provider.model
                                        apiKey = ""
                                        error = null
                                    },
                                    label = { Text(provider.label) }
                                )
                            }
                        }
                        OutlinedTextField(
                            value = endpoint,
                            onValueChange = { endpoint = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("API base URL") },
                            singleLine = true
                        )
                        OutlinedTextField(
                            value = model,
                            onValueChange = { model = it; error = null },
                            modifier = Modifier.fillMaxWidth(),
                            label = { Text("Model ID") },
                            singleLine = true
                        )
                        if (selectedProvider.requiresApiKey) {
                            OutlinedTextField(
                                value = apiKey,
                                onValueChange = { apiKey = it; error = null },
                                modifier = Modifier.fillMaxWidth(),
                                label = { Text("API key") },
                                singleLine = true,
                                visualTransformation = PasswordVisualTransformation(),
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) }
                            )
                        } else {
                            Text(
                                "Local Ollama usually does not need an API key. Keep the server running and reachable at this URL.",
                                style = MaterialTheme.typography.bodySmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        Text(
                            "Use a provider-issued API key. ChatGPT/other consumer subscriptions do not automatically include API credits. API keys are encrypted on this device.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                error?.let { Text(it, color = MaterialTheme.colorScheme.error) }
                Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedButton(
                        onClick = { step = 0; error = null },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Back")
                    }
                    Button(
                        onClick = {
                            val validationError = when {
                                endpoint.isBlank() -> "Enter the provider API base URL."
                                model.isBlank() -> "Enter a model ID."
                                selectedProvider.requiresApiKey && apiKey.isBlank() -> "Enter an API key to continue."
                                else -> null
                            }
                            if (validationError != null) {
                                error = validationError
                                return@Button
                            }

                            error = viewModel.saveVaultConfiguration(
                                snapshot.copy(
                                    primaryTunnelUrl = endpoint.trim(),
                                    primaryTunnelApiKey = apiKey.trim(),
                                    primaryModelName = model.trim()
                                )
                            )
                            if (error == null) {
                                error = viewModel.completeInitialSetup(
                                    displayName = displayName,
                                    workspaceId = workspaceId,
                                    purposeMode = selectedPurpose
                                )
                                if (error == null) {
                                    apiKey = ""
                                    onCompleted()
                                }
                            }
                        },
                        modifier = Modifier.weight(1f)
                    ) {
                        Text("Save & start")
                    }
                }
            }
        }
    }
}

@Composable
private fun SetupStepPill(label: String, selected: Boolean) {
    Surface(
        shape = RoundedCornerShape(50),
        color = if (selected) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
    ) {
        Text(
            label,
            modifier = Modifier.padding(horizontal = 12.dp, vertical = 7.dp),
            style = MaterialTheme.typography.labelMedium,
            color = if (selected) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}
