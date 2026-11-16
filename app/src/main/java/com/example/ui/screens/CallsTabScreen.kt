package com.example.ui.screens

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
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
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.CallMade
import androidx.compose.material.icons.automirrored.filled.CallMissed
import androidx.compose.material.icons.filled.Call
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.GraphicEq
import androidx.compose.material.icons.filled.Link
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Shield
import androidx.compose.material.icons.filled.Star
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.content.ContextCompat
import com.example.data.model.ContactRecord
import com.example.data.model.EncryptedCall
import com.example.data.model.UiState
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun CallsTabScreen(
    callsState: UiState<List<EncryptedCall>>,
    contacts: List<ContactRecord>,
    onStartVoiceCall: (calleeId: String, calleeName: String, hasMicPermission: Boolean, isEchoTest: Boolean) -> Unit,
    onCreateCallLink: ((EncryptedCall) -> Unit) -> Unit,
    onDeleteCall: (String) -> Unit
) {
    val context = LocalContext.current
    var pendingCallTarget by remember { mutableStateOf<Triple<String, String, Boolean>?>(null) }
    var callFilter by remember { mutableStateOf("All") }

    val micLauncher = rememberLauncherForActivityResult(
        contract = ActivityResultContracts.RequestPermission()
    ) { granted ->
        val target = pendingCallTarget
        if (target != null) {
            onStartVoiceCall(target.first, target.second, granted, target.third)
            pendingCallTarget = null
        }
    }

    fun launchEncryptedCall(calleeId: String, calleeName: String, isEchoTest: Boolean) {
        val granted = ContextCompat.checkSelfPermission(
            context,
            Manifest.permission.RECORD_AUDIO
        ) == PackageManager.PERMISSION_GRANTED
        if (granted) {
            onStartVoiceCall(calleeId, calleeName, true, isEchoTest)
        } else {
            pendingCallTarget = Triple(calleeId, calleeName, isEchoTest)
            micLauncher.launch(Manifest.permission.RECORD_AUDIO)
        }
    }

    val activeContacts = contacts.filter { !it.blocked }
    val favoriteContacts = activeContacts.filter { it.favorite }

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(
                            imageVector = Icons.Filled.GraphicEq,
                            contentDescription = null,
                            tint = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "E2EE Voice & Shareable Call Links",
                            style = MaterialTheme.typography.titleMedium,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Ephemeral ECDH P-256 key exchange, 4-emoji SAS authentication, live AudioRecord RMS waveform, and one-tap call links.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        Button(
                            onClick = { launchEncryptedCall("echo_test", "PulseTalk Echo Lab", true) },
                            modifier = Modifier.weight(1f).testTag("start_echo_call_button")
                        ) {
                            Icon(Icons.Filled.Call, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Voice Test")
                        }
                        OutlinedButton(
                            onClick = {
                                onCreateCallLink { call ->
                                    val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                        type = "text/plain"
                                        putExtra(
                                            Intent.EXTRA_TEXT,
                                            "Join my end-to-end encrypted PulseTalk voice call: https://pulsetalk.app/call/${call.callLinkCode} (SAS: ${call.sasCode})"
                                        )
                                    }
                                    context.startActivity(Intent.createChooser(shareIntent, "Share E2EE Call Link"))
                                }
                            },
                            modifier = Modifier.weight(1f)
                        ) {
                            Icon(Icons.Filled.Link, contentDescription = null, modifier = Modifier.size(16.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Call Link")
                        }
                    }
                }
            }
        }

        if (favoriteContacts.isNotEmpty()) {
            item {
                Text("Favorite Speed-Dial", style = MaterialTheme.typography.titleLarge)
            }
            items(favoriteContacts, key = { "fav_call_${it.id}" }) { contact ->
                ContactCallRow(
                    contact = contact,
                    isFav = true,
                    onCall = { launchEncryptedCall(contact.contactUid, contact.displayName, false) }
                )
            }
        }

        if (activeContacts.isNotEmpty()) {
            item {
                Text("Call Contacts", style = MaterialTheme.typography.titleLarge)
            }
            items(activeContacts, key = { "call_contact_${it.id}" }) { contact ->
                ContactCallRow(
                    contact = contact,
                    isFav = contact.favorite,
                    onCall = { launchEncryptedCall(contact.contactUid, contact.displayName, false) }
                )
            }
        }

        item {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                Text("Call History", style = MaterialTheme.typography.titleLarge)
                Row(horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    listOf("All", "Missed", "Links").forEach { f ->
                        FilterChip(
                            selected = callFilter == f,
                            onClick = { callFilter = f },
                            label = { Text(f) }
                        )
                    }
                }
            }
        }

        when (callsState) {
            is UiState.Loading -> {
                item {
                    Box(Modifier.fillMaxWidth().padding(24.dp), contentAlignment = Alignment.Center) {
                        CircularProgressIndicator()
                    }
                }
            }
            is UiState.Error -> {
                item { Text(callsState.message, color = MaterialTheme.colorScheme.error) }
            }
            is UiState.Success -> {
                val filtered = callsState.data.filter { call ->
                    when (callFilter) {
                        "Missed" -> call.status == "missed"
                        "Links" -> call.callMode == "link" || call.callLinkCode.isNotBlank()
                        else -> true
                    }
                }
                if (filtered.isEmpty()) {
                    item {
                        Surface(
                            color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                            shape = RoundedCornerShape(16.dp),
                            modifier = Modifier.fillMaxWidth()
                        ) {
                            Text(
                                text = "No matching call logs yet. Start a Voice Test or create a Call Link above.",
                                style = MaterialTheme.typography.bodyMedium,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(16.dp)
                            )
                        }
                    }
                } else {
                    items(filtered, key = { it.id }) { call ->
                        CallLogCard(
                            call = call,
                            onRedial = { launchEncryptedCall(call.calleeId, call.calleeName, call.isEchoTest) },
                            onDelete = { onDeleteCall(call.id) }
                        )
                    }
                }
            }
        }
    }
}

