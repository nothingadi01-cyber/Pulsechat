package com.example.ui.screens

import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
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
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.Add
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.NotificationsActive
import androidx.compose.material.icons.filled.PostAdd
import androidx.compose.material.icons.filled.Verified
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import coil.compose.AsyncImage
import com.example.data.model.ChannelFeed
import com.example.data.model.StatusUpdate

@Composable
fun UpdatesTabScreen(
    statuses: List<StatusUpdate>,
    channels: List<ChannelFeed>,
    onPostStatus: (caption: String, bgStyle: String, mediaUri: String) -> Unit,
    onReactStatus: (StatusUpdate, String) -> Unit,
    onDeleteStatus: (String) -> Unit,
    onCreateChannel: (name: String, category: String, updateText: String) -> Unit,
    onPostChannelUpdate: (channelId: String, updateText: String) -> Unit,
    onToggleChannelMute: (ChannelFeed) -> Unit,
    onDeleteChannel: (String) -> Unit
) {
    var showStatusDialog by remember { mutableStateOf(false) }
    var showChannelDialog by remember { mutableStateOf(false) }
    var selectedStatus by remember { mutableStateOf<StatusUpdate?>(null) }
    var postToChannel by remember { mutableStateOf<ChannelFeed?>(null) }

    Box(modifier = Modifier.fillMaxSize()) {
        LazyColumn(
            modifier = Modifier.fillMaxSize(),
            contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Encrypted Status", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showStatusDialog = true }) {
                        Text("+ New Status")
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(12.dp)
                ) {
                    Surface(
                        color = MaterialTheme.colorScheme.primaryContainer,
                        shape = RoundedCornerShape(20.dp),
                        modifier = Modifier
                            .size(width = 112.dp, height = 148.dp)
                            .clickable { showStatusDialog = true }
                            .testTag("add_status_card")
                    ) {
                        Column(
                            modifier = Modifier.padding(12.dp),
                            verticalArrangement = Arrangement.Center,
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Box(
                                modifier = Modifier
                                    .size(44.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(Icons.Filled.Add, contentDescription = "Add status", tint = MaterialTheme.colorScheme.onPrimary)
                            }
                            Spacer(Modifier.height(10.dp))
                            Text(
                                text = "Share Status",
                                style = MaterialTheme.typography.labelLarge,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                        }
                    }

                    statuses.forEach { status ->
                        StatusCardItem(
                            status = status,
                            onClick = {
                                onReactStatus(status, status.reactionEmoji)
                                selectedStatus = status
                            }
                        )
                    }
                }
            }

            item {
                Row(
                    modifier = Modifier.fillMaxWidth(),
                    horizontalArrangement = Arrangement.SpaceBetween,
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Text("Channels & Broadcasts", style = MaterialTheme.typography.titleLarge)
                    TextButton(onClick = { showChannelDialog = true }) {
                        Text("+ Create Channel")
                    }
                }
            }

            items(channels, key = { it.id }) { channel ->
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Box(
                                modifier = Modifier
                                    .size(40.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.secondaryContainer),
                                contentAlignment = Alignment.Center
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Campaign,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.onSecondaryContainer
                                )
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(modifier = Modifier.weight(1f)) {
                                Row(verticalAlignment = Alignment.CenterVertically) {
                                    Text(
                                        text = channel.name,
                                        style = MaterialTheme.typography.titleMedium,
                                        maxLines = 1,
                                        overflow = TextOverflow.Ellipsis,
                                        modifier = Modifier.weight(1f, fill = false)
                                    )
                                    if (channel.verified) {
                                        Spacer(Modifier.width(4.dp))
                                        Icon(
                                            imageVector = Icons.Filled.Verified,
                                            contentDescription = "Verified channel",
                                            tint = MaterialTheme.colorScheme.primary,
                                            modifier = Modifier.size(16.dp)
                                        )
                                    }
                                }
                                Text(
                                    text = "${channel.category} • ${channel.followersLabel}" + if (channel.muted) " • Muted" else "",
                                    style = MaterialTheme.typography.labelSmall,
                                    color = MaterialTheme.colorScheme.onSurfaceVariant
                                )
                            }
                            IconButton(onClick = { postToChannel = channel }) {
                                Icon(Icons.Filled.PostAdd, contentDescription = "Post broadcast update")
                            }
                            IconButton(onClick = { onToggleChannelMute(channel) }) {
                                Icon(
                                    imageVector = if (channel.muted) Icons.Filled.NotificationsOff else Icons.Filled.NotificationsActive,
                                    contentDescription = "Toggle channel mute"
                                )
                            }
                            IconButton(onClick = { onDeleteChannel(channel.id) }) {
                                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete channel", tint = MaterialTheme.colorScheme.error)
                            }
                        }
                        Spacer(Modifier.height(10.dp))
                        Text(
                            text = channel.latestUpdate,
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurface
                        )
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showStatusDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier.align(Alignment.BottomEnd).padding(20.dp)
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Post status")
        }
    }

    if (showStatusDialog) {
        PostStatusDialog(
            onDismiss = { showStatusDialog = false },
            onPost = { caption, bg, media ->
                showStatusDialog = false
                onPostStatus(caption, bg, media)
            }
        )
    }

    if (showChannelDialog) {
        CreateChannelDialog(
            onDismiss = { showChannelDialog = false },
            onCreate = { name, cat, text ->
                showChannelDialog = false
                onCreateChannel(name, cat, text)
            }
        )
    }

    postToChannel?.let { ch ->
        var announcement by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { postToChannel = null },
            title = { Text("Post to ${ch.name}") },
            text = {
                OutlinedTextField(
                    value = announcement,
                    onValueChange = { announcement = it },
                    label = { Text("New channel announcement") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        onPostChannelUpdate(ch.id, announcement)
                        postToChannel = null
                    },
                    enabled = announcement.isNotBlank()
                ) { Text("Broadcast") }
            },
            dismissButton = { TextButton(onClick = { postToChannel = null }) { Text("Cancel") } }
        )
    }

    selectedStatus?.let { st ->
        AlertDialog(
            onDismissRequest = { selectedStatus = null },
            title = { Text("${st.authorName}'s Status") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    if (st.mediaUri.isNotBlank()) {
                        AsyncImage(
                            model = st.mediaUri,
                            contentDescription = "Status image",
                            contentScale = ContentScale.Crop,
                            modifier = Modifier.fillMaxWidth().height(200.dp).clip(RoundedCornerShape(14.dp))
                        )
                    }
                    Text(st.caption, style = MaterialTheme.typography.bodyLarge)
                    Text(
                        text = "Views: ${st.viewCount + 1L} • Reaction: ${st.reactionEmoji.ifBlank { "None" }}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                        listOf("❤️", "🔥", "🎉", "🔐", "👏").forEach { emoji ->
                            FilterChip(
                                selected = st.reactionEmoji == emoji,
                                onClick = {
                                    onReactStatus(st, emoji)
                                    selectedStatus = st.copy(reactionEmoji = emoji)
                                },
                                label = { Text(emoji) }
                            )
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { selectedStatus = null }) { Text("Close") }
            },
            dismissButton = {
                TextButton(onClick = {
                    onDeleteStatus(st.id)
                    selectedStatus = null
                }) { Text("Delete", color = MaterialTheme.colorScheme.error) }
            }
        )
    }
}

