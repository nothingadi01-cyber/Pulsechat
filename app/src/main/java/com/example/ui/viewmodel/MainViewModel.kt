package com.example.ui.viewmodel

import android.util.Log
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.example.core.AudioCallEngine
import com.example.core.E2eeCryptoManager
import com.example.core.LocalAppPreferences
import com.example.core.PreferencesManager
import com.example.data.model.ChannelFeed
import com.example.data.model.ChatThread
import com.example.data.model.CloudBackup
import com.example.data.model.ContactRecord
import com.example.data.model.EncryptedCall
import com.example.data.model.EncryptedMessage
import com.example.data.model.LinkedDevice
import com.example.data.model.StatusUpdate
import com.example.data.model.UiState
import com.example.data.model.UserProfile
import com.example.data.repository.AiSuggestionsRepository
import com.example.data.repository.PulseTalkRepository
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

sealed interface AiUiState {
    data object Idle : AiUiState
    data object Loading : AiUiState
    data class SmartRepliesReady(val replies: List<String>) : AiUiState
    data class SummaryReady(val summary: String) : AiUiState
    data class TranslationReady(val translatedText: String, val language: String) : AiUiState
    data class RewriteStreaming(val text: String) : AiUiState
    data class RewriteReady(val text: String) : AiUiState
    data class Error(val message: String) : AiUiState
}

