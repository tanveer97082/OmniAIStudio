package com.omniai.studio.youtube

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Refresh
import androidx.compose.material.icons.filled.Send
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateMapOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.omniai.studio.data.remote.YouTubeCommentItem
import com.omniai.studio.ui.theme.AmoledBorder
import com.omniai.studio.ui.theme.AmoledSurface
import com.omniai.studio.ui.theme.AmoledSurfaceVariant
import com.omniai.studio.ui.theme.TextSecondary

/**
 * YouTube AI Comment Reply Assistant Sheet (v5.3).
 * Accessible from Content DB -> YouTube tab.
 * Fetches latest unanswered comments via YouTube Data API v3, lets the creator generate
 * an on-brand AI reply draft per comment, edit it inline, and post directly to YouTube.
 */
@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CommentAssistantSheet(
    initialVideoId: String,
    comments: List<YouTubeCommentItem>,
    isLoading: Boolean,
    statusMessage: String,
    onFetchComments: (videoIdOrUrl: String, onlyUnanswered: Boolean) -> Unit,
    onGenerateAiDraft: (comment: YouTubeCommentItem, onDraftReady: (String) -> Unit) -> Unit,
    onPostReply: (comment: YouTubeCommentItem, replyText: String) -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var videoIdInput by remember(initialVideoId) { mutableStateOf(initialVideoId) }
    var onlyUnanswered by remember { mutableStateOf(true) }
    val draftReplies = remember { mutableStateMapOf<String, String>() }
    val draftingIds = remember { mutableStateMapOf<String, Boolean>() }

    LaunchedEffect(initialVideoId) {
        if (initialVideoId.isNotBlank()) {
            onFetchComments(initialVideoId, onlyUnanswered)
        }
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = AmoledSurface,
        contentColor = Color.White
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 16.dp)
                .navigationBarsPadding()
                .padding(bottom = 24.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                ) {
                    Icon(Icons.Default.Forum, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                    Column {
                        Text(
                            text = "YouTube Comment Assistant",
                            style = MaterialTheme.typography.titleMedium,
                            fontWeight = FontWeight.Bold,
                            color = Color.White
                        )
                        Text(
                            text = "Fetch unanswered comments, AI draft replies & post via API v3",
                            style = MaterialTheme.typography.bodySmall,
                            color = TextSecondary
                        )
                    }
                }
                IconButton(onClick = onDismiss) {
                    Icon(Icons.Default.Close, contentDescription = "Close", tint = TextSecondary)
                }
            }

            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                OutlinedTextField(
                    value = videoIdInput,
                    onValueChange = { videoIdInput = it },
                    label = { Text("YouTube Video ID or URL") },
                    placeholder = { Text("https://youtu.be/dQw4w9WgXcQ") },
                    singleLine = true,
                    modifier = Modifier.weight(1f)
                )
                Button(
                    enabled = videoIdInput.isNotBlank() && !isLoading,
                    onClick = { onFetchComments(videoIdInput, onlyUnanswered) },
                    modifier = Modifier.height(52.dp)
                ) {
                    Icon(Icons.Default.Refresh, contentDescription = null, modifier = Modifier.size(18.dp))
                }
            }

            Row(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilterChip(
                    selected = onlyUnanswered,
                    onClick = {
                        onlyUnanswered = true
                        if (videoIdInput.isNotBlank()) onFetchComments(videoIdInput, true)
                    },
                    label = { Text("Unanswered Only") }
                )
                FilterChip(
                    selected = !onlyUnanswered,
                    onClick = {
                        onlyUnanswered = false
                        if (videoIdInput.isNotBlank()) onFetchComments(videoIdInput, false)
                    },
                    label = { Text("All Recent") }
                )
            }

            if (statusMessage.isNotBlank()) {
                Text(
                    text = statusMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.primary
                )
            }

            if (isLoading) {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 20.dp),
                    horizontalArrangement = Arrangement.Center,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(modifier = Modifier.size(22.dp), strokeWidth = 2.dp)
                    Spacer(Modifier.width(10.dp))
                    Text("Loading YouTube comments...", color = TextSecondary)
                }
            } else if (comments.isEmpty()) {
                Surface(
                    shape = RoundedCornerShape(14.dp),
                    color = AmoledSurfaceVariant,
                    border = BorderStroke(1.dp, AmoledBorder),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = if (videoIdInput.isBlank()) {
                            "Paste a YouTube Video ID or published URL above to load viewer comments."
                        } else {
                            "No matching comments found for this video."
                        },
                        style = MaterialTheme.typography.bodySmall,
                        color = TextSecondary,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            } else {
                LazyColumn(
                    modifier = Modifier
                        .fillMaxWidth()
                        .height(380.dp),
                    verticalArrangement = Arrangement.spacedBy(10.dp)
                ) {
                    items(comments, key = { it.topLevelCommentId }) { comment ->
                        val replyText = draftReplies[comment.topLevelCommentId] ?: ""
                        val isDrafting = draftingIds[comment.topLevelCommentId] == true

                        Surface(
                            shape = RoundedCornerShape(14.dp),
                            color = AmoledSurfaceVariant,
                            border = BorderStroke(1.dp, AmoledBorder),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Column(
                                modifier = Modifier.padding(12.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.SpaceBetween
                                ) {
                                    Text(
                                        text = comment.authorDisplayName,
                                        style = MaterialTheme.typography.labelLarge,
                                        fontWeight = FontWeight.Bold,
                                        color = Color.White
                                    )
                                    Text(
                                        text = "♥ ${comment.likeCount} · ${comment.replyCount} replies",
                                        style = MaterialTheme.typography.labelSmall,
                                        color = TextSecondary
                                    )
                                }
                                Text(
                                    text = comment.textDisplay,
                                    style = MaterialTheme.typography.bodySmall,
                                    color = Color(0xFFE6E6F0)
                                )
                                OutlinedTextField(
                                    value = replyText,
                                    onValueChange = { draftReplies[comment.topLevelCommentId] = it },
                                    label = { Text("Your Reply") },
                                    minLines = 2,
                                    maxLines = 4,
                                    modifier = Modifier.fillMaxWidth()
                                )
                                Row(
                                    modifier = Modifier.fillMaxWidth(),
                                    horizontalArrangement = Arrangement.spacedBy(8.dp)
                                ) {
                                    OutlinedButton(
                                        enabled = !isDrafting,
                                        onClick = {
                                            draftingIds[comment.topLevelCommentId] = true
                                            onGenerateAiDraft(comment) { generated ->
                                                draftReplies[comment.topLevelCommentId] = generated
                                                draftingIds[comment.topLevelCommentId] = false
                                            }
                                        },
                                        modifier = Modifier.weight(1f)
                                    ) {
                                        Icon(Icons.Default.AutoAwesome, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text(if (isDrafting) "Drafting..." else "AI Draft Reply", fontSize = 12.sp)
                                    }
                                    Button(
                                        enabled = replyText.isNotBlank(),
                                        onClick = {
                                            onPostReply(comment, replyText)
                                            draftReplies[comment.topLevelCommentId] = ""
                                        },
                                        modifier = Modifier.weight(1f),
                                        colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.primary)
                                    ) {
                                        Icon(Icons.Default.Send, contentDescription = null, modifier = Modifier.size(16.dp))
                                        Spacer(Modifier.width(6.dp))
                                        Text("Post Reply", fontSize = 12.sp, fontWeight = FontWeight.Bold)
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
