package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import android.content.Intent
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
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
import androidx.compose.material.icons.filled.Block
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.PersonAdd
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Share
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.outlined.StarOutline
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FilterChip
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
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.core.E2eeCryptoManager
import com.example.data.model.ChatThread
import com.example.data.model.ContactRecord
import com.example.data.model.EncryptedMessage

@Composable
fun NewChatDialog(
    contacts: List<ContactRecord>,
    onDismiss: () -> Unit,
    onCreate: (title: String, type: String, peerUid: String?, extraMembers: List<String>, description: String) -> Unit
) {
    var title by remember { mutableStateOf("") }
    var description by remember { mutableStateOf("") }
    var selectedType by remember { mutableStateOf("direct") }
    var peerUid by remember { mutableStateOf("") }
    val extraMembers = remember { mutableStateListOf<String>() }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Start Encrypted Thread") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Row(
                    modifier = Modifier.horizontalScroll(rememberScrollState()),
                    horizontalArrangement = Arrangement.spacedBy(6.dp)
                ) {
                    listOf(
                        "direct" to "Direct",
                        "group" to "Group",
                        "community" to "Community",
                        "broadcast" to "Broadcast",
                        "vault" to "Vault"
                    ).forEach { (type, label) ->
                        FilterChip(
                            selected = selectedType == type,
                            onClick = { selectedType = type },
                            label = { Text(label) }
                        )
                    }
                }
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Chat / Group / Community Name") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                if (selectedType == "group" || selectedType == "community") {
                    OutlinedTextField(
                        value = description,
                        onValueChange = { description = it },
                        label = { Text("Description / Topic") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                if (selectedType == "direct") {
                    OutlinedTextField(
                        value = peerUid,
                        onValueChange = { peerUid = it },
                        label = { Text("Recipient Invite ID (optional)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                }
                val activeContacts = contacts.filter { !it.blocked }
                if (activeContacts.isNotEmpty() && selectedType != "vault") {
                    Text(
                        text = if (selectedType == "direct") "Quick pick contact:" else "Add members:",
                        style = MaterialTheme.typography.labelSmall
                    )
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        activeContacts.forEach { contact ->
                            val isSelected = if (selectedType == "direct") {
                                peerUid == contact.contactUid
                            } else {
                                extraMembers.contains(contact.contactUid)
                            }
                            FilterChip(
                                selected = isSelected,
                                onClick = {
                                    if (selectedType == "direct") {
                                        peerUid = contact.contactUid
                                        if (title.isBlank()) title = contact.displayName
                                    } else {
                                        if (extraMembers.contains(contact.contactUid)) {
                                            extraMembers.remove(contact.contactUid)
                                        } else {
                                            extraMembers.add(contact.contactUid)
                                        }
                                    }
                                },
                                label = { Text(contact.displayName) }
                            )
                        }
                    }
                }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    onCreate(
                        title.ifBlank { "Encrypted ${selectedType.replaceFirstChar { it.uppercase() }}" },
                        selectedType,
                        peerUid.takeIf { it.isNotBlank() },
                        extraMembers.toList(),
                        description
                    )
                }
            ) {
                Text("Create")
            }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun BroadcastComposeDialog(
    chats: List<ChatThread>,
    onDismiss: () -> Unit,
    onSendBroadcast: (List<ChatThread>, String) -> Unit
) {
    val selectedIds = remember { mutableStateListOf<String>() }
    var text by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.Campaign, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Encrypted Broadcast")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(
                    text = "Select recipients to send an individually encrypted copy to each thread:",
                    style = MaterialTheme.typography.bodySmall
                )
                LazyColumn(modifier = Modifier.height(150.dp)) {
                    items(chats, key = { it.id }) { chat ->
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier
                                .fillMaxWidth()
                                .clickable {
                                    if (selectedIds.contains(chat.id)) selectedIds.remove(chat.id)
                                    else selectedIds.add(chat.id)
                                }
                        ) {
                            Checkbox(
                                checked = selectedIds.contains(chat.id),
                                onCheckedChange = {
                                    if (it) selectedIds.add(chat.id) else selectedIds.remove(chat.id)
                                }
                            )
                            Text(chat.title, style = MaterialTheme.typography.bodyMedium)
                        }
                    }
                }
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    label = { Text("Broadcast message") },
                    maxLines = 3,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val targets = chats.filter { selectedIds.contains(it.id) }
                    onSendBroadcast(targets, text)
                },
                enabled = selectedIds.isNotEmpty() && text.isNotBlank()
            ) { Text("Send (${selectedIds.size})") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancel") } }
    )
}

