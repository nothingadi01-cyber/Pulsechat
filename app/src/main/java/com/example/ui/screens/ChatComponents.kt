package com.example.ui.screens

import android.content.ClipData
import android.content.ClipboardManager
import android.content.Context
import androidx.compose.foundation.ExperimentalFoundationApi
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.combinedClickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.layout.widthIn
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.Forward
import androidx.compose.material.icons.automirrored.filled.Reply
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.Close
import androidx.compose.material.icons.filled.ContentCopy
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.DoneAll
import androidx.compose.material.icons.filled.Edit
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Mic
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.Star
import androidx.compose.material.icons.filled.Translate
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.DropdownMenu
import androidx.compose.material3.DropdownMenuItem
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
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
import androidx.compose.ui.layout.ContentScale
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.text.style.TextDecoration
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import coil.compose.AsyncImage
import com.example.data.model.ChatThread
import com.example.data.model.EncryptedMessage
import com.example.ui.theme.IncomingBubbleDark
import com.example.ui.theme.IncomingBubbleLight
import com.example.ui.theme.OutgoingBubbleDark
import com.example.ui.theme.OutgoingBubbleLight
import com.example.ui.viewmodel.AiUiState
import java.text.SimpleDateFormat
import java.util.Locale

fun parseFormattedChatText(raw: String): AnnotatedString {
    return buildAnnotatedString {
        var i = 0
        while (i < raw.length) {
            val c = raw[i]
            val markerStyle: SpanStyle? = when (c) {
                '*' -> SpanStyle(fontWeight = FontWeight.Bold)
                '_' -> SpanStyle(fontStyle = FontStyle.Italic)
                '~' -> SpanStyle(textDecoration = TextDecoration.LineThrough)
                '`' -> SpanStyle(fontFamily = FontFamily.Monospace)
                else -> null
            }
            if (markerStyle != null) {
                val closeIdx = raw.indexOf(c, i + 1)
                if (closeIdx > i + 1) {
                    val inner = raw.substring(i + 1, closeIdx)
                    val start = length
                    append(inner)
                    addStyle(markerStyle, start, length)
                    i = closeIdx + 1
                    continue
                }
            }
            append(c)
            i++
        }
    }
}