@Composable
private fun StatusCardItem(status: StatusUpdate, onClick: () -> Unit) {
    val gradient = when (status.bgStyle) {
        "cyan" -> listOf(Color(0xFF0C4A6E), Color(0xFF0284C7))
        "amber" -> listOf(Color(0xFF78350F), Color(0xFFD97706))
        else -> listOf(Color(0xFF064E3B), Color(0xFF10B981))
    }

    Box(
        modifier = Modifier
            .size(width = 120.dp, height = 148.dp)
            .clip(RoundedCornerShape(20.dp))
            .background(Brush.linearGradient(gradient))
            .clickable(onClick = onClick)
            .padding(12.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxSize(),
            verticalArrangement = Arrangement.SpaceBetween
        ) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween
            ) {
                Text(
                    text = status.authorName,
                    style = MaterialTheme.typography.labelLarge,
                    color = Color.White,
                    maxLines = 1,
                    overflow = TextOverflow.Ellipsis,
                    modifier = Modifier.weight(1f)
                )
                if (status.reactionEmoji.isNotBlank()) {
                    Text(status.reactionEmoji, style = MaterialTheme.typography.labelLarge)
                }
            }
            Text(
                text = status.caption,
                style = MaterialTheme.typography.bodySmall,
                color = Color.White.copy(alpha = 0.92f),
                maxLines = 4,
                overflow = TextOverflow.Ellipsis,
                fontWeight = FontWeight.Medium
            )
        }
    }
}

@Composable
private fun PostStatusDialog(
    onDismiss: () -> Unit,
    onPost: (String, String, String) -> Unit
) {
    var caption by remember { mutableStateOf("") }
    var bgStyle by remember { mutableStateOf("emerald") }
    var pickedUri by remember { mutableStateOf("") }

    val picker = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) pickedUri = uri.toString()
    }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Share Encrypted Status") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = caption,
                    onValueChange = { caption = it },
                    label = { Text("What's on your mind?") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("emerald" to "Emerald", "cyan" to "Cyan", "amber" to "Amber").forEach { (key, label) ->
                        FilterChip(selected = bgStyle == key, onClick = { bgStyle = key }, label = { Text(label) })
                    }
                }
                OutlinedButton(
                    onClick = { picker.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly)) },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.AddPhotoAlternate, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text(if (pickedUri.isBlank()) "Attach Photo (Photo Picker)" else "Photo Attached ✓")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onPost(caption.ifBlank { "Sharing a moment on PulseTalk" }, bgStyle, pickedUri) }) {
                Text("Post Status")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
private fun CreateChannelDialog(
    onDismiss: () -> Unit,
    onCreate: (String, String, String) -> Unit
) {
    var name by remember { mutableStateOf("") }
    var category by remember { mutableStateOf("Tech") }
    var updateText by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Create Broadcast Channel") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Channel Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = category, onValueChange = { category = it }, label = { Text("Category") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(value = updateText, onValueChange = { updateText = it }, label = { Text("First Announcement") }, maxLines = 3, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = { if (name.isNotBlank()) onCreate(name, category, updateText) },
                enabled = name.isNotBlank()
            ) { Text("Publish") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}
