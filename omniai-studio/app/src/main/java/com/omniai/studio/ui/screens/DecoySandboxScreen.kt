package com.omniai.studio.ui.screens

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
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Note
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp

private data class DecoyNoteItem(
    val id: Long,
    val title: String,
    val body: String
)

/**
 * Harmless, completely isolated "Quick Notes" sandbox displayed when the user unlocks
 * with their Duress / Decoy PIN. Has zero access to Vault keys, real Room chats, or videos.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DecoySandboxScreen(
    onLockAgain: () -> Unit
) {
    val notes = remember {
        mutableStateListOf(
            DecoyNoteItem(1L, "Grocery List", "Milk, bread, coffee beans, bananas, olive oil"),
            DecoyNoteItem(2L, "Weekend Errands", "Pick up laundry at 4 PM, renew gym pass")
        )
    }
    var showComposer by remember { mutableStateOf(false) }
    var draftTitle by remember { mutableStateOf("") }
    var draftBody by remember { mutableStateOf("") }

    Scaffold(
        containerColor = Color(0xFF0A0A0F),
        topBar = {
            TopAppBar(
                title = {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Default.Note,
                            contentDescription = null,
                            tint = Color(0xFFF59E0B)
                        )
                        Spacer(modifier = Modifier.padding(horizontal = 4.dp))
                        Text("Quick Notes", fontWeight = FontWeight.SemiBold)
                    }
                },
                actions = {
                    IconButton(onClick = onLockAgain) {
                        Icon(
                            imageVector = Icons.Default.Lock,
                            contentDescription = "Lock Notes",
                            tint = Color(0xFF94A3B8)
                        )
                    }
                },
                colors = TopAppBarDefaults.topAppBarColors(
                    containerColor = Color(0xFF11131A),
                    titleContentColor = Color.White
                )
            )
        },
        floatingActionButton = {
            FloatingActionButton(
                onClick = { showComposer = !showComposer },
                containerColor = Color(0xFFF59E0B),
                contentColor = Color.Black
            ) {
                Icon(Icons.Default.Add, contentDescription = "New Note")
            }
        }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .background(Color(0xFF0A0A0F))
                .padding(padding)
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            if (showComposer) {
                Card(
                    colors = CardDefaults.cardColors(containerColor = Color(0xFF151822)),
                    shape = RoundedCornerShape(14.dp)
                ) {
                    Column(
                        modifier = Modifier.padding(14.dp),
                        verticalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        OutlinedTextField(
                            value = draftTitle,
                            onValueChange = { draftTitle = it },
                            label = { Text("Title") },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        OutlinedTextField(
                            value = draftBody,
                            onValueChange = { draftBody = it },
                            label = { Text("Note") },
                            minLines = 2,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.End
                        ) {
                            TextButton(onClick = {
                                draftTitle = ""
                                draftBody = ""
                                showComposer = false
                            }) {
                                Text("Cancel")
                            }
                            TextButton(onClick = {
                                if (draftTitle.isNotBlank() || draftBody.isNotBlank()) {
                                    notes.add(
                                        0,
                                        DecoyNoteItem(
                                            id = System.currentTimeMillis(),
                                            title = draftTitle.ifBlank { "Untitled Note" },
                                            body = draftBody.trim()
                                        )
                                    )
                                    draftTitle = ""
                                    draftBody = ""
                                    showComposer = false
                                }
                            }) {
                                Text("Save Note", color = Color(0xFFF59E0B))
                            }
                        }
                    }
                }
            }

            if (notes.isEmpty()) {
                Spacer(modifier = Modifier.height(48.dp))
                Text(
                    text = "No notes yet. Tap + to jot down a quick reminder.",
                    style = MaterialTheme.typography.bodyMedium,
                    color = Color(0xFF94A3B8)
                )
            } else {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    items(notes, key = { it.id }) { note ->
                        Card(
                            colors = CardDefaults.cardColors(containerColor = Color(0xFF141720)),
                            shape = RoundedCornerShape(14.dp)
                        ) {
                            Row(
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .padding(14.dp),
                                horizontalArrangement = Arrangement.SpaceBetween,
                                verticalAlignment = Alignment.Top
                            ) {
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = note.title,
                                        style = MaterialTheme.typography.titleMedium,
                                        color = Color.White,
                                        fontWeight = FontWeight.Medium
                                    )
                                    Spacer(modifier = Modifier.height(4.dp))
                                    Text(
                                        text = note.body,
                                        style = MaterialTheme.typography.bodyMedium,
                                        color = Color(0xFFCBD5E1)
                                    )
                                }
                                IconButton(onClick = { notes.remove(note) }) {
                                    Icon(
                                        imageVector = Icons.Default.Delete,
                                        contentDescription = "Delete Note",
                                        tint = Color(0xFF64748B)
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