@Composable
fun ContactsAndQrDialog(
    currentUserId: String,
    contacts: List<ContactRecord>,
    onDismiss: () -> Unit,
    onAddContact: (String, String) -> Unit,
    onToggleFavorite: (ContactRecord) -> Unit,
    onToggleBlocked: (ContactRecord) -> Unit,
    onDeleteContact: (String) -> Unit
) {
    val context = LocalContext.current
    var showAddForm by remember { mutableStateOf(false) }
    var uid by remember { mutableStateOf("") }
    var name by remember { mutableStateOf("") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(Icons.Filled.QrCode2, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                Spacer(Modifier.width(8.dp))
                Text("Contacts & Identity QR")
            }
        },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                // Visual Identity QR Matrix Card
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Row(
                        modifier = Modifier.padding(12.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IdentityQrMatrix(seed = currentUserId, modifier = Modifier.size(64.dp))
                        Spacer(Modifier.width(12.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text("Your PulseTalk QR Card", style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            Text(
                                text = currentUserId.take(18) + "…",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer
                            )
                            Row {
                                IconButton(
                                    onClick = {
                                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                                        cb?.setPrimaryClip(ClipData.newPlainText("PulseTalk ID", currentUserId))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.ContentCopy, contentDescription = "Copy ID", modifier = Modifier.size(16.dp))
                                }
                                IconButton(
                                    onClick = {
                                        val intent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(
                                                Intent.EXTRA_TEXT,
                                                "Add me on PulseTalk (E2EE Messenger by Anupam)! My Invite ID: $currentUserId"
                                            )
                                        }
                                        context.startActivity(Intent.createChooser(intent, "Share PulseTalk Invite"))
                                    },
                                    modifier = Modifier.size(32.dp)
                                ) {
                                    Icon(Icons.Filled.Share, contentDescription = "Share Invite", modifier = Modifier.size(16.dp))
                                }
                            }
                        }
                    }
                }

                OutlinedButton(
                    onClick = { showAddForm = !showAddForm },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.PersonAdd, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (showAddForm) "Hide Add Contact Form" else "Add New Contact by ID")
                }

                if (showAddForm) {
                    OutlinedTextField(
                        value = uid,
                        onValueChange = { uid = it },
                        label = { Text("Friend's Invite ID / UID") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    OutlinedTextField(
                        value = name,
                        onValueChange = { name = it },
                        label = { Text("Display Name") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (uid.isNotBlank()) {
                                onAddContact(uid, name)
                                uid = ""
                                name = ""
                                showAddForm = false
                            }
                        },
                        enabled = uid.isNotBlank(),
                        modifier = Modifier.align(Alignment.End)
                    ) { Text("Save Contact") }
                }

                if (contacts.isNotEmpty()) {
                    LazyColumn(
                        modifier = Modifier.height(170.dp),
                        verticalArrangement = Arrangement.spacedBy(6.dp)
                    ) {
                        items(contacts, key = { it.id }) { contact ->
                            Surface(
                                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                                shape = RoundedCornerShape(12.dp),
                                modifier = Modifier.fillMaxWidth()
                            ) {
                                Row(
                                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp),
                                    verticalAlignment = Alignment.CenterVertically
                                ) {
                                    Column(modifier = Modifier.weight(1f)) {
                                        Text(
                                            text = contact.displayName + if (contact.blocked) " (Blocked)" else "",
                                            style = MaterialTheme.typography.titleSmall
                                        )
                                        Text(
                                            text = "Safety: ${contact.safetyNumber.take(15)}…",
                                            style = MaterialTheme.typography.labelSmall,
                                            color = MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onToggleFavorite(contact) }, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            imageVector = if (contact.favorite) Icons.Filled.Star else Icons.Outlined.StarOutline,
                                            contentDescription = "Favorite contact",
                                            tint = if (contact.favorite) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onToggleBlocked(contact) }, modifier = Modifier.size(34.dp)) {
                                        Icon(
                                            imageVector = Icons.Filled.Block,
                                            contentDescription = "Block contact",
                                            tint = if (contact.blocked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.onSurfaceVariant
                                        )
                                    }
                                    IconButton(onClick = { onDeleteContact(contact.id) }, modifier = Modifier.size(34.dp)) {
                                        Icon(Icons.Filled.Delete, contentDescription = "Remove contact")
                                    }
                                }
                            }
                        }
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}

@Composable
fun IdentityQrMatrix(seed: String, modifier: Modifier = Modifier) {
    val hash = remember(seed) { E2eeCryptoManager.sha256Hex(seed) }
    val fg = MaterialTheme.colorScheme.onPrimaryContainer
    val bg = MaterialTheme.colorScheme.surface
    Canvas(
        modifier = modifier
            .clip(RoundedCornerShape(10.dp))
            .background(bg)
            .padding(6.dp)
    ) {
        val grid = 9
        val cellW = size.width / grid
        val cellH = size.height / grid
        for (r in 0 until grid) {
            for (c in 0 until grid) {
                val isCorner = (r < 3 && c < 3) || (r < 3 && c >= grid - 3) || (r >= grid - 3 && c < 3)
                val bitIdx = (r * grid + c) % hash.length
                val nibble = hash[bitIdx].digitToIntOrNull(16) ?: 0
                if (isCorner || nibble % 2 == 0) {
                    drawRect(
                        color = fg,
                        topLeft = Offset(c * cellW, r * cellH),
                        size = Size(cellW * 0.88f, cellH * 0.88f)
                    )
                }
            }
        }
    }
}

@Composable
fun ThreadInfoDialog(
    chat: ChatThread,
    messages: List<EncryptedMessage>,
    onDismiss: () -> Unit,
    onUpdateInfo: (String, String) -> Unit,
    onToggleLock: () -> Unit
) {
    val context = LocalContext.current
    var title by remember { mutableStateOf(chat.title) }
    var desc by remember { mutableStateOf(chat.description) }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Thread Info & Security") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(
                    value = title,
                    onValueChange = { title = it },
                    label = { Text("Thread Title") },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                OutlinedTextField(
                    value = desc,
                    onValueChange = { desc = it },
                    label = { Text("Description / Guidelines") },
                    maxLines = 2,
                    modifier = Modifier.fillMaxWidth()
                )
                Text(
                    text = "Type: ${chat.chatType.uppercase()} • Members: ${chat.memberUids.size} • Messages: ${messages.size}",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.primary
                )
                OutlinedButton(
                    onClick = onToggleLock,
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text(if (chat.locked) "Unlock Chat PIN Protection" else "Lock Chat with PIN")
                }
                OutlinedButton(
                    onClick = {
                        val transcript = messages.joinToString("\n") { m ->
                            val plain = E2eeCryptoManager.decryptText(m.ciphertext, m.iv, chat.encryptionKeyId, chat.id)
                            "${m.senderName}: $plain"
                        }.ifBlank { "No messages yet." }
                        val intent = Intent(Intent.ACTION_SEND).apply {
                            type = "text/plain"
                            putExtra(Intent.EXTRA_SUBJECT, "PulseTalk Export: ${chat.title}")
                            putExtra(Intent.EXTRA_TEXT, "PulseTalk Chat Export (${chat.title})\n\n$transcript")
                        }
                        context.startActivity(Intent.createChooser(intent, "Export Chat Transcript"))
                    },
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Icon(Icons.Filled.Share, contentDescription = null, modifier = Modifier.size(16.dp))
                    Spacer(Modifier.width(6.dp))
                    Text("Export Decrypted Transcript")
                }
            }
        },
        confirmButton = {
            Button(onClick = { onUpdateInfo(title, desc) }) { Text("Save") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Close") }
        }
    )
}
