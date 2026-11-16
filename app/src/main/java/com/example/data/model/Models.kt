package com.example.data.model

import com.google.firebase.Timestamp
import com.google.firebase.firestore.DocumentId
import com.google.firebase.firestore.IgnoreExtraProperties
import com.google.firebase.firestore.PropertyName
import com.google.firebase.firestore.ServerTimestamp

sealed interface UiState<out T> {
    data object Loading : UiState<Nothing>
    data class Success<T>(val data: T) : UiState<T>
    data class Error(val message: String) : UiState<Nothing>
}

@IgnoreExtraProperties
data class UserProfile(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val email: String = "",
    val about: String = "Encrypted on PulseTalk",
    val avatarEmoji: String = "🛡️",
    val publicKeyFingerprint: String = "",
    val e2eePublicKey: String = "",
    val twoStepEnabled: Boolean = false,
    val appLockEnabled: Boolean = false,
    val readReceiptsEnabled: Boolean = true,
    val defaultDisappearingHours: Long = 0L,
    val aiSmartRepliesEnabled: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class PublicContactEntry(
    @DocumentId val id: String = "",
    val userId: String = "",
    val displayName: String = "",
    val handle: String = "",
    val about: String = "",
    val publicKeyFingerprint: String = "",
    val e2eePublicKey: String = "",
    @get:PropertyName("isOnline") val isOnline: Boolean = true,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ContactRecord(
    @DocumentId val id: String = "",
    val userId: String = "",
    val contactUid: String = "",
    val displayName: String = "",
    val about: String = "",
    val safetyNumber: String = "",
    val verified: Boolean = false,
    val favorite: Boolean = false,
    val blocked: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class LinkedDevice(
    @DocumentId val id: String = "",
    val userId: String = "",
    val deviceName: String = "",
    val platform: String = "",
    val keyFingerprint: String = "",
    @get:PropertyName("isCurrent") val isCurrent: Boolean = false,
    val status: String = "active",
    val handoffNote: String = "",
    @ServerTimestamp val lastSyncAt: Timestamp? = null,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class CloudBackup(
    @DocumentId val id: String = "",
    val userId: String = "",
    val label: String = "",
    val chatCount: Long = 0L,
    val messageCount: Long = 0L,
    val sizeBytes: Long = 0L,
    val checksumSha256: String = "",
    val encryptedManifest: String = "",
    val encryptionMode: String = "E2EE-AES256",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class StatusUpdate(
    @DocumentId val id: String = "",
    val userId: String = "",
    val authorName: String = "",
    val caption: String = "",
    val bgStyle: String = "emerald",
    val mediaUri: String = "",
    val viewCount: Long = 0L,
    val reactionEmoji: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChannelFeed(
    @DocumentId val id: String = "",
    val userId: String = "",
    val name: String = "",
    val category: String = "",
    val latestUpdate: String = "",
    val followersLabel: String = "",
    val verified: Boolean = true,
    val muted: Boolean = false,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class ChatThread(
    @DocumentId val id: String = "",
    val title: String = "",
    val chatType: String = "direct",
    val createdBy: String = "",
    val memberUids: List<String> = emptyList(),
    val description: String = "",
    val lastMessagePreview: String = "",
    val lastMessageSenderId: String = "",
    val unreadCount: Long = 0L,
    val pinned: Boolean = false,
    val archived: Boolean = false,
    val muted: Boolean = false,
    val locked: Boolean = false,
    val pinnedMessagePreview: String = "",
    val disappearingHours: Long = 0L,
    val e2eeVerified: Boolean = false,
    val encryptionKeyId: String = "",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EncryptedMessage(
    @DocumentId val id: String = "",
    val chatId: String = "",
    val senderId: String = "",
    val senderName: String = "",
    val memberUids: List<String> = emptyList(),
    val ciphertext: String = "",
    val iv: String = "",
    val encryptionScheme: String = "ECDH-P256-AES256-GCM",
    val messageType: String = "text",
    val replyToPreview: String = "",
    val reactionEmoji: String = "",
    val starred: Boolean = false,
    val edited: Boolean = false,
    val deletedForAll: Boolean = false,
    val forwarded: Boolean = false,
    val viewOnce: Boolean = false,
    val viewOnceOpened: Boolean = false,
    val pollVotes: String = "",
    val mediaUri: String = "",
    val voiceDurationSec: Long = 0L,
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

@IgnoreExtraProperties
data class EncryptedCall(
    @DocumentId val id: String = "",
    val callerId: String = "",
    val callerName: String = "",
    val calleeId: String = "",
    val calleeName: String = "",
    val memberUids: List<String> = emptyList(),
    val status: String = "ringing",
    val callerEphemeralPubKey: String = "",
    val calleeEphemeralPubKey: String = "",
    val sasCode: String = "",
    val cipherSuite: String = "ECDH-P256 + AES-256-GCM",
    val durationSeconds: Long = 0L,
    @get:PropertyName("isEchoTest") val isEchoTest: Boolean = false,
    val callLinkCode: String = "",
    val callMode: String = "voice",
    @ServerTimestamp val createdAt: Timestamp? = null,
    @ServerTimestamp val updatedAt: Timestamp? = null
)

data class BackupRestoreAudit(
    val backupId: String,
    val label: String,
    val checksumValid: Boolean,
    val manifestEntries: List<String>
)