class MainViewModel(
    private val repository: PulseTalkRepository,
    private val aiRepository: AiSuggestionsRepository,
    private val preferencesManager: PreferencesManager,
    val audioCallEngine: AudioCallEngine,
    val currentUserId: String,
    initialDisplayName: String,
    initialEmail: String
) : ViewModel() {

    private val _statusBanner = MutableStateFlow<String?>(null)
    val statusBanner: StateFlow<String?> = _statusBanner.asStateFlow()

    private val _aiState = MutableStateFlow<AiUiState>(AiUiState.Idle)
    val aiState: StateFlow<AiUiState> = _aiState.asStateFlow()

    private val _activeCall = MutableStateFlow<EncryptedCall?>(null)
    val activeCall: StateFlow<EncryptedCall?> = _activeCall.asStateFlow()

    private val _callElapsedSec = MutableStateFlow(0L)
    val callElapsedSec: StateFlow<Long> = _callElapsedSec.asStateFlow()

    private val _selectedChatId = MutableStateFlow<String?>(null)
    val selectedChatId: StateFlow<String?> = _selectedChatId.asStateFlow()

    private var callTimerJob: Job? = null
    private var messageObserverJob: Job? = null

    private val _messagesState = MutableStateFlow<UiState<List<EncryptedMessage>>>(UiState.Loading)
    val messagesState: StateFlow<UiState<List<EncryptedMessage>>> = _messagesState.asStateFlow()

    val localPrefs: StateFlow<LocalAppPreferences> = preferencesManager.preferencesFlow
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), LocalAppPreferences())

    val userProfile: StateFlow<UserProfile?> = repository.observeUserProfile()
        .catch { emit(null) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), null)

    val chatsState: StateFlow<UiState<List<ChatThread>>> = repository.observeChats()
        .map<List<ChatThread>, UiState<List<ChatThread>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(extractErrorMessage(it, "Failed to load chats"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val contactsState: StateFlow<List<ContactRecord>> = repository.observeContacts()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val callsState: StateFlow<UiState<List<EncryptedCall>>> = repository.observeCalls()
        .map<List<EncryptedCall>, UiState<List<EncryptedCall>>> { UiState.Success(it) }
        .catch { emit(UiState.Error(extractErrorMessage(it, "Failed to load call history"))) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), UiState.Loading)

    val devicesState: StateFlow<List<LinkedDevice>> = repository.observeLinkedDevices()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val backupsState: StateFlow<List<CloudBackup>> = repository.observeBackups()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val statusesState: StateFlow<List<StatusUpdate>> = repository.observeStatuses()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    val channelsState: StateFlow<List<ChannelFeed>> = repository.observeChannels()
        .catch { emit(emptyList()) }
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000L), emptyList())

    init {
        viewModelScope.launch {
            repository.ensureUserInitialized(initialDisplayName, initialEmail)
                .onFailure { e ->
                    Log.e(TAG, "Bootstrap failed", e)
                    _statusBanner.value = e.localizedMessage ?: "Account sync warning"
                }
        }
    }

    fun clearBanner() {
        _statusBanner.value = null
    }

    fun selectChat(chatId: String) {
        _selectedChatId.value = chatId
        _aiState.value = AiUiState.Idle
        messageObserverJob?.cancel()
        _messagesState.value = UiState.Loading
        messageObserverJob = viewModelScope.launch {
            repository.observeMessages(chatId)
                .map<List<EncryptedMessage>, UiState<List<EncryptedMessage>>> { UiState.Success(it) }
                .catch { emit(UiState.Error(extractErrorMessage(it, "Failed to load encrypted messages"))) }
                .collect { state -> _messagesState.value = state }
        }
    }

    fun createNewChat(
        title: String,
        chatType: String,
        peerUid: String? = null,
        extraMemberUids: List<String> = emptyList(),
        description: String = "",
        onCreated: (String) -> Unit = {}
    ) {
        viewModelScope.launch {
            repository.createChat(title, chatType, peerUid, extraMemberUids, description)
                .onSuccess { chatId ->
                    onCreated(chatId)
                }
                .onFailure { e ->
                    _statusBanner.value = e.localizedMessage ?: "Could not create chat"
                }
        }
    }

    fun addContactByUid(contactUid: String, fallbackName: String) {
        viewModelScope.launch {
            val lookup = repository.lookupPublicUser(contactUid)
            val entry = lookup.getOrNull()
            val name = entry?.displayName ?: fallbackName.ifBlank { "PulseTalk Contact" }
            val about = entry?.about ?: "Verified via Invite ID"
            repository.addContact(contactUid, name, about)
                .onSuccess {
                    _statusBanner.value = "Added $name to contacts with 60-digit safety number"
                }
                .onFailure { e ->
                    _statusBanner.value = e.localizedMessage ?: "Failed to add contact"
                }
        }
    }

    fun toggleContactFavorite(contact: ContactRecord) {
        viewModelScope.launch {
            repository.updateContactFlags(contact.id, mapOf("favorite" to !contact.favorite))
        }
    }

    fun toggleContactBlocked(contact: ContactRecord) {
        viewModelScope.launch {
            val next = !contact.blocked
            repository.updateContactFlags(contact.id, mapOf("blocked" to next))
                .onSuccess {
                    _statusBanner.value = if (next) "Blocked ${contact.displayName}" else "Unblocked ${contact.displayName}"
                }
        }
    }

    fun deleteContact(contactId: String) {
        viewModelScope.launch {
            repository.deleteContact(contactId)
                .onSuccess { _statusBanner.value = "Contact removed" }
        }
    }

    fun sendMessage(
        chat: ChatThread,
        plaintext: String,
        messageType: String = "text",
        replyToPreview: String = "",
        mediaUri: String = "",
        voiceDurationSec: Long = 0L,
        forwarded: Boolean = false,
        viewOnce: Boolean = false
    ) {
        if (plaintext.isBlank() && mediaUri.isBlank() && voiceDurationSec <= 0L) return
        val senderName = userProfile.value?.displayName ?: "Me"
        viewModelScope.launch {
            repository.sendEncryptedMessage(
                chat = chat,
                plaintext = plaintext.ifBlank { "Attachment" },
                senderName = senderName,
                messageType = messageType,
                replyToPreview = replyToPreview,
                mediaUri = mediaUri,
                voiceDurationSec = voiceDurationSec,
                forwarded = forwarded,
                viewOnce = viewOnce
            ).onSuccess {
                preferencesManager.saveChatDraft(chat.id, "")
            }.onFailure { e ->
                _statusBanner.value = e.localizedMessage ?: "Failed to send encrypted message"
            }
        }
    }

    fun forwardEncryptedMessage(
        targetChat: ChatThread,
        plaintext: String,
        messageType: String,
        mediaUri: String
    ) {
        val senderName = userProfile.value?.displayName ?: "Me"
        viewModelScope.launch {
            repository.sendEncryptedMessage(
                chat = targetChat,
                plaintext = plaintext,
                senderName = senderName,
                messageType = messageType,
                mediaUri = mediaUri,
                forwarded = true
            ).onSuccess {
                _statusBanner.value = "Forwarded to ${targetChat.title}"
            }
        }
    }

    fun sendBroadcastMessage(targetChats: List<ChatThread>, plaintext: String) {
        if (plaintext.isBlank() || targetChats.isEmpty()) return
        val senderName = userProfile.value?.displayName ?: "Me"
        viewModelScope.launch {
            targetChats.forEach { target ->
                repository.sendEncryptedMessage(
                    chat = target,
                    plaintext = plaintext,
                    senderName = senderName,
                    messageType = "text"
                )
            }
            _statusBanner.value = "Broadcast encrypted & sent to ${targetChats.size} chats"
        }
    }

    fun togglePinChat(chat: ChatThread) {
        viewModelScope.launch {
            repository.updateChatMetadata(chat.id, mapOf("pinned" to !chat.pinned))
        }
    }

    fun toggleArchiveChat(chat: ChatThread) {
        viewModelScope.launch {
            repository.updateChatMetadata(chat.id, mapOf("archived" to !chat.archived))
        }
    }

    fun toggleMuteChat(chat: ChatThread) {
        viewModelScope.launch {
            repository.updateChatMetadata(chat.id, mapOf("muted" to !chat.muted))
        }
    }

    fun toggleLockChat(chat: ChatThread) {
        viewModelScope.launch {
            val next = !chat.locked
            repository.updateChatMetadata(chat.id, mapOf("locked" to next))
                .onSuccess {
                    _statusBanner.value = if (next) "Chat locked behind PIN" else "Chat unlocked"
                }
        }
    }

    fun toggleMarkUnread(chat: ChatThread) {
        viewModelScope.launch {
            val nextUnread = if (chat.unreadCount > 0L) 0L else 1L
            repository.updateChatMetadata(chat.id, mapOf("unreadCount" to nextUnread))
        }
    }

    fun clearUnread(chat: ChatThread) {
        if (chat.unreadCount > 0L) {
            viewModelScope.launch {
                repository.updateChatMetadata(chat.id, mapOf("unreadCount" to 0L))
            }
        }
    }

    fun pinMessageInChat(chat: ChatThread, preview: String) {
        viewModelScope.launch {
            repository.updateChatMetadata(chat.id, mapOf("pinnedMessagePreview" to preview.take(180)))
                .onSuccess {
                    _statusBanner.value = if (preview.isBlank()) "Unpinned banner message" else "Message pinned to thread header"
                }
        }
    }

    fun updateChatInfo(chat: ChatThread, newTitle: String, newDesc: String) {
        viewModelScope.launch {
            repository.updateChatMetadata(
                chat.id,
                mapOf(
                    "title" to newTitle.trim().ifBlank { chat.title }.take(80),
                    "description" to newDesc.trim().take(240)
                )
            ).onSuccess { _statusBanner.value = "Thread info updated" }
        }
    }

    fun deleteChatThread(chatId: String) {
        viewModelScope.launch {
            repository.deleteChat(chatId)
                .onSuccess { _statusBanner.value = "Encrypted chat deleted" }
        }
    }

    fun setDisappearingHours(chat: ChatThread, hours: Long) {
        viewModelScope.launch {
            repository.updateChatMetadata(chat.id, mapOf("disappearingHours" to hours))
                .onSuccess {
                    _statusBanner.value = if (hours == 0L) "Disappearing messages turned off" else "Disappearing timer set to ${hours}h"
                }
        }
    }

    fun verifyChatEncryption(chat: ChatThread) {
        viewModelScope.launch {
            repository.updateChatMetadata(chat.id, mapOf("e2eeVerified" to !chat.e2eeVerified))
        }
    }

    fun reactOrStarMessage(chatId: String, msg: EncryptedMessage, reaction: String, starred: Boolean) {
        viewModelScope.launch {
            repository.reactOrStarMessage(chatId, msg.id, reaction, starred)
        }
    }

    fun revealViewOnce(chatId: String, messageId: String) {
        viewModelScope.launch {
            repository.revealViewOnceMessage(chatId, messageId)
        }
    }

    fun voteOnPoll(chatId: String, msg: EncryptedMessage, optionIndex: Int) {
        viewModelScope.launch {
            repository.voteOnPoll(chatId, msg.id, optionIndex, msg.pollVotes)
        }
    }

    fun editMessage(chat: ChatThread, messageId: String, newText: String) {
        viewModelScope.launch {
            repository.editOrDeleteMessage(chat, messageId, newText, deleteForAll = false)
        }
    }

    fun deleteMessageForAll(chat: ChatThread, messageId: String) {
        viewModelScope.launch {
            repository.editOrDeleteMessage(chat, messageId, null, deleteForAll = true)
        }
    }

    // --- AI Chat Suggestions ---
    fun requestSmartReplies(chat: ChatThread, messages: List<EncryptedMessage>) {
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            val decrypted = messages.takeLast(8).map { m ->
                val text = E2eeCryptoManager.decryptText(m.ciphertext, m.iv, chat.encryptionKeyId, chat.id)
                "${m.senderName}: $text"
            }
            aiRepository.generateSmartReplies(chat.title, decrypted, localPrefs.value.aiReplyTone)
                .onSuccess { list -> _aiState.value = AiUiState.SmartRepliesReady(list) }
                .onFailure { e -> _aiState.value = AiUiState.Error(e.localizedMessage ?: "AI suggestions failed.") }
        }
    }

    fun rewriteDraftWithAi(draft: String, style: String) {
        if (draft.isBlank()) {
            _aiState.value = AiUiState.Error("Type a message draft first to polish or rewrite.")
            return
        }
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            var latest = ""
            try {
                aiRepository.rewriteDraftStream(draft, style).collect { partial ->
                    latest = partial
                    _aiState.value = AiUiState.RewriteStreaming(partial)
                }
                if (latest.isNotBlank()) {
                    _aiState.value = AiUiState.RewriteReady(latest.trim())
                } else {
                    _aiState.value = AiUiState.Error("AI returned an empty rewrite.")
                }
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.e(TAG, "Rewrite streaming failed", e)
                _aiState.value = AiUiState.Error(e.localizedMessage ?: "AI rewrite failed.")
            }
        }
    }

    fun summarizeChatThread(chat: ChatThread, messages: List<EncryptedMessage>) {
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            val decrypted = messages.takeLast(25).map { m ->
                val text = E2eeCryptoManager.decryptText(m.ciphertext, m.iv, chat.encryptionKeyId, chat.id)
                "${m.senderName}: $text"
            }
            aiRepository.summarizeThread(chat.title, decrypted)
                .onSuccess { summary -> _aiState.value = AiUiState.SummaryReady(summary) }
                .onFailure { e -> _aiState.value = AiUiState.Error(e.localizedMessage ?: "Summary failed.") }
        }
    }

    fun translateMessageWithAi(text: String, targetLanguage: String) {
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            aiRepository.translateMessage(text, targetLanguage)
                .onSuccess { translated -> _aiState.value = AiUiState.TranslationReady(translated, targetLanguage) }
                .onFailure { e -> _aiState.value = AiUiState.Error(e.localizedMessage ?: "Translation failed.") }
        }
    }

    fun composeDraftFromTopic(topic: String) {
        if (topic.isBlank()) return
        viewModelScope.launch {
            _aiState.value = AiUiState.Loading
            aiRepository.composeDraftFromTopic(topic, localPrefs.value.aiReplyTone)
                .onSuccess { draft -> _aiState.value = AiUiState.RewriteReady(draft) }
                .onFailure { e -> _aiState.value = AiUiState.Error(e.localizedMessage ?: "Compose failed.") }
        }
    }

    fun resetAiState() {
        _aiState.value = AiUiState.Idle
    }

    // --- E2EE Voice Calling ---
    fun startVoiceCall(calleeId: String, calleeName: String, hasMicPermission: Boolean, isEchoTest: Boolean = false) {
        val callerName = userProfile.value?.displayName ?: "Me"
        viewModelScope.launch {
            repository.startEncryptedCall(callerName, calleeId, calleeName, isEchoTest)
                .onSuccess { call ->
                    _activeCall.value = call
                    _callElapsedSec.value = 0L
                    audioCallEngine.startAudioCapture(viewModelScope, hasMicPermission)
                    delay(1400L)
                    if (_activeCall.value?.id == call.id) {
                        repository.activateEncryptedCall(call.id).onSuccess {
                            _activeCall.value = _activeCall.value?.copy(status = "active")
                            startCallTimer()
                        }
                    }
                }
                .onFailure { e ->
                    _statusBanner.value = e.localizedMessage ?: "Unable to start encrypted call"
                }
        }
    }

    fun refreshMicCaptureForActiveCall(hasMicPermission: Boolean) {
        if (_activeCall.value != null) {
            audioCallEngine.startAudioCapture(viewModelScope, hasMicPermission)
        }
    }

    private fun startCallTimer() {
        callTimerJob?.cancel()
        callTimerJob = viewModelScope.launch {
            while (isActive) {
                delay(1000L)
                _callElapsedSec.value += 1L
            }
        }
    }

    fun endActiveCall() {
        val call = _activeCall.value ?: return
        val elapsed = _callElapsedSec.value
        callTimerJob?.cancel()
        callTimerJob = null
        audioCallEngine.stopAudioCapture()
        _activeCall.value = null
        _callElapsedSec.value = 0L
        viewModelScope.launch {
            repository.endEncryptedCall(call.id, elapsed)
        }
    }

    fun createCallInviteLink(onCreated: (EncryptedCall) -> Unit) {
        val callerName = userProfile.value?.displayName ?: "Me"
        viewModelScope.launch {
            repository.createCallInviteLink(callerName)
                .onSuccess { call ->
                    onCreated(call)
                    _statusBanner.value = "Created E2EE call link: ${call.callLinkCode}"
                }
                .onFailure { e -> _statusBanner.value = e.localizedMessage ?: "Call link failed" }
        }
    }

    fun deleteCallRecord(callId: String) {
        viewModelScope.launch {
            repository.deleteCallRecord(callId)
        }
    }

    // --- Multi-Device Sync & Cloud Backup ---
    fun linkCompanionDevice(deviceName: String, platform: String) {
        viewModelScope.launch {
            repository.linkCompanionDevice(deviceName, platform)
                .onSuccess { _statusBanner.value = "Linked $deviceName via E2EE device keypair" }
                .onFailure { e -> _statusBanner.value = e.localizedMessage ?: "Device link failed" }
        }
    }

    fun syncHandoffAcrossDevices(deviceId: String, note: String) {
        viewModelScope.launch {
            repository.syncDeviceHeartbeat(deviceId, note)
                .onSuccess { _statusBanner.value = "Encrypted handoff synced across devices" }
                .onFailure { e -> _statusBanner.value = e.localizedMessage ?: "Sync failed" }
        }
    }

    fun revokeLinkedDevice(deviceId: String) {
        viewModelScope.launch {
            repository.revokeDevice(deviceId)
                .onSuccess { _statusBanner.value = "Device session revoked" }
        }
    }

    fun createCloudBackupNow(label: String) {
        val chats = (chatsState.value as? UiState.Success)?.data ?: emptyList()
        val msgEstimate = (chats.size * 6L).coerceAtLeast(1L)
        viewModelScope.launch {
            repository.createCloudBackup(label, chats, msgEstimate)
                .onSuccess { _statusBanner.value = "AES-256-GCM cloud backup saved & checksum verified" }
                .onFailure { e -> _statusBanner.value = e.localizedMessage ?: "Backup failed" }
        }
    }

    fun deleteCloudBackup(backupId: String) {
        viewModelScope.launch {
            repository.deleteBackup(backupId)
        }
    }

    fun auditCloudBackup(backup: CloudBackup): com.example.data.model.BackupRestoreAudit {
        return repository.auditCloudBackup(backup)
    }

    // --- Statuses & Channels ---
    fun postStatusUpdate(caption: String, bgStyle: String, mediaUri: String = "") {
        val author = userProfile.value?.displayName ?: "Me"
        viewModelScope.launch {
            repository.postStatus(author, caption, bgStyle, mediaUri)
                .onSuccess { _statusBanner.value = "Encrypted status posted" }
        }
    }

    fun reactToStatus(status: StatusUpdate, reaction: String) {
        viewModelScope.launch {
            repository.updateStatusViewOrReaction(status.id, status.viewCount + 1L, reaction)
        }
    }

    fun deleteStatusUpdate(statusId: String) {
        viewModelScope.launch {
            repository.deleteStatus(statusId)
                .onSuccess { _statusBanner.value = "Status deleted" }
        }
    }

    fun createChannel(name: String, category: String, updateText: String) {
        viewModelScope.launch {
            repository.createChannel(name, category, updateText)
                .onSuccess { _statusBanner.value = "Channel '$name' published" }
        }
    }

    fun postChannelUpdate(channelId: String, updateText: String) {
        viewModelScope.launch {
            repository.updateChannel(channelId, mapOf("latestUpdate" to updateText.trim().take(500)))
                .onSuccess { _statusBanner.value = "Channel broadcast updated" }
        }
    }

    fun toggleChannelMute(channel: ChannelFeed) {
        viewModelScope.launch {
            repository.updateChannel(channel.id, mapOf("muted" to !channel.muted))
        }
    }

    fun deleteChannel(channelId: String) {
        viewModelScope.launch {
            repository.deleteChannel(channelId)
        }
    }

    // --- Profile & Settings ---
    fun updateProfileField(updates: Map<String, Any>) {
        viewModelScope.launch {
            repository.updateProfileSettings(updates)
        }
    }

    fun updateDarkMode(mode: String) {
        viewModelScope.launch { preferencesManager.updateDarkMode(mode) }
    }

    fun setScreenshotGuard(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setScreenshotGuard(enabled) }
    }

    fun setCompactChatList(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setCompactChatList(enabled) }
    }

    fun setEnterToSend(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setEnterToSend(enabled) }
    }

    fun setAutoBackupFreq(freq: String) {
        viewModelScope.launch { preferencesManager.setAutoBackupFrequency(freq) }
    }

    fun setAiReplyTone(tone: String) {
        viewModelScope.launch { preferencesManager.setAiReplyTone(tone) }
    }

    fun setWallpaperEnabled(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setWallpaperEnabled(enabled) }
    }

    fun setWallpaperPreset(preset: String) {
        viewModelScope.launch { preferencesManager.setWallpaperPreset(preset) }
    }

    fun setFontSizeScale(scale: String) {
        viewModelScope.launch { preferencesManager.setFontSizeScale(scale) }
    }

    fun setHapticFeedback(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setHapticFeedback(enabled) }
    }

    fun setHighContrastCipherBadges(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setHighContrastBadges(enabled) }
    }

    fun setCallNoiseSuppression(enabled: Boolean) {
        viewModelScope.launch { preferencesManager.setCallNoiseSuppression(enabled) }
    }

    fun setChatLockPin(pin: String) {
        viewModelScope.launch {
            preferencesManager.setChatLockPin(pin)
            _statusBanner.value = if (pin.isBlank()) "Chat Lock PIN cleared" else "Chat Lock PIN updated"
        }
    }

    fun verifyChatLockPin(inputPin: String): Boolean {
        val saved = localPrefs.value.chatLockPinHash
        if (saved.isBlank()) return true
        return E2eeCryptoManager.sha256Hex("PulseLock:$inputPin") == saved
    }

    fun saveChatDraft(chatId: String, draft: String) {
        viewModelScope.launch {
            preferencesManager.saveChatDraft(chatId, draft)
        }
    }

    fun addQuickReplyTemplate(template: String) {
        viewModelScope.launch {
            preferencesManager.addQuickReplyTemplate(template)
            _statusBanner.value = "Quick reply template saved"
        }
    }

    private fun extractErrorMessage(error: Throwable, fallback: String): String {
        val fs = generateSequence(error) { it.cause }
            .filterIsInstance<FirebaseFirestoreException>()
            .firstOrNull()
        if (fs == null && error is CancellationException) throw error
        return (fs ?: error).localizedMessage ?: fallback
    }

    override fun onCleared() {
        super.onCleared()
        audioCallEngine.stopAudioCapture()
    }

    private companion object {
        const val TAG = "MainViewModel"
    }
}
