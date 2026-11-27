package com.omniai.studio.ui.components

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.unit.dp
import com.omniai.studio.data.local.WorkspaceProfileEntity

@Composable
fun WorkspaceProfileManagerDialog(
    profiles: List<WorkspaceProfileEntity>,
    activeProfileId: String,
    onSwitch: (String) -> Unit,
    onSave: (id: String, name: String, description: String) -> Unit,
    onDismiss: () -> Unit
) {
    var editingProfile by remember { mutableStateOf<WorkspaceProfileEntity?>(null) }
    var draftName by remember(editingProfile) { mutableStateOf(editingProfile?.name.orEmpty()) }
    var draftDescription by remember(editingProfile) { mutableStateOf(editingProfile?.description.orEmpty()) }

    if (editingProfile == null) {
        AlertDialog(
            onDismissRequest = onDismiss,
            title = { Text("Manage Workspace Profiles") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        "Switching profiles keeps each workspace's chats and research context separate.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    LazyColumn(
                        modifier = Modifier
                            .fillMaxWidth()
                            .heightIn(max = 340.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(profiles, key = { it.id }) { profile ->
                            Row(
                                modifier = Modifier.fillMaxWidth(),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(profile.name, style = MaterialTheme.typography.titleSmall)
                                    if (profile.description.isNotBlank()) {
                                        Text(
                                            profile.description,
                                            style = MaterialTheme.typography.bodySmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    if (profile.id == activeProfileId) {
                                        Text(
                                            "Active",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.primary
                                        )
                                    }
                                }
                                IconButton(onClick = { editingProfile = profile }) {
                                    Icon(Icons.Filled.Edit, contentDescription = "Edit ${profile.name}")
                                }
                                TextButton(onClick = {
                                    onSwitch(profile.id)
                                    onDismiss()
                                }) {
                                    Text("Switch")
                                }
                            }
                        }
                    }
                }
            },
            confirmButton = {
                OutlinedButton(onClick = onDismiss) { Text("Close") }
            }
        )
    } else {
        val profile = editingProfile!!
        AlertDialog(
            onDismissRequest = { editingProfile = null },
            title = { Text("Edit Workspace Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = draftName,
                        onValueChange = { draftName = it },
                        label = { Text("Profile name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = draftDescription,
                        onValueChange = { draftDescription = it },
                        label = { Text("Purpose and notes") },
                        minLines = 2,
                        maxLines = 4,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        onSave(profile.id, draftName, draftDescription)
                        editingProfile = null
                    },
                    enabled = draftName.isNotBlank()
                ) {
                    Text("Save")
                }
            },
            dismissButton = {
                TextButton(onClick = { editingProfile = null }) {
                    Text("Cancel")
                }
            }
        )
    }
}
