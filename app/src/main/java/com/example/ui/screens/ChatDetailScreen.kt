package com.example.ui.screens

import android.Manifest
import android.content.pm.PackageManager
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.imePadding
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.lazy.rememberLazyListState
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.automirrored.filled.Send
import androidx.compose.material.icons.filled.AddPhotoAlternate
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.Contacts
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Info
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.MoreVert
import androidx.compose.material.icons.filled.Poll
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Search
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Summarize
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.AssistChip
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.material3.TopAppBarDefaults
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableLongStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.alpha
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.R
import com.example.core.E2eeCryptoManager
import com.example.core.LocalAppPreferences
import com.example.data.model.ChatThread
import com.example.data.model.ContactRecord
import com.example.data.model.EncryptedMessage
import com.example.data.model.UiState
import com.example.ui.viewmodel.AiUiState
import kotlinx.coroutines.delay

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun ChatDetailScreen(
    chat: ChatThread,
    allChats: List<ChatThread>,
    contacts: List<ContactRecord>,
    messagesState: UiState<List<EncryptedMessage>>,
    aiState: AiUiState,
    currentUserId: String,
    prefs: LocalAppPreferences,
    aiEnabled: Boolean,
    isDarkTheme: Boolean,
    onBack: () -> Unit,
    onSendMessage: (plaintext: String, type: String, replyTo: String, mediaUri: String, voiceSec: Long, viewOnce: Boolean) -> Unit,
    onForwardMessage: (targetChat: ChatThread, plaintext: String, type: String, mediaUri: String) -> Unit,
    onSaveDraft: (String) -> Unit,
    onPinMessage: (String) -> Unit,
    onRevealViewOnce: (String) -> Unit,
    onVotePoll: (EncryptedMessage, Int) -> Unit,
    onReactOrStar: (EncryptedMessage, String, Boolean) -> Unit,
    onEditMessage: (String, String) -> Unit,
    onDeleteMessage: (String) -> Unit,
    onUpdateThreadInfo: (String, String) -> Unit,
    onToggleLockChat: () -> Unit,
    onStartVoiceCall: (hasMicPermission: Boolean) -> Unit,
    onSetDisappearingHours: (Long) -> Unit,
    onToggleVerifyE2ee: () -> Unit,
    onRequestSmartReplies: (List<EncryptedMessage>) -> Unit,
    onRewriteDraft: (String, String) -> Unit,
    onComposeFromTopic: (String) -> Unit,
    onTranslateText: (String, String) -> Unit,
    onSummarizeThread: (List<EncryptedMessage>) -> Unit,
    onResetAiState: () -> Unit
) {
    BackHandler { onBack() }

    val context = LocalContext.current
    var draftText by remember(chat.id) { mutableStateOf(prefs.draftsMap[chat.id].orEmpty()) }
    var replyQuote by remember { mutableStateOf("") }
    var editingMessage by remember { mutableStateOf<EncryptedMessage?>(null) }
    var forwardPayload by remember { mutableStateOf<Triple<String, String, String>?>(null) }

    var showOverflowMenu by remember { mutableStateOf(false) }
    var showSearchInChat by remember { mutableStateOf(false) }
    var inChatQuery by remember { mutableStateOf("") }
    var starredOnly by remember { mutableStateOf(false) }
    var viewOnceArmed by remember { mutableStateOf(false) }

    var showSafetyDialog by remember { mutableStateOf(false) }
    var showDisappearingDialog by remember { mutableStateOf(false) }
    var showAiToolsDialog by remember { mutableStateOf(false) }
    var showPollDialog by remember { mutableStateOf(false) }
    var showThreadInfoDialog by remember { mutableStateOf(false) }
    var showContactCardDialog by remember { mutableStateOf(false) }

    var isRecordingVoiceNote by remember { mutableStateOf(false) }
    var voiceSeconds by remember { mutableLongStateOf(0L) }

    val listState = rememberLazyListState()

    DisposableEffect(chat.id) {
        onDispose {
            onSaveDraft(draftText)
        }
    }

    LaunchedEffect(isRecordingVoiceNote) {
        if (isRecordingVoiceNote) {
            voiceSeconds = 0L
            while (isRecordingVoiceNote) {
                delay(1000L)
                voiceSeconds += 1L
            }
        }
    }

    val photoPickerLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.PickVisualMedia()
    ) { uri ->
        if (uri != null) {
            onSendMessage(
                draftText.ifBlank { "Encrypted Photo" },
                "image",
                replyQuote,
                uri.toString(),
                0L,
                viewOnceArmed
            )
            draftText = ""
            replyQuote = ""
            viewOnceArmed = false
        }
    }

    val micPermissionLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        onStartVoiceCall(granted)
    }

    LaunchedEffect(aiState) {
        if (aiState is AiUiState.RewriteReady) {
            draftText = aiState.text
        }
    }

    val messageList = (messagesState as? UiState.Success)?.data ?: emptyList()

    LaunchedEffect(messageList.size) {
        if (messageList.isNotEmpty() && !showSearchInChat && !starredOnly) {
            listState.animateScrollToItem(messageList.size - 1)
        }
    }

    fun submitCurrentDraft() {
        val editing = editingMessage
        if (editing != null) {
            onEditMessage(editing.id, draftText)
            editingMessage = null
        } else {
            onSendMessage(draftText, "text", replyQuote, "", 0L, viewOnceArmed)
            replyQuote = ""
            viewOnceArmed = false
        }
        draftText = ""
        onSaveDraft("")
    }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                colors = TopAppBarDefaults.topAppBarColors(containerColor = MaterialTheme.colorScheme.surface),
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("chat_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back to chats")
                    }
                },
                title = {
                    Column(modifier = Modifier.clickable { showThreadInfoDialog = true }) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Text(
                                text = chat.title,
                                style = MaterialTheme.typography.titleMedium,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis
                            )
                            if (chat.e2eeVerified) {
                                Spacer(Modifier.width(4.dp))
                                Icon(
                                    imageVector = Icons.Filled.VerifiedUser,
                                    contentDescription = "Verified",
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(16.dp)
                                )
                            }
                        }
                        Text(
                            text = buildString {
                                append("AES-256-GCM")
                                if (chat.disappearingHours > 0L) append(" • ⏱ ${chat.disappearingHours}h")
                                if (chat.locked) append(" • 🔒 Locked")
                            },
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.primary
                        )
                    }
                },
                actions = {
                    IconButton(
                        onClick = {
                            val granted = ContextCompat.checkSelfPermission(
                                context,
                                Manifest.permission.RECORD_AUDIO
                            ) == PackageManager.PERMISSION_GRANTED
                            if (granted) onStartVoiceCall(true)
                            else micPermissionLauncher.launch(Manifest.permission.RECORD_AUDIO)
                        },
                        modifier = Modifier.testTag("start_voice_call_button")
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Call,
                            contentDescription = "Start E2EE voice call",
                            tint = MaterialTheme.colorScheme.primary
                        )
                    }
                    if (aiEnabled) {
                        IconButton(
                            onClick = { showAiToolsDialog = true },
                            modifier = Modifier.testTag("open_ai_copilot_button")
                        ) {
                            Icon(
                                imageVector = Icons.Filled.AutoAwesome,
                                contentDescription = "Gemini AI Copilot",
                                tint = MaterialTheme.colorScheme.secondary
                            )
                        }
                    }
                    Box {
                        IconButton(onClick = { showOverflowMenu = true }) {
                            Icon(Icons.Filled.MoreVert, contentDescription = "Chat options")
                        }
                        DropdownMenu(expanded = showOverflowMenu, onDismissRequest = { showOverflowMenu = false }) {
                            DropdownMenuItem(
                                text = { Text("Thread Info & Export") },
                                leadingIcon = { Icon(Icons.Filled.Info, contentDescription = null) },
                                onClick = { showOverflowMenu = false; showThreadInfoDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text(if (showSearchInChat) "Hide Message Search" else "Search Messages") },
                                leadingIcon = { Icon(Icons.Filled.Search, contentDescription = null) },
                                onClick = { showOverflowMenu = false; showSearchInChat = !showSearchInChat }
                            )
                            DropdownMenuItem(
                                text = { Text(if (starredOnly) "Show All Messages" else "Starred Messages Only") },
                                leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                                onClick = { showOverflowMenu = false; starredOnly = !starredOnly }
                            )
                            DropdownMenuItem(
                                text = { Text("Verify Safety Number") },
                                leadingIcon = { Icon(Icons.Filled.Lock, contentDescription = null) },
                                onClick = { showOverflowMenu = false; showSafetyDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Disappearing Messages") },
                                onClick = { showOverflowMenu = false; showDisappearingDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Create Encrypted Poll") },
                                leadingIcon = { Icon(Icons.Filled.Poll, contentDescription = null) },
                                onClick = { showOverflowMenu = false; showPollDialog = true }
                            )
                            DropdownMenuItem(
                                text = { Text("Share Contact Card") },
                                leadingIcon = { Icon(Icons.Filled.Contacts, contentDescription = null) },
                                onClick = { showOverflowMenu = false; showContactCardDialog = true }
                            )
                            if (aiEnabled) {
                                DropdownMenuItem(
                                    text = { Text("AI Summarize Thread") },
                                    leadingIcon = { Icon(Icons.Filled.Summarize, contentDescription = null) },
                                    onClick = { showOverflowMenu = false; onSummarizeThread(messageList) }
                                )
                            }
                        }
                    }
                }
            )
        }
    ) { innerPadding ->
        Box(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .imePadding()
                .navigationBarsPadding()
        ) {
            if (prefs.wallpaperEnabled) {
                when (prefs.wallpaperPreset) {
                    "Emerald Mist" -> Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF064E3B).copy(alpha = if (isDarkTheme) 0.35f else 0.08f),
                                    Color.Transparent
                                )
                            )
                        )
                    )
                    "Midnight Ink" -> Box(
                        modifier = Modifier.fillMaxSize().background(
                            Brush.verticalGradient(
                                listOf(
                                    Color(0xFF0F172A).copy(alpha = if (isDarkTheme) 0.45f else 0.07f),
                                    Color(0xFF0284C7).copy(alpha = 0.05f)
                                )
                            )
                        )
                    )
                    "Minimal" -> Unit
                    else -> Image(
                        painter = painterResource(id = R.drawable.img_chat_wallpaper),
                        contentDescription = null,
                        contentScale = ContentScale.Crop,
                        modifier = Modifier.fillMaxSize().alpha(if (isDarkTheme) 0.28f else 0.08f)
                    )
                }
            }

            Column(modifier = Modifier.fillMaxSize()) {
                AnimatedVisibility(visible = showSearchInChat) {
                    OutlinedTextField(
                        value = inChatQuery,
                        onValueChange = { inChatQuery = it },
                        placeholder = { Text("Search decrypted messages in this thread…") },
                        trailingIcon = {
                            IconButton(onClick = { inChatQuery = ""; showSearchInChat = false }) {
                                Icon(Icons.Filled.Close, contentDescription = "Close in-chat search")
                            }
                        },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 6.dp)
                    )
                }

                // Pinned Message Banner
                if (chat.pinnedMessagePreview.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.tertiaryContainer,
                        shape = RoundedCornerShape(12.dp),
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 14.dp, vertical = 4.dp)
                    ) {
                        Row(
                            modifier = Modifier.padding(horizontal = 12.dp, vertical = 6.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(Icons.Filled.PushPin, contentDescription = null, modifier = Modifier.size(14.dp))
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "Pinned: ${chat.pinnedMessagePreview}",
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(onClick = { onPinMessage("") }, modifier = Modifier.size(22.dp)) {
                                Icon(Icons.Filled.Close, contentDescription = "Unpin", modifier = Modifier.size(14.dp))
                            }
                        }
                    }
                }

                // Encryption Banner
                Surface(
                    color = MaterialTheme.colorScheme.primaryContainer.copy(alpha = 0.72f),
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier.fillMaxWidth().padding(horizontal = 16.dp, vertical = 4.dp)
                ) {
                    Row(
                        modifier = Modifier.padding(horizontal = 12.dp, vertical = 5.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        Icon(
                            imageVector = Icons.Filled.Lock,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer,
                            modifier = Modifier.size(14.dp)
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = if (starredOnly) "Viewing Starred Encrypted Messages" else "E2EE Active • Supports *bold*, _italic_, ~strike~, `code`",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                }

                // Messages List
                Box(modifier = Modifier.weight(1f)) {
                    when (messagesState) {
                        is UiState.Loading -> CircularProgressIndicator(modifier = Modifier.align(Alignment.Center))
                        is UiState.Error -> Text(
                            text = messagesState.message,
                            color = MaterialTheme.colorScheme.error,
                            modifier = Modifier.align(Alignment.Center).padding(20.dp)
                        )
                        is UiState.Success -> {
                            val displayMessages = remember(messagesState.data, inChatQuery, starredOnly, chat.encryptionKeyId) {
                                messagesState.data.filter { msg ->
                                    val dec = E2eeCryptoManager.decryptText(msg.ciphertext, msg.iv, chat.encryptionKeyId, chat.id)
                                    val matchStar = !starredOnly || msg.starred
                                    val matchQuery = inChatQuery.isBlank() || dec.contains(inChatQuery, ignoreCase = true)
                                    matchStar && matchQuery
                                }
                            }
                            LazyColumn(
                                state = listState,
                                modifier = Modifier.fillMaxSize(),
                                contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 14.dp, vertical = 8.dp),
                                verticalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                items(displayMessages, key = { it.id }) { msg ->
                                    val decrypted = remember(msg.ciphertext, msg.iv, chat.encryptionKeyId) {
                                        E2eeCryptoManager.decryptText(msg.ciphertext, msg.iv, chat.encryptionKeyId, chat.id)
                                    }
                                    MessageBubble(
                                        msg = msg,
                                        decryptedText = decrypted,
                                        isMe = msg.senderId == currentUserId,
                                        isDarkTheme = isDarkTheme,
                                        fontSizeScale = prefs.fontSizeScale,
                                        onReply = { replyQuote = decrypted.take(80) },
                                        onReact = { emoji -> onReactOrStar(msg, emoji, msg.starred) },
                                        onToggleStar = { onReactOrStar(msg, msg.reactionEmoji, !msg.starred) },
                                        onPinMessage = { onPinMessage(decrypted.take(160)) },
                                        onForward = { forwardPayload = Triple(decrypted, msg.messageType, msg.mediaUri) },
                                        onTranslate = { lang -> onTranslateText(decrypted, lang) },
                                        onRevealViewOnce = { onRevealViewOnce(msg.id) },
                                        onVotePoll = { optIdx -> onVotePoll(msg, optIdx) },
                                        onEdit = { editingMessage = msg; draftText = decrypted },
                                        onDeleteForAll = { onDeleteMessage(msg.id) }
                                    )
                                }
                            }
                        }
                    }
                }

                AnimatedVisibility(visible = aiState !is AiUiState.Idle) {
                    AiAssistantStrip(
                        aiState = aiState,
                        onSelectReply = { suggestion ->
                            onSendMessage(suggestion, "text", replyQuote, "", 0L, viewOnceArmed)
                            replyQuote = ""
                            viewOnceArmed = false
                            onResetAiState()
                        },
                        onDismiss = onResetAiState
                    )
                }

                if (replyQuote.isNotBlank() || editingMessage != null) {
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, modifier = Modifier.fillMaxWidth()) {
                        Row(
                            modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            Icon(
                                imageVector = if (editingMessage != null) Icons.Filled.Edit else Icons.AutoMirrored.Filled.Reply,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(16.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = if (editingMessage != null) "Editing encrypted message" else replyQuote,
                                style = MaterialTheme.typography.bodySmall,
                                maxLines = 1,
                                overflow = TextOverflow.Ellipsis,
                                modifier = Modifier.weight(1f)
                            )
                            IconButton(
                                onClick = { replyQuote = ""; editingMessage = null },
                                modifier = Modifier.size(28.dp)
                            ) {
                                Icon(Icons.Filled.Close, contentDescription = "Cancel", modifier = Modifier.size(16.dp))
                            }
                        }
                    }
                }

                // Quick AI + Canned Quick-Replies Strip
                if (aiState is AiUiState.Idle) {
                    Row(
                        modifier = Modifier
                            .fillMaxWidth()
                            .horizontalScroll(rememberScrollState())
                            .padding(horizontal = 12.dp, vertical = 4.dp),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        if (aiEnabled) {
                            AssistChip(
                                onClick = { onRequestSmartReplies(messageList) },
                                label = { Text("AI Replies") },
                                leadingIcon = {
                                    Icon(Icons.Filled.AutoAwesome, contentDescription = null, tint = MaterialTheme.colorScheme.secondary, modifier = Modifier.size(16.dp))
                                },
                                modifier = Modifier.testTag("ai_smart_replies_chip")
                            )
                            AssistChip(
                                onClick = { onSummarizeThread(messageList) },
                                label = { Text("Summarize") },
                                leadingIcon = { Icon(Icons.Filled.Summarize, contentDescription = null, modifier = Modifier.size(16.dp)) }
                            )
                        }
                        prefs.quickReplyTemplates.forEach { tpl ->
                            SuggestionChip(
                                onClick = { draftText = tpl },
                                label = { Text(tpl, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }

                // Composer Bar
                Surface(color = MaterialTheme.colorScheme.surface, tonalElevation = 3.dp, modifier = Modifier.fillMaxWidth()) {
                    Row(
                        modifier = Modifier.fillMaxWidth().padding(horizontal = 8.dp, vertical = 8.dp),
                        verticalAlignment = Alignment.CenterVertically
                    ) {
                        IconButton(onClick = {
                            photoPickerLauncher.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                        }) {
                            Icon(Icons.Filled.AddPhotoAlternate, contentDescription = "Pick photo")
                        }
                        IconButton(onClick = { viewOnceArmed = !viewOnceArmed }) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = "Toggle View-Once mode",
                                tint = if (viewOnceArmed) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                        OutlinedTextField(
                            value = draftText,
                            onValueChange = { draftText = it },
                            placeholder = {
                                Text(
                                    when {
                                        isRecordingVoiceNote -> "Recording voice note (${voiceSeconds}s)…"
                                        viewOnceArmed -> "View-Once secret message…"
                                        else -> "Encrypted message"
                                    }
                                )
                            },
                            maxLines = 4,
                            keyboardOptions = KeyboardOptions(
                                imeAction = if (prefs.enterToSend) ImeAction.Send else ImeAction.Default
                            ),
                            keyboardActions = KeyboardActions(
                                onSend = { if (prefs.enterToSend && draftText.isNotBlank()) submitCurrentDraft() }
                            ),
                            shape = RoundedCornerShape(24.dp),
                            modifier = Modifier.weight(1f).testTag("message_input")
                        )
                        Spacer(Modifier.width(6.dp))
                        if (draftText.isBlank()) {
                            IconButton(
                                onClick = {
                                    if (!isRecordingVoiceNote) {
                                        isRecordingVoiceNote = true
                                    } else {
                                        val dur = voiceSeconds.coerceAtLeast(1L)
                                        isRecordingVoiceNote = false
                                        onSendMessage("Voice note", "voice", replyQuote, "", dur, false)
                                        replyQuote = ""
                                    }
                                },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(if (isRecordingVoiceNote) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = if (isRecordingVoiceNote) "Stop and send voice note" else "Record voice note",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        } else {
                            IconButton(
                                onClick = { submitCurrentDraft() },
                                modifier = Modifier
                                    .size(48.dp)
                                    .clip(CircleShape)
                                    .background(MaterialTheme.colorScheme.primary)
                                    .testTag("send_message_button")
                            ) {
                                Icon(
                                    imageVector = Icons.AutoMirrored.Filled.Send,
                                    contentDescription = "Send encrypted message",
                                    tint = MaterialTheme.colorScheme.onPrimary
                                )
                            }
                        }
                    }
                }
            }
        }
    }

    forwardPayload?.let { (plain, type, media) ->
        ForwardMessageDialog(
            chats = allChats.filter { it.id != chat.id },
            onDismiss = { forwardPayload = null },
            onForwardTo = { target ->
                forwardPayload = null
                onForwardMessage(target, plain, type, media)
            }
        )
    }

    if (showThreadInfoDialog) {
        ThreadInfoDialog(
            chat = chat,
            messages = messageList,
            onDismiss = { showThreadInfoDialog = false },
            onUpdateInfo = { t, d ->
                showThreadInfoDialog = false
                onUpdateThreadInfo(t, d)
            },
            onToggleLock = {
                showThreadInfoDialog = false
                onToggleLockChat()
            }
        )
    }

    if (showSafetyDialog) {
        val safetyNum = remember(chat.id, chat.encryptionKeyId) {
            E2eeCryptoManager.computeSafetyNumber(currentUserId, chat.encryptionKeyId)
        }
        AlertDialog(
            onDismissRequest = { showSafetyDialog = false },
            title = { Text("E2EE Safety Number") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text(
                        text = "Compare this 60-digit cryptographic fingerprint with your contact to confirm zero-trust end-to-end encryption.",
                        style = MaterialTheme.typography.bodySmall
                    )
                    Surface(color = MaterialTheme.colorScheme.surfaceVariant, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
                        Text(text = safetyNum, style = MaterialTheme.typography.labelSmall, modifier = Modifier.padding(14.dp))
                    }
                    Text("Cipher: ECDH P-256 + AES-256-GCM", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                }
            },
            confirmButton = {
                Button(onClick = { onToggleVerifyE2ee(); showSafetyDialog = false }) {
                    Text(if (chat.e2eeVerified) "Verified ✓" else "Mark Verified")
                }
            },
            dismissButton = { TextButton(onClick = { showSafetyDialog = false }) { Text("Close") } }
        )
    }

    if (showDisappearingDialog) {
        AlertDialog(
            onDismissRequest = { showDisappearingDialog = false },
            title = { Text("Disappearing Messages Timer") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf(0L to "Off", 24L to "24 Hours", 168L to "7 Days", 2160L to "90 Days").forEach { (hours, label) ->
                        OutlinedButton(
                            onClick = { onSetDisappearingHours(hours); showDisappearingDialog = false },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(if (chat.disappearingHours == hours) "✓ $label" else label)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showDisappearingDialog = false }) { Text("Close") } }
        )
    }

    if (showAiToolsDialog) {
        var topicPrompt by remember { mutableStateOf("") }
        AlertDialog(
            onDismissRequest = { showAiToolsDialog = false },
            title = { Text("Gemini AI Copilot") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(
                        value = topicPrompt,
                        onValueChange = { topicPrompt = it },
                        label = { Text("Draft from topic (e.g., Wish birthday)") },
                        singleLine = true,
                        modifier = Modifier.fillMaxWidth()
                    )
                    Button(
                        onClick = {
                            if (topicPrompt.isNotBlank()) {
                                showAiToolsDialog = false
                                onComposeFromTopic(topicPrompt)
                            }
                        },
                        enabled = topicPrompt.isNotBlank(),
                        modifier = Modifier.fillMaxWidth()
                    ) { Text("Compose Draft from Topic") }

                    Text("Or polish your current draft:", style = MaterialTheme.typography.labelSmall)
                    listOf("Professional", "Warm & Friendly", "Concise", "Fix Grammar & Spelling").forEach { style ->
                        OutlinedButton(
                            onClick = { showAiToolsDialog = false; onRewriteDraft(draftText, style) },
                            modifier = Modifier.fillMaxWidth()
                        ) { Text("Rewrite: $style") }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showAiToolsDialog = false }) { Text("Done") } }
        )
    }

    if (showPollDialog) {
        var pollQuestion by remember { mutableStateOf("") }
        var optA by remember { mutableStateOf("Yes") }
        var optB by remember { mutableStateOf("No") }
        AlertDialog(
            onDismissRequest = { showPollDialog = false },
            title = { Text("Create Interactive Poll") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(value = pollQuestion, onValueChange = { pollQuestion = it }, label = { Text("Poll question") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = optA, onValueChange = { optA = it }, label = { Text("Option 1") }, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = optB, onValueChange = { optB = it }, label = { Text("Option 2") }, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(
                    onClick = {
                        if (pollQuestion.isNotBlank()) {
                            onSendMessage("POLL: $pollQuestion [$optA | $optB]", "poll", "", "", 0L, false)
                            showPollDialog = false
                        }
                    },
                    enabled = pollQuestion.isNotBlank()
                ) { Text("Post Poll") }
            },
            dismissButton = { TextButton(onClick = { showPollDialog = false }) { Text("Cancel") } }
        )
    }

    if (showContactCardDialog) {
        AlertDialog(
            onDismissRequest = { showContactCardDialog = false },
            title = { Text("Share Contact Card") },
            text = {
                if (contacts.isEmpty()) {
                    Text("No saved contacts yet. Add a contact from the Chats tab first.")
                } else {
                    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        contacts.take(6).forEach { c ->
                            OutlinedButton(
                                onClick = {
                                    onSendMessage("${c.displayName} • ID: ${c.contactUid.take(14)}…", "contact", "", "", 0L, false)
                                    showContactCardDialog = false
                                },
                                modifier = Modifier.fillMaxWidth()
                            ) { Text(c.displayName) }
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showContactCardDialog = false }) { Text("Close") } }
        )
    }
}