@OptIn(ExperimentalFoundationApi::class)
@Composable
fun MessageBubble(
    msg: EncryptedMessage,
    decryptedText: String,
    isMe: Boolean,
    isDarkTheme: Boolean,
    fontSizeScale: String,
    onReply: () -> Unit,
    onReact: (String) -> Unit,
    onToggleStar: () -> Unit,
    onPinMessage: () -> Unit,
    onForward: () -> Unit,
    onTranslate: (String) -> Unit,
    onRevealViewOnce: () -> Unit,
    onVotePoll: (Int) -> Unit,
    onEdit: () -> Unit,
    onDeleteForAll: () -> Unit
) {
    val context = LocalContext.current
    var menuOpen by remember { mutableStateOf(false) }
    var showTranslateMenu by remember { mutableStateOf(false) }
    val timeFormat = remember { SimpleDateFormat("HH:mm", Locale.getDefault()) }
    val timeLabel = msg.createdAt?.toDate()?.let { timeFormat.format(it) } ?: "Sending"
    val bubbleColor = when {
        isMe && isDarkTheme -> OutgoingBubbleDark
        isMe && !isDarkTheme -> OutgoingBubbleLight
        !isMe && isDarkTheme -> IncomingBubbleDark
        else -> IncomingBubbleLight
    }
    val textSize = when (fontSizeScale) {
        "Small" -> 13.5.sp
        "Large" -> 17.5.sp
        else -> 15.sp
    }

    Box(
        modifier = Modifier.fillMaxWidth(),
        contentAlignment = if (isMe) Alignment.CenterEnd else Alignment.CenterStart
    ) {
        Surface(
            color = bubbleColor,
            shape = RoundedCornerShape(
                topStart = 18.dp,
                topEnd = 18.dp,
                bottomStart = if (isMe) 18.dp else 4.dp,
                bottomEnd = if (isMe) 4.dp else 18.dp
            ),
            tonalElevation = 1.dp,
            modifier = Modifier
                .widthIn(max = 315.dp)
                .combinedClickable(
                    onClick = {
                        if (msg.viewOnce && !msg.viewOnceOpened) {
                            onRevealViewOnce()
                        }
                    },
                    onLongClick = { menuOpen = true }
                )
        ) {
            Column(modifier = Modifier.padding(horizontal = 12.dp, vertical = 8.dp)) {
                if (!isMe) {
                    Text(
                        text = msg.senderName,
                        style = MaterialTheme.typography.labelLarge,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(Modifier.height(2.dp))
                }
                if (msg.forwarded) {
                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        modifier = Modifier.padding(bottom = 4.dp)
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.Forward,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onSurfaceVariant,
                            modifier = Modifier.size(13.dp)
                        )
                        Spacer(Modifier.width(4.dp))
                        Text(
                            text = "Forwarded",
                            style = MaterialTheme.typography.labelSmall,
                            fontStyle = FontStyle.Italic,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
                if (msg.replyToPreview.isNotBlank()) {
                    Surface(
                        color = MaterialTheme.colorScheme.surface.copy(alpha = 0.45f),
                        shape = RoundedCornerShape(8.dp),
                        modifier = Modifier
                            .fillMaxWidth()
                            .padding(bottom = 6.dp)
                    ) {
                        Text(
                            text = msg.replyToPreview,
                            style = MaterialTheme.typography.bodySmall,
                            maxLines = 2,
                            overflow = TextOverflow.Ellipsis,
                            modifier = Modifier.padding(6.dp)
                        )
                    }
                }

                when {
                    msg.viewOnce && !msg.viewOnceOpened -> {
                        Row(
                            verticalAlignment = Alignment.CenterVertically,
                            modifier = Modifier.padding(vertical = 4.dp)
                        ) {
                            Icon(
                                imageVector = Icons.Filled.Visibility,
                                contentDescription = "Tap to view once",
                                tint = MaterialTheme.colorScheme.primary,
                                modifier = Modifier.size(18.dp)
                            )
                            Spacer(Modifier.width(8.dp))
                            Text(
                                text = "View-Once Secret Message • Tap to reveal",
                                style = MaterialTheme.typography.bodyMedium,
                                fontWeight = FontWeight.SemiBold,
                                color = MaterialTheme.colorScheme.primary
                            )
                        }
                    }
                    msg.viewOnce && msg.viewOnceOpened -> {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(
                                imageVector = Icons.Filled.Lock,
                                contentDescription = null,
                                tint = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.size(15.dp)
                            )
                            Spacer(Modifier.width(6.dp))
                            Text(
                                text = "Opened: $decryptedText",
                                style = MaterialTheme.typography.bodyMedium,
                                fontStyle = FontStyle.Italic,
                                color = MaterialTheme.colorScheme.onSurfaceVariant
                            )
                        }
                    }
                    msg.messageType == "poll" -> {
                        InteractivePollContent(
                            decryptedText = decryptedText,
                            pollVotes = msg.pollVotes,
                            onVote = onVotePoll
                        )
                    }
                    msg.messageType == "contact" -> {
                        Surface(
                            color = MaterialTheme.colorScheme.surface.copy(alpha = 0.55f),
                            shape = RoundedCornerShape(12.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Row(
                                modifier = Modifier.padding(10.dp),
                                verticalAlignment = Alignment.CenterVertically
                            ) {
                                Icon(Icons.Filled.Person, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                                Spacer(Modifier.width(8.dp))
                                Text(decryptedText, style = MaterialTheme.typography.bodyMedium, fontWeight = FontWeight.Medium)
                            }
                        }
                    }
                    else -> {
                        if (msg.mediaUri.isNotBlank()) {
                            AsyncImage(
                                model = msg.mediaUri,
                                contentDescription = "Encrypted photo attachment",
                                contentScale = ContentScale.Crop,
                                modifier = Modifier
                                    .fillMaxWidth()
                                    .height(165.dp)
                                    .clip(RoundedCornerShape(12.dp))
                                    .padding(bottom = 6.dp)
                            )
                        }
                        if (msg.messageType == "voice") {
                            Row(
                                verticalAlignment = Alignment.CenterVertically,
                                horizontalArrangement = Arrangement.spacedBy(8.dp)
                            ) {
                                Icon(
                                    imageVector = Icons.Filled.Mic,
                                    contentDescription = null,
                                    tint = MaterialTheme.colorScheme.primary,
                                    modifier = Modifier.size(20.dp)
                                )
                                Column(modifier = Modifier.weight(1f)) {
                                    Text(
                                        text = "Encrypted Voice Note • ${msg.voiceDurationSec}s",
                                        style = MaterialTheme.typography.bodyMedium,
                                        fontWeight = FontWeight.Medium
                                    )
                                    LinearProgressIndicator(
                                        progress = { 0.65f },
                                        modifier = Modifier
                                            .fillMaxWidth()
                                            .padding(top = 4.dp)
                                            .height(4.dp)
                                            .clip(RoundedCornerShape(2.dp))
                                    )
                                }
                            }
                        } else {
                            Text(
                                text = parseFormattedChatText(decryptedText),
                                style = MaterialTheme.typography.bodyLarge.copy(fontSize = textSize),
                                fontStyle = if (msg.deletedForAll) FontStyle.Italic else FontStyle.Normal
                            )
                        }
                    }
                }

                Spacer(Modifier.height(4.dp))
                Row(
                    horizontalArrangement = Arrangement.spacedBy(5.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    modifier = Modifier.align(Alignment.End)
                ) {
                    if (msg.reactionEmoji.isNotBlank()) {
                        Text(text = msg.reactionEmoji, style = MaterialTheme.typography.bodySmall)
                    }
                    if (msg.starred) {
                        Icon(
                            imageVector = Icons.Filled.Star,
                            contentDescription = "Starred",
                            tint = MaterialTheme.colorScheme.tertiary,
                            modifier = Modifier.size(12.dp)
                        )
                    }
                    if (msg.edited && !msg.deletedForAll) {
                        Text(
                            text = "edited",
                            style = MaterialTheme.typography.labelSmall,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                    Text(
                        text = timeLabel,
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                    if (isMe) {
                        Icon(
                            imageVector = Icons.Filled.DoneAll,
                            contentDescription = "Encrypted & Delivered",
                            tint = MaterialTheme.colorScheme.primary,
                            modifier = Modifier.size(13.dp)
                        )
                    }
                }
            }

            DropdownMenu(expanded = menuOpen, onDismissRequest = { menuOpen = false }) {
                Row(
                    modifier = Modifier.padding(horizontal = 8.dp, vertical = 4.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp)
                ) {
                    listOf("❤️", "👍", "🔥", "😂", "🔐", "🙏").forEach { emoji ->
                        TextButton(
                            onClick = { menuOpen = false; onReact(emoji) },
                            contentPadding = PaddingValues(2.dp)
                        ) { Text(emoji) }
                    }
                }
                DropdownMenuItem(
                    text = { Text("Reply") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Reply, contentDescription = null) },
                    onClick = { menuOpen = false; onReply() }
                )
                DropdownMenuItem(
                    text = { Text("Forward") },
                    leadingIcon = { Icon(Icons.AutoMirrored.Filled.Forward, contentDescription = null) },
                    onClick = { menuOpen = false; onForward() }
                )
                DropdownMenuItem(
                    text = { Text("Copy Text") },
                    leadingIcon = { Icon(Icons.Filled.ContentCopy, contentDescription = null) },
                    onClick = {
                        menuOpen = false
                        val cb = context.getSystemService(Context.CLIPBOARD_SERVICE) as? ClipboardManager
                        cb?.setPrimaryClip(ClipData.newPlainText("PulseTalk Message", decryptedText))
                    }
                )
                DropdownMenuItem(
                    text = { Text("Pin to Top") },
                    leadingIcon = { Icon(Icons.Filled.PushPin, contentDescription = null) },
                    onClick = { menuOpen = false; onPinMessage() }
                )
                DropdownMenuItem(
                    text = { Text("AI Translate") },
                    leadingIcon = { Icon(Icons.Filled.Translate, contentDescription = null) },
                    onClick = { menuOpen = false; showTranslateMenu = true }
                )
                DropdownMenuItem(
                    text = { Text(if (msg.starred) "Unstar" else "Star message") },
                    leadingIcon = { Icon(Icons.Filled.Star, contentDescription = null) },
                    onClick = { menuOpen = false; onToggleStar() }
                )
                if (isMe && !msg.deletedForAll) {
                    DropdownMenuItem(
                        text = { Text("Edit") },
                        leadingIcon = { Icon(Icons.Filled.Edit, contentDescription = null) },
                        onClick = { menuOpen = false; onEdit() }
                    )
                    DropdownMenuItem(
                        text = { Text("Delete for everyone") },
                        leadingIcon = { Icon(Icons.Filled.Delete, contentDescription = null) },
                        onClick = { menuOpen = false; onDeleteForAll() }
                    )
                }
            }
        }
    }

    if (showTranslateMenu) {
        AlertDialog(
            onDismissRequest = { showTranslateMenu = false },
            title = { Text("AI Translate Message") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("English", "Bengali", "Hindi", "Spanish", "French", "Arabic").forEach { lang ->
                        OutlinedButton(
                            onClick = {
                                showTranslateMenu = false
                                onTranslate(lang)
                            },
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text("Translate to $lang")
                        }
                    }
                }
            },
            confirmButton = {
                TextButton(onClick = { showTranslateMenu = false }) { Text("Cancel") }
            }
        )
    }
}

@Composable
private fun InteractivePollContent(
    decryptedText: String,
    pollVotes: String,
    onVote: (Int) -> Unit
) {
    val clean = decryptedText.removePrefix("POLL:").trim()
    val bracketStart = clean.lastIndexOf('[')
    val bracketEnd = clean.lastIndexOf(']')
    val question = if (bracketStart > 0) clean.substring(0, bracketStart).trim() else clean
    val options = if (bracketStart in 0 until bracketEnd) {
        clean.substring(bracketStart + 1, bracketEnd).split("|").map { it.trim() }
    } else listOf("Yes", "No")
    val counts = pollVotes.split(":").map { it.toIntOrNull() ?: 0 }
    val c0 = counts.getOrElse(0) { 0 }
    val c1 = counts.getOrElse(1) { 0 }
    val total = (c0 + c1).coerceAtLeast(1)

    Column(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        Text(
            text = "📊 $question",
            style = MaterialTheme.typography.titleSmall,
            fontWeight = FontWeight.Bold
        )
        listOf(0 to options.getOrElse(0) { "Yes" }, 1 to options.getOrElse(1) { "No" }).forEach { (idx, label) ->
            val votes = if (idx == 0) c0 else c1
            Surface(
                color = MaterialTheme.colorScheme.surface.copy(alpha = 0.65f),
                shape = RoundedCornerShape(10.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable { onVote(idx) }
            ) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Row(
                        modifier = Modifier.fillMaxWidth(),
                        horizontalArrangement = Arrangement.SpaceBetween
                    ) {
                        Text(label, style = MaterialTheme.typography.bodySmall, fontWeight = FontWeight.Medium)
                        Text("$votes votes", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                    }
                    Spacer(Modifier.height(4.dp))
                    LinearProgressIndicator(
                        progress = { votes.toFloat() / total.toFloat() },
                        modifier = Modifier
                            .fillMaxWidth()
                            .height(5.dp)
                            .clip(RoundedCornerShape(3.dp))
                    )
                }
            }
        }
    }
}

@Composable
fun AiAssistantStrip(
    aiState: AiUiState,
    onSelectReply: (String) -> Unit,
    onDismiss: () -> Unit
) {
    Card(
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.secondaryContainer.copy(alpha = 0.9f)
        ),
        shape = RoundedCornerShape(topStart = 18.dp, topEnd = 18.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Column(modifier = Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Icon(
                    imageVector = Icons.Filled.AutoAwesome,
                    contentDescription = null,
                    tint = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.size(16.dp)
                )
                Spacer(Modifier.width(6.dp))
                Text(
                    text = "Gemini AI Copilot",
                    style = MaterialTheme.typography.labelLarge,
                    color = MaterialTheme.colorScheme.onSecondaryContainer,
                    modifier = Modifier.weight(1f)
                )
                IconButton(onClick = onDismiss, modifier = Modifier.size(24.dp)) {
                    Icon(Icons.Filled.Close, contentDescription = "Close AI panel", modifier = Modifier.size(16.dp))
                }
            }
            Spacer(Modifier.height(6.dp))
            when (aiState) {
                is AiUiState.Loading -> {
                    LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
                }
                is AiUiState.SmartRepliesReady -> {
                    Row(
                        modifier = Modifier.horizontalScroll(rememberScrollState()),
                        horizontalArrangement = Arrangement.spacedBy(8.dp)
                    ) {
                        aiState.replies.forEach { reply ->
                            SuggestionChip(
                                onClick = { onSelectReply(reply) },
                                label = { Text(reply, maxLines = 1, overflow = TextOverflow.Ellipsis) }
                            )
                        }
                    }
                }
                is AiUiState.SummaryReady -> {
                    Text(
                        text = aiState.summary,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                is AiUiState.TranslationReady -> {
                    Text(
                        text = "${aiState.language}: ${aiState.translatedText}",
                        style = MaterialTheme.typography.bodySmall,
                        fontWeight = FontWeight.Medium,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                is AiUiState.RewriteStreaming -> {
                    Text(
                        text = aiState.text,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                is AiUiState.RewriteReady -> {
                    Text(
                        text = "Draft updated with AI.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer
                    )
                }
                is AiUiState.Error -> {
                    Text(
                        text = aiState.message,
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.error
                    )
                }
                else -> Unit
            }
        }
    }
}

@Composable
fun ForwardMessageDialog(
    chats: List<ChatThread>,
    onDismiss: () -> Unit,
    onForwardTo: (ChatThread) -> Unit
) {
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Forward Encrypted Message") },
        text = {
            LazyColumn(
                modifier = Modifier.height(260.dp),
                verticalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                items(chats, key = { it.id }) { target ->
                    OutlinedButton(
                        onClick = { onForwardTo(target) },
                        modifier = Modifier.fillMaxWidth()
                    ) {
                        Text(target.title, maxLines = 1, overflow = TextOverflow.Ellipsis)
                    }
                }
            }
        },
        confirmButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}

@Composable
fun ChatLockPinDialog(
    title: String,
    subtitle: String,
    onDismiss: () -> Unit,
    onSubmitPin: (String) -> Unit
) {
    var pin by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text(subtitle, style = MaterialTheme.typography.bodySmall)
                OutlinedTextField(
                    value = pin,
                    onValueChange = { if (it.length <= 6 && it.all { ch -> ch.isDigit() }) pin = it },
                    label = { Text("4-6 Digit PIN") },
                    visualTransformation = PasswordVisualTransformation(),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = { onSubmitPin(pin) },
                enabled = pin.length >= 4
            ) { Text("Verify") }
        },
        dismissButton = {
            TextButton(onClick = onDismiss) { Text("Cancel") }
        }
    )
}
