package com.example.ui.screens

import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.combinedClickable
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
import androidx.compose.material.icons.filled.Archive
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Campaign
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Forum
import androidx.compose.material.icons.filled.Group
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.NotificationsOff
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.QrCode2
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Timer
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.FilterChip
import androidx.compose.material3.FilterChipDefaults
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
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import com.example.data.model.ChatThread
import com.example.data.model.ContactRecord
import com.example.data.model.UiState
import java.text.SimpleDateFormat
import java.util.Locale

private enum class ChatFilter(val label: String) {
    ALL("All"),
    UNREAD("Unread"),
    GROUPS("Groups"),
    COMMUNITIES("Communities"),
    VAULT("Vault"),
    LOCKED("Locked"),
    ARCHIVED("Archived")
}

@Composable
fun ChatsTabScreen(
    chatsState: UiState<List<ChatThread>>,
    contacts: List<ContactRecord>,
    currentUserId: String,
    compactMode: Boolean,
    draftsMap: Map<String, String>,
    hasChatLockPin: Boolean,
    onVerifyChatPin: (String) -> Boolean,
    onOpenChat: (String) -> Unit,
    onCreateChat: (title: String, type: String, peerUid: String?, extraMembers: List<String>, description: String) -> Unit,
    onSendBroadcast: (List<ChatThread>, String) -> Unit,
    onAddContact: (uid: String, name: String) -> Unit,
    onToggleContactFavorite: (ContactRecord) -> Unit,
    onToggleContactBlocked: (ContactRecord) -> Unit,
    onDeleteContact: (String) -> Unit,
    onTogglePin: (ChatThread) -> Unit,
    onToggleArchive: (ChatThread) -> Unit,
    onToggleMute: (ChatThread) -> Unit,
    onToggleLock: (ChatThread) -> Unit,
    onToggleUnread: (ChatThread) -> Unit,
    onDeleteChat: (String) -> Unit
) {
    var searchQuery by remember { mutableStateOf("") }
    var activeFilter by remember { mutableStateOf(ChatFilter.ALL) }
    var showNewChatDialog by remember { mutableStateOf(false) }
    var showContactsDialog by remember { mutableStateOf(false) }
    var showBroadcastDialog by remember { mutableStateOf(false) }
    var pendingLockedChat by remember { mutableStateOf<ChatThread?>(null) }
    var pinError by remember { mutableStateOf(false) }

    val allChats = (chatsState as? UiState.Success)?.data ?: emptyList()

    Box(modifier = Modifier.fillMaxSize()) {
        Column(modifier = Modifier.fillMaxSize()) {
            OutlinedTextField(
                value = searchQuery,
                onValueChange = { searchQuery = it },
                placeholder = { Text("Search encrypted chats, groups, drafts…") },
                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = "Search chats") },
                trailingIcon = {
                    if (searchQuery.isNotEmpty()) {
                        IconButton(onClick = { searchQuery = "" }) {
                            Icon(Icons.Filled.Close, contentDescription = "Clear search")
                        }
                    } else {
                        Icon(
                            imageVector = Icons.Filled.AutoAwesome,
                            contentDescription = "AI Search Ready",
                            tint = MaterialTheme.colorScheme.secondary
                        )
                    }
                },
                singleLine = true,
                shape = RoundedCornerShape(24.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 8.dp)
                    .testTag("chat_search_input")
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .horizontalScroll(rememberScrollState())
                    .padding(horizontal = 16.dp, vertical = 4.dp),
                horizontalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                ChatFilter.entries.forEach { filter ->
                    FilterChip(
                        selected = activeFilter == filter,
                        onClick = { activeFilter = filter },
                        label = { Text(filter.label) },
                        colors = FilterChipDefaults.filterChipColors(
                            selectedContainerColor = MaterialTheme.colorScheme.primaryContainer,
                            selectedLabelColor = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    )
                }
            }

            // Invite ID + Contacts QR + Broadcast Strip
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.55f),
                shape = RoundedCornerShape(14.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(horizontal = 16.dp, vertical = 6.dp)
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 10.dp, vertical = 6.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.SpaceBetween
                ) {
                    Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.weight(1f)) {
                        Icon(
                            imageVector = Icons.Filled.Shield,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(16.dp)
                        )
                        Spacer(Modifier.width(6.dp))
                        Text(
                            text = "ID: ${currentUserId.take(10)}…",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant,
                            maxLines = 1,
                            overflow = TextOverflow.Ellipsis
                        )
                    }
                    TextButton(onClick = { showBroadcastDialog = true }) {
                        Icon(Icons.Filled.Campaign, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Broadcast")
                    }
                    TextButton(onClick = { showContactsDialog = true }) {
                        Icon(Icons.Filled.QrCode2, contentDescription = null, modifier = Modifier.size(16.dp))
                        Spacer(Modifier.width(4.dp))
                        Text("Contacts")
                    }
                }
            }

            when (chatsState) {
                is UiState.Loading -> {
                    Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
                is UiState.Error -> {
                    Box(Modifier.fillMaxSize().padding(24.dp), contentAlignment = Alignment.Center) {
                        Text(
                            text = chatsState.message,
                            color = MaterialTheme.colorScheme.error,
                            style = MaterialTheme.typography.bodyMedium
                        )
                    }
                }
                is UiState.Success -> {
                    val filtered = remember(chatsState.data, searchQuery, activeFilter, draftsMap) {
                        chatsState.data.filter { chat ->
                            val draft = draftsMap[chat.id].orEmpty()
                            val matchesSearch = searchQuery.isBlank() ||
                                chat.title.contains(searchQuery, ignoreCase = true) ||
                                chat.lastMessagePreview.contains(searchQuery, ignoreCase = true) ||
                                draft.contains(searchQuery, ignoreCase = true)
                            val matchesFilter = when (activeFilter) {
                                ChatFilter.ALL -> !chat.archived && !chat.locked
                                ChatFilter.UNREAD -> !chat.archived && chat.unreadCount > 0
                                ChatFilter.GROUPS -> !chat.archived && chat.chatType == "group"
                                ChatFilter.COMMUNITIES -> !chat.archived && chat.chatType == "community"
                                ChatFilter.VAULT -> !chat.archived && chat.chatType == "vault"
                                ChatFilter.LOCKED -> chat.locked
                                ChatFilter.ARCHIVED -> chat.archived
                            }
                            matchesSearch && matchesFilter
                        }
                    }

                    AnimatedVisibility(visible = filtered.isEmpty()) {
                        EmptyChatsState(onNewChat = { showNewChatDialog = true })
                    }

                    LazyColumn(
                        modifier = Modifier.fillMaxSize(),
                        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 4.dp, bottom = 92.dp),
                        verticalArrangement = Arrangement.spacedBy(if (compactMode) 6.dp else 10.dp)
                    ) {
                        items(filtered, key = { it.id }) { chat ->
                            ChatThreadCard(
                                chat = chat,
                                draftPreview = draftsMap[chat.id].orEmpty(),
                                compactMode = compactMode,
                                onClick = {
                                    if (chat.locked && hasChatLockPin) {
                                        pinError = false
                                        pendingLockedChat = chat
                                    } else {
                                        onOpenChat(chat.id)
                                    }
                                },
                                onTogglePin = { onTogglePin(chat) },
                                onToggleArchive = { onToggleArchive(chat) },
                                onToggleMute = { onToggleMute(chat) },
                                onToggleLock = { onToggleLock(chat) },
                                onToggleUnread = { onToggleUnread(chat) },
                                onDelete = { onDeleteChat(chat.id) }
                            )
                        }
                    }
                }
            }
        }

        FloatingActionButton(
            onClick = { showNewChatDialog = true },
            containerColor = MaterialTheme.colorScheme.primary,
            contentColor = MaterialTheme.colorScheme.onPrimary,
            modifier = Modifier
                .align(Alignment.BottomEnd)
                .padding(20.dp)
                .testTag("new_chat_fab")
        ) {
            Icon(Icons.Filled.Add, contentDescription = "Start new encrypted chat")
        }
    }

    if (showNewChatDialog) {
        NewChatDialog(
            contacts = contacts,
            onDismiss = { showNewChatDialog = false },
            onCreate = { title, type, peerUid, extras, desc ->
                showNewChatDialog = false
                onCreateChat(title, type, peerUid, extras, desc)
            }
        )
    }

    if (showContactsDialog) {
        ContactsAndQrDialog(
            currentUserId = currentUserId,
            contacts = contacts,
            onDismiss = { showContactsDialog = false },
            onAddContact = onAddContact,
            onToggleFavorite = onToggleContactFavorite,
            onToggleBlocked = onToggleContactBlocked,
            onDeleteContact = onDeleteContact
        )
    }

    if (showBroadcastDialog) {
        BroadcastComposeDialog(
            chats = allChats,
            onDismiss = { showBroadcastDialog = false },
            onSendBroadcast = { targets, msg ->
                showBroadcastDialog = false
                onSendBroadcast(targets, msg)
            }
        )
    }

    pendingLockedChat?.let { target ->
        ChatLockPinDialog(
            title = if (pinError) "Incorrect PIN — Try Again" else "Unlock '${target.title}'",
            subtitle = "Enter your PulseTalk Chat Lock PIN to open this protected conversation.",
            onDismiss = { pendingLockedChat = null },
            onSubmitPin = { pin ->
                if (onVerifyChatPin(pin)) {
                    pendingLockedChat = null
                    onOpenChat(target.id)
                } else {
                    pinError = true
                }
            }
        )
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
private fun ChatThreadCard(
    chat: ChatThread,
    draftPreview: String,
    compactMode: Boolean,
    onClick: () -> Unit,
    onTogglePin: () -> Unit,
    onToggleArchive: () -> Unit,
    onToggleMute: () -> Unit,
    onToggleLock: () -> Unit,
    onToggleUnread: () -> Unit,
    onDelete: () -> Unit
) {
    var menuExpanded by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeText = chat.updatedAt?.toDate()?.let { timeFormat.format(it) } ?: "Now"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(18.dp),
        modifier = Modifier
            .fillMaxWidth()
            .combinedClickable(onClick = onClick, onLongClick = { menuExpanded = true })
            .testTag("chat_item_${chat.id}")
    ) {
        Row(
            modifier = Modifier
                .fillMaxWidth()
                .padding(horizontal = 14.dp, vertical = if (compactMode) 10.dp else 14.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Box(
                modifier = Modifier
                    .size(if (compactMode) 44.dp else 52.dp)
                    .clip(CircleShape)
                    .background(MaterialTheme.colorScheme.primaryContainer),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = when (chat.chatType) {
                        "group" -> Icons.Filled.Group
                        "community" -> Icons.Filled.Forum
                        "broadcast" -> Icons.Filled.Campaign
                        "vault" -> Icons.Filled.Lock
                        else -> Icons.Filled.Shield
                    },
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onPrimaryContainer
                )
            }

            Spacer(Modifier.width(12.dp))

            Column(modifier = Modifier.weight(1f)) {
                Row(
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(5.dp)
                ) {
                    Text(
                        text = chat.title,
                        style = MaterialTheme.typography.titleMedium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis,
                        modifier = Modifier.weight(1f, fill = false)
                    )
                    if (chat.e2eeVerified) {
                        Icon(
                            imageVector = Icons.Filled.VerifiedUser,
                            contentDescription = "Verified safety number",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(15.dp)
                        )
                    }
                    if (chat.locked) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = "Locked chat",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.disappearingHours > 0L) {
                        Icon(
                            imageVector = Icons.Filled.Timer,
                            contentDescription = "Disappearing messages active",
                            tint = MaterialTheme.colorScheme.secondary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
                Spacer(Modifier.height(3.dp))
                if (draftPreview.isNotBlank()) {
                    Text(
                        text = "Draft: $draftPreview",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.primary,
                        fontWeight = FontWeight.Medium,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                } else {
                    Text(
                        text = if (chat.locked) "Locked encrypted conversation" else chat.lastMessagePreview.ifBlank { "End-to-end encrypted" },
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        maxLines = 1,
                        overflow = TextOverflow.Ellipsis
                    )
                }
            }

            Spacer(Modifier.width(8.dp))

            Column(horizontalAlignment = Alignment.End) {
                Text(
                    text = timeText,
                    style = MaterialTheme.typography.labelSmall,
                    color = if (chat.unreadCount > 0L) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                )
                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    if (chat.unreadCount > 0L) {
                        Box(
                            modifier = Modifier
                                .size(20.dp)
                                .clip(CircleShape)
                                .background(MaterialTheme.colorScheme.primary),
                            contentAlignment = Alignment.Center
                        ) {
                            Text(
                                text = chat.unreadCount.coerceAtMost(99L).toString(),
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimary
                            )
                        }
                    }
                    if (chat.pinned) {
                        Icon(
                            imageVector = Icons.Filled.PushPin,
                            contentDescription = "Pinned",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.muted) {
                        Icon(
                            imageVector = Icons.Filled.NotificationsOff,
                            contentDescription = "Muted",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                    if (chat.archived) {
                        Icon(
                            imageVector = Icons.Filled.Archive,
                            contentDescription = "Archived",
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(14.dp)
                        )
                    }
                }
            }

            DropdownMenu(expanded = menuExpanded, onDismissRequest = { menuExpanded = false }) {
                DropdownMenuItem(
                    text = { Text(if (chat.pinned) "Unpin chat" else "Pin to top") },
                    onClick = { menuExpanded = false; onTogglePin() }
                )
                DropdownMenuItem(
                    text = { Text(if (chat.unreadCount > 0L) "Mark as read" else "Mark as unread") },
                    onClick = { menuExpanded = false; onToggleUnread() }
                )
                DropdownMenuItem(
                    text = { Text(if (chat.locked) "Unlock chat" else "Lock chat") },
                    onClick = { menuExpanded = false; onToggleLock() }
                )
                DropdownMenuItem(
                    text = { Text(if (chat.muted) "Unmute notifications" else "Mute notifications") },
                    onClick = { menuExpanded = false; onToggleMute() }
                )
                DropdownMenuItem(
                    text = { Text(if (chat.archived) "Unarchive chat" else "Archive chat") },
                    onClick = { menuExpanded = false; onToggleArchive() }
                )
                if (chat.chatType != "vault") {
                    DropdownMenuItem(
                        text = { Text("Delete chat", color = MaterialTheme.colorScheme.error) },
                        onClick = { menuExpanded = false; onDelete() }
                    )
                }
            }
        }
    }
}

@Composable
private fun EmptyChatsState(onNewChat: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .padding(32.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Icon(
            imageVector = Icons.Filled.Lock,
            contentDescription = null,
            tint = MaterialTheme.colorScheme.primary,
            modifier = Modifier.size(48.dp)
        )
        Spacer(Modifier.height(12.dp))
        Text("No matching encrypted chats", style = MaterialTheme.typography.titleLarge)
        Spacer(Modifier.height(6.dp))
        Text(
            text = "Create a private vault, group, community, broadcast, or direct encrypted thread.",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
        Spacer(Modifier.height(14.dp))
        OutlinedButton(onClick = onNewChat) {
            Text("New Encrypted Chat")
        }
    }
}