@Composable
private fun ContactCallRow(contact: ContactRecord, isFav: Boolean, onCall: () -> Unit) {
    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(
                modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant),
                contentAlignment = Alignment.Center
            ) {
                Icon(
                    imageVector = if (isFav) Icons.Filled.Star else Icons.Filled.Shield,
                    contentDescription = null,
                    tint = if (isFav) MaterialTheme.colorScheme.tertiary else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(contact.displayName, style = MaterialTheme.typography.titleMedium)
                Text(
                    text = "Safety: ${contact.safetyNumber.take(17)}…",
                    style = MaterialTheme.typography.labelSmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }
            IconButton(onClick = onCall) {
                Icon(Icons.Filled.Call, contentDescription = "Call ${contact.displayName}", tint = MaterialTheme.colorScheme.primary)
            }
        }
    }
}

@Composable
private fun CallLogCard(call: EncryptedCall, onRedial: () -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val dateStr = call.createdAt?.toDate()?.let { fmt.format(it) } ?: "Just now"
    val isMissed = call.status == "missed"

    Card(
        colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
        shape = RoundedCornerShape(16.dp),
        modifier = Modifier.fillMaxWidth()
    ) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(
                imageVector = if (isMissed) Icons.AutoMirrored.Filled.CallMissed else Icons.AutoMirrored.Filled.CallMade,
                contentDescription = null,
                tint = if (isMissed) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
            )
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(call.calleeName, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(6.dp)) {
                    Icon(Icons.Filled.Lock, contentDescription = null, modifier = Modifier.size(12.dp), tint = MaterialTheme.colorScheme.primary)
                    Text(
                        text = "$dateStr • ${call.durationSeconds}s • SAS ${call.sasCode}",
                        style = MaterialTheme.typography.labelSmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            }
            IconButton(onClick = onRedial) {
                Icon(Icons.Filled.Call, contentDescription = "Redial", tint = MaterialTheme.colorScheme.primary)
            }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete call log", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
