package com.omniai.studio.ui.screens

import androidx.compose.foundation.layout.Arrangement
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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Cloud
import androidx.compose.material.icons.filled.Login
import androidx.compose.material.icons.filled.Person
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.AiPurposeMode
import com.omniai.studio.viewmodel.OmniAiViewModel

@Composable
fun CloudLoginScreen(
    viewModel: OmniAiViewModel,
    isSigningIn: Boolean,
    onContinueOffline: () -> Unit
) {
    val context = LocalContext.current
    val session by viewModel.cloudSessionState.collectAsState()
    val workspaces by viewModel.workspaces.collectAsState()
    val activeWorkspaceId by viewModel.activeWorkspaceId.collectAsState()
    val appSettings by viewModel.appSettings.collectAsState()
    val toastMessage by viewModel.toastMessage.collectAsState()
    val colorScheme = MaterialTheme.colorScheme

    Surface(
        modifier = Modifier.fillMaxSize(),
        color = colorScheme.background
    ) {
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(horizontal = 24.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.Center
        ) {
            item {
                Column(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalAlignment = Alignment.CenterHorizontally,
                    verticalArrangement = Arrangement.spacedBy(14.dp)
                ) {
                    Surface(
                        shape = RoundedCornerShape(22.dp),
                        color = colorScheme.primaryContainer
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Cloud,
                            contentDescription = null,
                            tint = colorScheme.primary,
                            modifier = Modifier.padding(18.dp).size(36.dp)
                        )
                    }
                    Text(
                        text = "Welcome to OmniAI Studio",
                        style = MaterialTheme.typography.headlineSmall,
                        fontWeight = FontWeight.Bold
                    )
                    Text(
                        text = "Sign in to sync your workspace and use your assigned cloud role. Your local profiles and data remain available in offline mode.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = colorScheme.onSurfaceVariant
                    )

                    Card(
                        modifier = Modifier.fillMaxWidth(),
                        colors = CardDefaults.cardColors(containerColor = colorScheme.surface)
                    ) {
                        Column(
                            modifier = Modifier.padding(16.dp),
                            verticalArrangement = Arrangement.spacedBy(12.dp)
                        ) {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = colorScheme.primary)
                                Text("Choose a workspace profile", style = MaterialTheme.typography.titleMedium)
                            }
                            Text(
                                text = "Profiles keep chats, research, and projects organized separately.",
                                style = MaterialTheme.typography.bodySmall,
                                color = colorScheme.onSurfaceVariant
                            )
                            if (workspaces.isEmpty()) {
                                Text(
                                    text = "Your Personal, Creator Studio, and Research Lab profiles will be ready during setup.",
                                    style = MaterialTheme.typography.bodySmall,
                                    color = colorScheme.onSurfaceVariant
                                )
                            } else {
                                workspaces.forEach { profile ->
                                    FilterChip(
                                        selected = profile.id == activeWorkspaceId,
                                        onClick = { viewModel.switchWorkspace(profile.id) },
                                        label = {
                                            Column {
                                                Text(profile.name)
                                                if (profile.description.isNotBlank()) {
                                                    Text(
                                                        profile.description,
                                                        style = MaterialTheme.typography.labelSmall,
                                                        color = colorScheme.onSurfaceVariant
                                                    )
                                                }
                                            }
                                        },
                                        modifier = Modifier.fillMaxWidth()
                                    )
                                }
                            }
                            Text(
                                text = "Purpose: ${appSettings.aiPurposeMode.label}",
                                style = MaterialTheme.typography.labelMedium,
                                color = colorScheme.onSurfaceVariant
                            )
                        }
                    }

                    toastMessage?.let {
                        Text(it, color = colorScheme.error, style = MaterialTheme.typography.bodySmall)
                    }
                    if (!session.isConfigured) {
                        Text(
                            text = "Cloud sign-in is unavailable in this build. You can continue with local profiles.",
                            color = colorScheme.onSurfaceVariant,
                            style = MaterialTheme.typography.bodySmall
                        )
                    }

                    Button(
                        onClick = { viewModel.startGoogleCloudSignIn(context) },
                        enabled = session.isConfigured && !isSigningIn,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        if (isSigningIn) {
                            CircularProgressIndicator(modifier = Modifier.size(18.dp), strokeWidth = 2.dp)
                        } else {
                            Icon(Icons.Filled.Login, contentDescription = null, modifier = Modifier.size(18.dp))
                        }
                        Spacer(modifier = Modifier.width(8.dp))
                        Text(if (isSigningIn) "Opening secure sign-in..." else "Sign in with Google")
                    }
                    OutlinedButton(
                        onClick = onContinueOffline,
                        modifier = Modifier.fillMaxWidth(),
                        shape = RoundedCornerShape(12.dp)
                    ) {
                        Text("Continue offline")
                    }
                    Spacer(modifier = Modifier.height(16.dp))
                }
            }
        }
    }
}
