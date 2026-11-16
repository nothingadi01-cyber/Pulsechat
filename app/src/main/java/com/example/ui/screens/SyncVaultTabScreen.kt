package com.example.ui.screens

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
import androidx.compose.material.icons.filled.AddLink
import androidx.compose.material.icons.filled.Backup
import androidx.compose.material.icons.filled.CloudDone
import androidx.compose.material.icons.filled.DeleteOutline
import androidx.compose.material.icons.filled.Devices
import androidx.compose.material.icons.filled.LaptopMac
import androidx.compose.material.icons.filled.PhoneAndroid
import androidx.compose.material.icons.filled.Sync
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
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
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.data.model.BackupRestoreAudit
import com.example.data.model.CloudBackup
import com.example.data.model.LinkedDevice
import java.text.SimpleDateFormat
import java.util.Locale

@Composable
fun SyncVaultTabScreen(
    devices: List<LinkedDevice>,
    backups: List<CloudBackup>,
    autoBackupFreq: String,
    onLinkDevice: (name: String, platform: String) -> Unit,
    onSyncHandoff: (deviceId: String, note: String) -> Unit,
    onRevokeDevice: (deviceId: String) -> Unit,
    onCreateBackup: (label: String) -> Unit,
    onAuditBackup: (CloudBackup) -> BackupRestoreAudit,
    onDeleteBackup: (backupId: String) -> Unit,
    onSetAutoBackupFreq: (String) -> Unit
) {
    var showLinkDialog by remember { mutableStateOf(false) }
    var handoffDraft by remember { mutableStateOf("") }
    var showBackupDialog by remember { mutableStateOf(false) }
    var auditResult by remember { mutableStateOf<BackupRestoreAudit?>(null) }

    val activeDevices = devices.filter { it.status == "active" }
    val currentDevice = activeDevices.firstOrNull { it.isCurrent } ?: activeDevices.firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize(),
        contentPadding = PaddingValues(start = 16.dp, end = 16.dp, top = 8.dp, bottom = 92.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.secondaryContainer),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.Devices, contentDescription = null, tint = MaterialTheme.colorScheme.onSecondaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Multi-Device Sync (${activeDevices.size} Active)",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onSecondaryContainer
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Independent per-device ECDH subkeys synchronize messages, calls, and encrypted handoff notes in real time.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSecondaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(12.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                        Button(onClick = { showLinkDialog = true }, modifier = Modifier.testTag("link_device_button")) {
                            Icon(Icons.Filled.AddLink, contentDescription = null, modifier = Modifier.size(18.dp))
                            Spacer(Modifier.width(6.dp))
                            Text("Link Device")
                        }
                        if (currentDevice != null) {
                            OutlinedButton(onClick = {
                                onSyncHandoff(currentDevice.id, handoffDraft.ifBlank { "Heartbeat synced at ${System.currentTimeMillis() / 1000}" })
                            }) {
                                Icon(Icons.Filled.Sync, contentDescription = null, modifier = Modifier.size(18.dp))
                                Spacer(Modifier.width(6.dp))
                                Text("Sync Now")
                            }
                        }
                    }
                }
            }
        }

        if (currentDevice != null) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Text("Cross-Device Encrypted Handoff", style = MaterialTheme.typography.titleMedium)
                        OutlinedTextField(
                            value = handoffDraft,
                            onValueChange = { handoffDraft = it },
                            placeholder = { Text(currentDevice.handoffNote.ifBlank { "Share a snippet across your linked devices…" }) },
                            singleLine = true,
                            modifier = Modifier.fillMaxWidth()
                        )
                        Button(
                            onClick = {
                                if (handoffDraft.isNotBlank()) {
                                    onSyncHandoff(currentDevice.id, handoffDraft)
                                    handoffDraft = ""
                                }
                            },
                            enabled = handoffDraft.isNotBlank(),
                            modifier = Modifier.align(Alignment.End)
                        ) { Text("Push Encrypted Handoff") }
                    }
                }
            }
        }

        item { Text("Linked Sessions", style = MaterialTheme.typography.titleLarge) }

        items(devices, key = { it.id }) { device ->
            DeviceCard(device = device, onRevoke = { onRevokeDevice(device.id) })
        }

        item {
            Spacer(Modifier.height(6.dp))
            Card(
                colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                shape = RoundedCornerShape(22.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Column(modifier = Modifier.padding(18.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Filled.CloudDone, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                        Spacer(Modifier.width(8.dp))
                        Text(
                            text = "Encrypted Cloud Backup Vault",
                            style = MaterialTheme.typography.titleLarge,
                            color = MaterialTheme.colorScheme.onPrimaryContainer
                        )
                    }
                    Spacer(Modifier.height(6.dp))
                    Text(
                        text = "Snapshots are encrypted client-side with AES-256-GCM and verified with a SHA-256 manifest checksum.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                    )
                    Spacer(Modifier.height(10.dp))
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Daily", "Weekly", "Manual").forEach { freq ->
                            FilterChip(selected = autoBackupFreq == freq, onClick = { onSetAutoBackupFreq(freq) }, label = { Text(freq) })
                        }
                    }
                    Spacer(Modifier.height(10.dp))
                    Button(onClick = { showBackupDialog = true }, modifier = Modifier.testTag("create_backup_button")) {
                        Icon(Icons.Filled.Backup, contentDescription = null, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(8.dp))
                        Text("Back Up Chats Now")
                    }
                }
            }
        }

        item { Text("Backup Snapshots (${backups.size})", style = MaterialTheme.typography.titleLarge) }

        if (backups.isEmpty()) {
            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Text(
                        text = "No cloud backups created yet. Tap 'Back Up Chats Now' above to create an AES-256-GCM snapshot.",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        } else {
            items(backups, key = { it.id }) { backup ->
                BackupCard(
                    backup = backup,
                    onAudit = { auditResult = onAuditBackup(backup) },
                    onDelete = { onDeleteBackup(backup.id) }
                )
            }
        }
    }

    if (showLinkDialog) {
        var devName by remember { mutableStateOf("PulseTalk Web Desktop") }
        var platform by remember { mutableStateOf("Chrome / macOS") }
        AlertDialog(
            onDismissRequest = { showLinkDialog = false },
            title = { Text("Pair Companion Device") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    Text("Generate an isolated ECDH subkey for a companion browser, tablet, or desktop session.", style = MaterialTheme.typography.bodySmall)
                    OutlinedTextField(value = devName, onValueChange = { devName = it }, label = { Text("Device Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = platform, onValueChange = { platform = it }, label = { Text("Platform") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = { showLinkDialog = false; onLinkDevice(devName, platform) }) { Text("Authorize Device") }
            },
            dismissButton = { TextButton(onClick = { showLinkDialog = false }) { Text("Cancel") } }
        )
    }

    if (showBackupDialog) {
        var label by remember { mutableStateOf("Full Encrypted Backup") }
        AlertDialog(
            onDismissRequest = { showBackupDialog = false },
            title = { Text("Create Cloud Backup") },
            text = {
                OutlinedTextField(value = label, onValueChange = { label = it }, label = { Text("Snapshot Label") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            },
            confirmButton = {
                Button(onClick = { showBackupDialog = false; onCreateBackup(label) }) { Text("Encrypt & Upload") }
            },
            dismissButton = { TextButton(onClick = { showBackupDialog = false }) { Text("Cancel") } }
        )
    }

    auditResult?.let { audit ->
        AlertDialog(
            onDismissRequest = { auditResult = null },
            title = { Text("Backup Checksum Audit") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                    Text(
                        text = if (audit.checksumValid) "✓ SHA-256 Checksum Verified — Integrity intact" else "⚠ Checksum mismatch detected",
                        color = if (audit.checksumValid) MaterialTheme.colorScheme.primary else MaterialTheme.colorScheme.error,
                        fontWeight = FontWeight.Bold
                    )
                    Text("Snapshot: ${audit.label}", style = MaterialTheme.typography.bodyMedium)
                    Text(
                        text = "Encrypted manifest verified (${audit.manifestEntries.size} segment(s)). All active chats sync automatically via Firestore.",
                        style = MaterialTheme.typography.bodySmall,
                        color = MaterialTheme.colorScheme.onSurfaceVariant
                    )
                }
            },
            confirmButton = { TextButton(onClick = { auditResult = null }) { Text("Done") } }
        )
    }
}

@Composable
private fun DeviceCard(device: LinkedDevice, onRevoke: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM dd, HH:mm", Locale.getDefault()) }
    val syncStr = device.lastSyncAt?.toDate()?.let { fmt.format(it) } ?: "Active"
    val isRevoked = device.status == "revoked"

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Box(modifier = Modifier.size(44.dp).clip(CircleShape).background(MaterialTheme.colorScheme.surfaceVariant), contentAlignment = Alignment.Center) {
                Icon(
                    imageVector = if (device.isCurrent) Icons.Filled.PhoneAndroid else Icons.Filled.LaptopMac,
                    contentDescription = null,
                    tint = if (isRevoked) MaterialTheme.colorScheme.error else MaterialTheme.colorScheme.primary
                )
            }
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Text(device.deviceName, style = MaterialTheme.typography.titleMedium)
                    if (device.isCurrent) {
                        Spacer(Modifier.width(6.dp))
                        Surface(color = MaterialTheme.colorScheme.primaryContainer, shape = RoundedCornerShape(6.dp)) {
                            Text("This device", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp))
                        }
                    }
                }
                Text("${device.platform} • ${device.status.uppercase()} • $syncStr", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("Key: ${device.keyFingerprint.take(19)}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                if (device.handoffNote.isNotBlank()) {
                    Text("Handoff: ${device.handoffNote}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.tertiary)
                }
            }
            if (!device.isCurrent && !isRevoked) {
                TextButton(onClick = onRevoke) { Text("Revoke", color = MaterialTheme.colorScheme.error) }
            }
        }
    }
}

@Composable
private fun BackupCard(backup: CloudBackup, onAudit: () -> Unit, onDelete: () -> Unit) {
    val fmt = remember { SimpleDateFormat("MMM dd, yyyy HH:mm", Locale.getDefault()) }
    val dateStr = backup.createdAt?.toDate()?.let { fmt.format(it) } ?: "Just now"
    val kb = (backup.sizeBytes / 1024L).coerceAtLeast(1L)

    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Row(modifier = Modifier.padding(14.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(12.dp))
            Column(modifier = Modifier.weight(1f)) {
                Text(backup.label, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.SemiBold)
                Text("$dateStr • ${backup.chatCount} chats • ${kb} KB • ${backup.encryptionMode}", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                Text("SHA-256: ${backup.checksumSha256.take(20)}…", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
            }
            TextButton(onClick = onAudit) { Text("Verify") }
            IconButton(onClick = onDelete) {
                Icon(Icons.Filled.DeleteOutline, contentDescription = "Delete backup", tint = MaterialTheme.colorScheme.error)
            }
        }
    }
}
