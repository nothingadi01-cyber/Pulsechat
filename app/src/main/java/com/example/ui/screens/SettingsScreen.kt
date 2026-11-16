package com.example.ui.screens

import androidx.activity.compose.BackHandler
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.WindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.automirrored.filled.Logout
import androidx.compose.material.icons.filled.AutoAwesome
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Lock
import androidx.compose.material.icons.filled.Palette
import androidx.compose.material.icons.filled.Security
import androidx.compose.material.icons.filled.VerifiedUser
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButton
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.core.LocalAppPreferences
import com.example.data.model.UserProfile

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun SettingsScreen(
    profile: UserProfile?,
    prefs: LocalAppPreferences,
    onBack: () -> Unit,
    onUpdateProfile: (Map<String, Any>) -> Unit,
    onDarkModeChange: (String) -> Unit,
    onScreenshotGuardChange: (Boolean) -> Unit,
    onCompactModeChange: (Boolean) -> Unit,
    onWallpaperChange: (Boolean) -> Unit,
    onWallpaperPresetChange: (String) -> Unit,
    onFontSizeScaleChange: (String) -> Unit,
    onEnterToSendChange: (Boolean) -> Unit,
    onCallNoiseSuppressionChange: (Boolean) -> Unit,
    onAiToneChange: (String) -> Unit,
    onSetChatLockPin: (String) -> Unit,
    onAddQuickReply: (String) -> Unit,
    onSignOut: () -> Unit
) {
    BackHandler { onBack() }

    var showEditProfileDialog by remember { mutableStateOf(false) }
    var showCapabilitiesDialog by remember { mutableStateOf(false) }
    var showSetPinDialog by remember { mutableStateOf(false) }
    var newQuickReply by remember { mutableStateOf("") }

    Scaffold(
        contentWindowInsets = WindowInsets(0, 0, 0, 0),
        topBar = {
            TopAppBar(
                navigationIcon = {
                    IconButton(onClick = onBack, modifier = Modifier.testTag("settings_back_button")) {
                        Icon(Icons.AutoMirrored.Filled.ArrowBack, contentDescription = "Back")
                    }
                },
                title = { Text(stringResource(R.string.settings_title)) }
            )
        }
    ) { innerPadding ->
        LazyColumn(
            modifier = Modifier
                .fillMaxSize()
                .padding(innerPadding)
                .navigationBarsPadding(),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(14.dp)
        ) {
            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth().clickable { showEditProfileDialog = true }
                ) {
                    Row(modifier = Modifier.padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                        Image(
                            painter = painterResource(id = R.drawable.pulsetalk_icon),
                            contentDescription = "PulseTalk avatar",
                            modifier = Modifier.size(60.dp).clip(CircleShape)
                        )
                        Spacer(Modifier.width(14.dp))
                        Column(modifier = Modifier.weight(1f)) {
                            Text(profile?.displayName ?: "PulseTalk User", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
                            Text(profile?.about ?: "Protected by PulseTalk E2EE", style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                            Spacer(Modifier.height(4.dp))
                            Text("Key ID: ${profile?.publicKeyFingerprint?.take(19) ?: "ECDH-P256"}", style = MaterialTheme.typography.labelSmall, color = MaterialTheme.colorScheme.primary)
                        }
                        Text("Edit", color = MaterialTheme.colorScheme.primary, style = MaterialTheme.typography.labelLarge)
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Privacy & Zero-Trust Security", icon = Icons.Filled.Security) {
                    SettingsToggleRow("Screenshot & Screen Recording Guard", "Sets Android FLAG_SECURE on window", prefs.screenshotGuard, onScreenshotGuardChange)
                    SettingsToggleRow("Two-Step Verification PIN", "Extra protection when registering new devices", profile?.twoStepEnabled ?: false) {
                        onUpdateProfile(mapOf("twoStepEnabled" to it))
                    }
                    SettingsToggleRow("Biometric / App Lock", "Require verification when opening PulseTalk", profile?.appLockEnabled ?: false) {
                        onUpdateProfile(mapOf("appLockEnabled" to it))
                    }
                    SettingsToggleRow("Read Receipts", "Share message read status in chats", profile?.readReceiptsEnabled ?: true) {
                        onUpdateProfile(mapOf("readReceiptsEnabled" to it))
                    }
                    SettingsToggleRow("Voice Call Noise Suppression", "Enhanced audio capture filter for E2EE calls", prefs.callNoiseSuppression, onCallNoiseSuppressionChange)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedButton(onClick = { showSetPinDialog = true }, modifier = Modifier.weight(1f)) {
                            Text(if (prefs.chatLockPinHash.isBlank()) "Set Chat Lock PIN" else "Change Chat Lock PIN")
                        }
                        if (prefs.chatLockPinHash.isNotBlank()) {
                            TextButton(onClick = { onSetChatLockPin("") }) { Text("Clear PIN") }
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Gemini AI Chat Copilot", icon = Icons.Filled.AutoAwesome) {
                    SettingsToggleRow("AI Smart Reply Suggestions", "Context-aware chips, translator & composer", profile?.aiSmartRepliesEnabled ?: true) {
                        onUpdateProfile(mapOf("aiSmartRepliesEnabled" to it))
                    }
                    Text("Default Reply Tone", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Balanced", "Professional", "Friendly", "Concise").forEach { tone ->
                            FilterChip(selected = prefs.aiReplyTone == tone, onClick = { onAiToneChange(tone) }, label = { Text(tone) })
                        }
                    }
                }
            }

            item {
                SettingsSectionCard(title = "Appearance, Wallpaper & Quick Replies", icon = Icons.Filled.Palette) {
                    Text("Theme Mode", style = MaterialTheme.typography.labelLarge)
                    Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("system" to "System", "dark" to "Obsidian Dark", "light" to "Alabaster Light").forEach { (key, label) ->
                            FilterChip(selected = prefs.darkMode == key, onClick = { onDarkModeChange(key) }, label = { Text(label) })
                        }
                    }
                    Text("Message Font Size", style = MaterialTheme.typography.labelLarge)
                    Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        listOf("Small", "Medium", "Large").forEach { scale ->
                            FilterChip(selected = prefs.fontSizeScale == scale, onClick = { onFontSizeScaleChange(scale) }, label = { Text(scale) })
                        }
                    }
                    SettingsToggleRow("Chat Wallpaper", "Custom background artwork or gradient", prefs.wallpaperEnabled, onWallpaperChange)
                    if (prefs.wallpaperEnabled) {
                        Row(modifier = Modifier.horizontalScroll(rememberScrollState()), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            listOf("Constellation", "Emerald Mist", "Midnight Ink", "Minimal").forEach { preset ->
                                FilterChip(selected = prefs.wallpaperPreset == preset, onClick = { onWallpaperPresetChange(preset) }, label = { Text(preset) })
                            }
                        }
                    }
                    SettingsToggleRow("Compact Chat List Density", "Fit more conversations per screen", prefs.compactChatList, onCompactModeChange)
                    SettingsToggleRow("Enter Key Sends Message", "Hardware keyboard quick-send", prefs.enterToSend, onEnterToSendChange)

                    Text("Add Quick-Reply Chip", style = MaterialTheme.typography.labelLarge)
                    Row(verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                        OutlinedTextField(
                            value = newQuickReply,
                            onValueChange = { newQuickReply = it },
                            placeholder = { Text("e.g., Call me in 10 mins") },
                            singleLine = true,
                            modifier = Modifier.weight(1f)
                        )
                        Button(
                            onClick = { onAddQuickReply(newQuickReply); newQuickReply = "" },
                            enabled = newQuickReply.isNotBlank()
                        ) { Text("Add") }
                    }
                }
            }

            item {
                Card(
                    colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.primaryContainer),
                    shape = RoundedCornerShape(22.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Filled.VerifiedUser, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer)
                            Spacer(Modifier.width(8.dp))
                            Text("PulseTalk™ Pro Capability Suite (135 Features)", style = MaterialTheme.typography.titleMedium, color = MaterialTheme.colorScheme.onPrimaryContainer)
                        }
                        Text(
                            text = "Explore the full architecture covering E2EE Cryptography, E2EE Voice & Call Links, Gemini AI Copilot & Translator, Multi-Device Sync, Cloud Backup Audit, Chat Lock PIN, Broadcasts, Polls & View-Once Privacy.",
                            style = MaterialTheme.typography.bodySmall,
                            color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
                        )
                        OutlinedButton(onClick = { showCapabilitiesDialog = true }) { Text("View All 135 Features") }
                    }
                }
            }

            item {
                Surface(
                    color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.65f),
                    shape = RoundedCornerShape(18.dp),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(16.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.spacedBy(4.dp)) {
                        Icon(Icons.Filled.Lock, contentDescription = null, tint = MaterialTheme.colorScheme.primary)
                        Text("PulseTalk™ Enterprise Messaging", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
                        Text(stringResource(R.string.developer_attribution), style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                    }
                }
            }

            item {
                Button(
                    onClick = onSignOut,
                    colors = ButtonDefaults.buttonColors(containerColor = MaterialTheme.colorScheme.error, contentColor = MaterialTheme.colorScheme.onError),
                    shape = RoundedCornerShape(16.dp),
                    modifier = Modifier.fillMaxWidth().height(52.dp).testTag("sign_out_button")
                ) {
                    Icon(Icons.AutoMirrored.Filled.Logout, contentDescription = null)
                    Spacer(Modifier.width(8.dp))
                    Text("Sign Out")
                }
            }
        }
    }

    if (showSetPinDialog) {
        ChatLockPinDialog(
            title = "Configure Chat Lock PIN",
            subtitle = "Set a 4-6 digit PIN to lock sensitive threads behind your Locked Chats filter.",
            onDismiss = { showSetPinDialog = false },
            onSubmitPin = { pin ->
                showSetPinDialog = false
                onSetChatLockPin(pin)
            }
        )
    }

    if (showEditProfileDialog) {
        var name by remember { mutableStateOf(profile?.displayName ?: "") }
        var about by remember { mutableStateOf(profile?.about ?: "") }
        AlertDialog(
            onDismissRequest = { showEditProfileDialog = false },
            title = { Text("Edit Profile") },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                    OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Display Name") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                    OutlinedTextField(value = about, onValueChange = { about = it }, label = { Text("About / Status Bio") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                }
            },
            confirmButton = {
                Button(onClick = {
                    showEditProfileDialog = false
                    onUpdateProfile(mapOf("displayName" to name.trim().ifBlank { "PulseTalk User" }.take(60), "about" to about.trim().take(140)))
                }) { Text("Save") }
            },
            dismissButton = { TextButton(onClick = { showEditProfileDialog = false }) { Text("Cancel") } }
        )
    }

    if (showCapabilitiesDialog) {
        val categories = remember {
            listOf(
                "1. E2EE Cryptography & Chat Lock (20)" to "ECDH P-256 keygen, AES-256-GCM cipher, 12-byte random IVs, 60-digit Safety Numbers, Identity QR Matrix, Chat Lock PIN (SHA-256), View-Once Secret Messages, Disappearing Timers (24h/7d/90d), Two-Step PIN, App Lock, FLAG_SECURE Screenshot Guard, Contact Blocking, Zero-Trust Firestore Rules & Verified Badges.",
                "2. E2EE Voice Calling & Call Links (18)" to "Ephemeral ECDH Call Key Exchange, 4-Emoji SAS Verification, Shareable Call Invite Links, Real-Time AudioRecord RMS Waveform, Noise Suppression Toggle, Speakerphone, Mic Mute, Call Timer, Encrypted Echo Lab, Missed/Links/All Filters, Favorite Speed-Dial, Redial & History Delete.",
                "3. Gemini AI Copilot & Translator (18)" to "Structured JSON Smart Replies, Streaming Tone Rewriter, Topic-to-Draft AI Composer, Multi-Language Translator (English/Bengali/Hindi/Spanish/French/Arabic), Thread Summarizer, Grammar Polish, Action-Item Extraction, App Check Attestation & Toggleable AI Control.",
                "4. WhatsApp-Grade Messaging & Media (32)" to "1:1 Direct, Multi-Member Groups, Communities, Broadcast Lists, Encrypted Vault, Interactive Live Polls, Message Forwarding, Pin Message to Header, Reply Quotes, 6-Emoji Reactions, Starred Filter, In-Chat Search, Rich Formatting (*bold*, _italic_, ~strike~, `code`), Voice Notes with Timer, Zero-Permission Photo Picker, Contact Card Sharing, Draft Auto-Save, Unread Counters, Archive/Mute, Transcript Export & Quick-Reply Chips.",
                "5. Multi-Device Sync & Cloud Backup Vault (19)" to "Primary Device Registry, Companion Pairing, Subkey Fingerprints, Live Sync Heartbeats, Visible Cross-Device Handoff Snippets, Session Revocation, AES-256-GCM Backups, SHA-256 Integrity Checksum Audit, Auto-Backup Cadence & Account-Keyed ViewModels.",
                "6. Updates, Channels & Visual Craft (28)" to "24h Encrypted Statuses, Status Emoji Reactions & Views, Verified Channels with Broadcast Publishing & Mute, Custom Adaptive Icon, 4 Wallpaper Presets, 3 Font Size Scales, Tablet NavigationRail, Obsidian Dark/Alabaster Light Themes & PulseTalk™ by Anupam."
            )
        }
        AlertDialog(
            onDismissRequest = { showCapabilitiesDialog = false },
            title = { Text("PulseTalk™ 135-Feature Architecture") },
            text = {
                LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), modifier = Modifier.height(340.dp)) {
                    items(categories.size) { idx ->
                        val (heading, desc) = categories[idx]
                        Column {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Icon(Icons.Filled.CheckCircle, contentDescription = null, tint = MaterialTheme.colorScheme.primary, modifier = Modifier.size(16.dp))
                                Spacer(Modifier.width(6.dp))
                                Text(heading, style = MaterialTheme.typography.titleSmall, fontWeight = FontWeight.Bold)
                            }
                            Text(desc, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
                        }
                    }
                }
            },
            confirmButton = { TextButton(onClick = { showCapabilitiesDialog = false }) { Text("Done") } }
        )
    }
}

@Composable
private fun SettingsSectionCard(
    title: String,
    icon: androidx.compose.ui.graphics.vector.ImageVector,
    content: @Composable () -> Unit
) {
    Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.surface), shape = RoundedCornerShape(20.dp), modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(modifier = Modifier.size(32.dp).clip(CircleShape).background(MaterialTheme.colorScheme.primaryContainer), contentAlignment = Alignment.Center) {
                    Icon(icon, contentDescription = null, tint = MaterialTheme.colorScheme.onPrimaryContainer, modifier = Modifier.size(18.dp))
                }
                Spacer(Modifier.width(10.dp))
                Text(title, style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold)
            }
            content()
        }
    }
}

@Composable
private fun SettingsToggleRow(title: String, subtitle: String, checked: Boolean, onCheckedChange: (Boolean) -> Unit) {
    Row(modifier = Modifier.fillMaxWidth(), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
        Column(modifier = Modifier.weight(1f).padding(end = 12.dp)) {
            Text(title, style = MaterialTheme.typography.bodyLarge)
            Text(subtitle, style = MaterialTheme.typography.bodySmall, color = MaterialTheme.colorScheme.onSurfaceVariant)
        }
        Switch(checked = checked, onCheckedChange = onCheckedChange)
    }
}
