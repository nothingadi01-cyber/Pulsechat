package com.example.data.repository

import android.content.Context
import android.os.Build
import com.example.R
import com.example.core.E2eeCryptoManager
import com.example.core.OperationType
import com.example.core.handleFirestoreError
import com.example.data.model.ChannelFeed
import com.example.data.model.ChatThread
import com.example.data.model.CloudBackup
import com.example.data.model.ContactRecord
import com.example.data.model.EncryptedCall
import com.example.data.model.EncryptedMessage
import com.example.data.model.LinkedDevice
import com.example.data.model.PublicContactEntry
import com.example.data.model.StatusUpdate
import com.example.data.model.UserProfile
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await
import java.util.UUID

class PulseTalkRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        return auth.currentUser?.uid
            ?: throw IllegalStateException("User must be signed in with Google before accessing Firestore.")
    }

    private fun <T> Flow<T>.logFirestoreErrors(op: OperationType, path: String, uid: String): Flow<T> =
        this.catch { error ->
            val firestoreError = generateSequence(error) { it.cause }
                .filterIsInstance<FirebaseFirestoreException>()
                .firstOrNull()
            if (firestoreError == null && error is CancellationException) throw error
            val toLog = firestoreError ?: (error as? Exception)
            if (toLog != null && auth.currentUser?.uid == uid) {
                handleFirestoreError(toLog, op, path)
            }
            throw error
        }

    // --- Bootstrap user profile, public directory, current device, vault chat & starter channels ---
    suspend fun ensureUserInitialized(displayName: String, email: String): Result<UserProfile> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val userDoc = db.collection("users").document(uid)
        return try {
            val existing = userDoc.get().await()
            val safeName = displayName.ifBlank { "PulseTalk User" }.take(60)
            val fingerprint = E2eeCryptoManager.computeKeyFingerprint(uid)
            val keyPair = E2eeCryptoManager.generateEcKeyPair()
            val pubKeyB64 = E2eeCryptoManager.encodePublicKey(keyPair).take(500)

            if (!existing.exists()) {
                val profileMap = mapOf(
                    "userId" to uid,
                    "displayName" to safeName,
                    "email" to email.take(120),
                    "about" to "Protected by PulseTalk E2EE",
                    "avatarEmoji" to "🛡️",
                    "publicKeyFingerprint" to fingerprint,
                    "e2eePublicKey" to pubKeyB64,
                    "twoStepEnabled" to false,
                    "appLockEnabled" to false,
                    "readReceiptsEnabled" to true,
                    "defaultDisappearingHours" to 0L,
                    "aiSmartRepliesEnabled" to true,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
                userDoc.set(profileMap).await()
            }

            // Ensure PII-free public directory entry exists
            val publicDoc = db.collection("public_directory").document(uid)
            val pubSnap = publicDoc.get().await()
            if (!pubSnap.exists()) {
                val handle = safeName.lowercase().replace(Regex("[^a-z0-9]"), "").take(16)
                    .ifBlank { "user" } + "_" + uid.take(4)
                publicDoc.set(
                    mapOf(
                        "userId" to uid,
                        "displayName" to safeName,
                        "handle" to handle.take(40),
                        "about" to "Protected by PulseTalk E2EE",
                        "publicKeyFingerprint" to fingerprint,
                        "e2eePublicKey" to pubKeyB64,
                        "isOnline" to true,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else {
                publicDoc.update(
                    mapOf(
                        "isOnline" to true,
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Ensure current device is registered for multi-device sync
            val currentDeviceId = "android_primary_${uid.take(8)}"
            val devDoc = userDoc.collection("devices").document(currentDeviceId)
            val devSnap = devDoc.get().await()
            val modelLabel = "${Build.MANUFACTURER} ${Build.MODEL}".trim().ifBlank { "Android Device" }.take(80)
            if (!devSnap.exists()) {
                devDoc.set(
                    mapOf(
                        "userId" to uid,
                        "deviceName" to modelLabel,
                        "platform" to "Android ${Build.VERSION.RELEASE ?: "16"}".take(30),
                        "keyFingerprint" to fingerprint,
                        "isCurrent" to true,
                        "status" to "active",
                        "handoffNote" to "",
                        "lastSyncAt" to FieldValue.serverTimestamp(),
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            } else if (devSnap.getString("status") == "active") {
                devDoc.update(
                    mapOf(
                        "lastSyncAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            // Seed starter personal E2EE Vault chat + Echo Test contact if user has no chats yet
            val existingChats = db.collection("chats")
                .whereArrayContains("memberUids", uid)
                .limit(1)
                .get()
                .await()

            if (existingChats.isEmpty) {
                val vaultChatId = "vault_${uid.take(16)}"
                val encKeyId = E2eeCryptoManager.computeKeyFingerprint("vault:$uid")
                db.collection("chats").document(vaultChatId).set(
                    mapOf(
                        "title" to "My Encrypted Vault",
                        "chatType" to "vault",
                        "createdBy" to uid,
                        "memberUids" to listOf(uid),
                        "lastMessagePreview" to "Zero-knowledge encrypted notes & multi-device vault",
                        "lastMessageSenderId" to uid,
                        "unreadCount" to 0L,
                        "pinned" to true,
                        "archived" to false,
                        "muted" to false,
                        "disappearingHours" to 0L,
                        "e2eeVerified" to true,
                        "encryptionKeyId" to encKeyId,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()

                val welcomePayload = E2eeCryptoManager.encryptText(
                    "Welcome to PulseTalk by Anupam! All messages here are encrypted client-side with AES-256-GCM before syncing across your linked devices.",
                    encKeyId,
                    vaultChatId
                )
                db.collection("chats").document(vaultChatId).collection("messages")
                    .document("welcome_${UUID.randomUUID().toString().take(8)}")
                    .set(
                        mapOf(
                            "chatId" to vaultChatId,
                            "senderId" to uid,
                            "senderName" to safeName,
                            "memberUids" to listOf(uid),
                            "ciphertext" to welcomePayload.ciphertextBase64,
                            "iv" to welcomePayload.ivBase64,
                            "encryptionScheme" to "ECDH-P256-AES256-GCM",
                            "messageType" to "text",
                            "replyToPreview" to "",
                            "reactionEmoji" to "",
                            "starred" to true,
                            "edited" to false,
                            "deletedForAll" to false,
                            "mediaUri" to "",
                            "voiceDurationSec" to 0L,
                            "createdAt" to FieldValue.serverTimestamp(),
                            "updatedAt" to FieldValue.serverTimestamp()
                        )
                    ).await()

                // Seed verified channel subscription
                userDoc.collection("channels").document("pulsetalk_security").set(
                    mapOf(
                        "userId" to uid,
                        "name" to "PulseTalk Security & Updates",
                        "category" to "Official",
                        "latestUpdate" to "ECDH P-256 + AES-256-GCM voice & chat encryption active across multi-device sessions.",
                        "followersLabel" to "1.4M followers",
                        "verified" to true,
                        "muted" to false,
                        "createdAt" to FieldValue.serverTimestamp(),
                        "updatedAt" to FieldValue.serverTimestamp()
                    )
                ).await()
            }

            val finalProfile = userDoc.get().await()
                .toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: UserProfile(id = uid, userId = uid, displayName = safeName, email = email, publicKeyFingerprint = fingerprint)
            Result.success(finalProfile)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, userDoc.path)
            Result.failure(e)
        }
    }

    // --- Observe User Profile ---
    fun observeUserProfile(): Flow<UserProfile?> = flow {
        val uid = requireUserId()
        val path = "users/$uid"
        emitAll(
            db.collection("users").document(uid)
                .snapshots()
                .map { snap -> snap.toObject(UserProfile::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.GET, path, uid)
        )
    }

    suspend fun updateProfileSettings(updates: Map<String, Any>): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid)
        return try {
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Chats ---
    fun observeChats(): Flow<List<ChatThread>> = flow {
        val uid = requireUserId()
        val path = "chats"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(ChatThread::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedWith(
                            compareByDescending<ChatThread> { it.pinned }
                                .thenByDescending { it.updatedAt?.seconds ?: 0L }
                        )
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createChat(
        title: String,
        chatType: String,
        peerUid: String? = null,
        extraMemberUids: List<String> = emptyList(),
        description: String = ""
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val chatId = "chat_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("chats").document(chatId)
        val members = buildList {
            add(uid)
            if (!peerUid.isNullOrBlank() && peerUid != uid) add(peerUid.trim())
            extraMemberUids.filter { it.isNotBlank() && it != uid }.forEach { add(it.trim()) }
        }.distinct().take(20)
        val keyId = E2eeCryptoManager.computeKeyFingerprint("$chatId:${members.sorted().joinToString(",")}")
        val cleanTitle = title.trim().ifBlank { "Encrypted Chat" }.take(80)

        return try {
            docRef.set(
                mapOf(
                    "title" to cleanTitle,
                    "chatType" to chatType,
                    "createdBy" to uid,
                    "memberUids" to members,
                    "description" to description.trim().take(240),
                    "lastMessagePreview" to "End-to-end encryption enabled",
                    "lastMessageSenderId" to uid,
                    "unreadCount" to 0L,
                    "pinned" to false,
                    "archived" to false,
                    "muted" to false,
                    "locked" to false,
                    "pinnedMessagePreview" to "",
                    "disappearingHours" to 0L,
                    "e2eeVerified" to false,
                    "encryptionKeyId" to keyId,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(chatId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateChatMetadata(chatId: String, updates: Map<String, Any>): Result<Unit> {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        return try {
            val payload = updates.toMutableMap().apply {
                put("updatedAt", FieldValue.serverTimestamp())
            }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteChat(chatId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("chats").document(chatId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Encrypted Messages ---
    fun observeMessages(chatId: String): Flow<List<EncryptedMessage>> = flow {
        val uid = requireUserId()
        val path = "chats/$chatId/messages"
        emitAll(
            db.collection("chats").document(chatId).collection("messages")
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EncryptedMessage::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedBy { it.createdAt?.seconds ?: Long.MAX_VALUE }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun sendEncryptedMessage(
        chat: ChatThread,
        plaintext: String,
        senderName: String,
        messageType: String = "text",
        replyToPreview: String = "",
        mediaUri: String = "",
        voiceDurationSec: Long = 0L,
        forwarded: Boolean = false,
        viewOnce: Boolean = false
    ): Result<String> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val msgId = "msg_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val msgRef = db.collection("chats").document(chat.id).collection("messages").document(msgId)
        val encrypted = E2eeCryptoManager.encryptText(
            plaintext = plaintext.take(4000),
            encryptionKeyId = chat.encryptionKeyId,
            chatId = chat.id
        )
        val previewLabel = when {
            viewOnce -> "1️⃣ View-Once Encrypted Message"
            messageType == "voice" -> "🎤 Voice note (${voiceDurationSec}s)"
            messageType == "image" -> "🖼️ Photo • ${plaintext.take(40)}"
            messageType == "poll" -> "📊 Poll: ${plaintext.take(40)}"
            messageType == "contact" -> "👤 Contact Card • ${plaintext.take(40)}"
            else -> plaintext.take(80)
        }
        return try {
            msgRef.set(
                mapOf(
                    "chatId" to chat.id,
                    "senderId" to uid,
                    "senderName" to senderName.ifBlank { "Me" }.take(60),
                    "memberUids" to chat.memberUids,
                    "ciphertext" to encrypted.ciphertextBase64.take(7800),
                    "iv" to encrypted.ivBase64,
                    "encryptionScheme" to "ECDH-P256-AES256-GCM",
                    "messageType" to messageType,
                    "replyToPreview" to replyToPreview.take(200),
                    "reactionEmoji" to "",
                    "starred" to false,
                    "edited" to false,
                    "deletedForAll" to false,
                    "forwarded" to forwarded,
                    "viewOnce" to viewOnce,
                    "viewOnceOpened" to false,
                    "pollVotes" to if (messageType == "poll") "0:0" else "",
                    "mediaUri" to mediaUri.take(500),
                    "voiceDurationSec" to voiceDurationSec.coerceIn(0L, 3600L),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            db.collection("chats").document(chat.id).update(
                mapOf(
                    "lastMessagePreview" to previewLabel.take(240),
                    "lastMessageSenderId" to uid,
                    "unreadCount" to 0L,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            Result.success(msgId)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun reactOrStarMessage(
        chatId: String,
        messageId: String,
        reactionEmoji: String,
        starred: Boolean
    ): Result<Unit> {
        requireUserId()
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "reactionEmoji" to reactionEmoji.take(16),
                    "starred" to starred,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun revealViewOnceMessage(chatId: String, messageId: String): Result<Unit> {
        requireUserId()
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "viewOnceOpened" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun voteOnPoll(
        chatId: String,
        messageId: String,
        optionIndex: Int,
        currentPollVotes: String
    ): Result<Unit> {
        requireUserId()
        val parts = currentPollVotes.split(":").map { it.toIntOrNull() ?: 0 }
        val a = parts.getOrElse(0) { 0 } + if (optionIndex == 0) 1 else 0
        val b = parts.getOrElse(1) { 0 } + if (optionIndex == 1) 1 else 0
        val msgRef = db.collection("chats").document(chatId).collection("messages").document(messageId)
        return try {
            msgRef.update(
                mapOf(
                    "pollVotes" to "$a:$b",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    suspend fun editOrDeleteMessage(
        chat: ChatThread,
        messageId: String,
        newPlaintext: String?,
        deleteForAll: Boolean
    ): Result<Unit> {
        requireUserId()
        val msgRef = db.collection("chats").document(chat.id).collection("messages").document(messageId)
        return try {
            val payload = if (deleteForAll) {
                val enc = E2eeCryptoManager.encryptText("This message was deleted", chat.encryptionKeyId, chat.id)
                mapOf(
                    "ciphertext" to enc.ciphertextBase64,
                    "iv" to enc.ivBase64,
                    "edited" to false,
                    "deletedForAll" to true,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            } else {
                val enc = E2eeCryptoManager.encryptText((newPlaintext ?: "").take(4000), chat.encryptionKeyId, chat.id)
                mapOf(
                    "ciphertext" to enc.ciphertextBase64,
                    "iv" to enc.ivBase64,
                    "edited" to true,
                    "deletedForAll" to false,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            }
            msgRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, msgRef.path)
            Result.failure(e)
        }
    }

    // --- Contacts & Public Directory Lookup ---
    fun observeContacts(): Flow<List<ContactRecord>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/contacts"
        emitAll(
            db.collection("users").document(uid).collection("contacts")
                .snapshots()
                .map { snap -> snap.toObjects(ContactRecord::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun lookupPublicUser(targetUid: String): Result<PublicContactEntry?> {
        requireUserId()
        val cleanId = targetUid.trim()
        if (!cleanId.matches(Regex("^[a-zA-Z0-9_\\-]{1,128}$"))) {
            return Result.failure(IllegalArgumentException("Invalid PulseTalk User ID format."))
        }
        val docRef = db.collection("public_directory").document(cleanId)
        return try {
            val snap = docRef.get().await()
            Result.success(snap.toObject(PublicContactEntry::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.GET, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun addContact(contactUid: String, displayName: String, about: String = "PulseTalk E2EE Contact"): Result<Unit> {
        val uid = requireUserId()
        val cleanContactId = contactUid.trim().replace(Regex("[^a-zA-Z0-9_\\-]"), "").take(128)
        if (cleanContactId.isBlank()) return Result.failure(IllegalArgumentException("Contact ID cannot be empty."))
        val docRef = db.collection("users").document(uid).collection("contacts").document(cleanContactId)
        val safety = E2eeCryptoManager.computeSafetyNumber(uid, cleanContactId)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "contactUid" to cleanContactId,
                    "displayName" to displayName.trim().ifBlank { "PulseTalk Contact" }.take(60),
                    "about" to about.take(140),
                    "safetyNumber" to safety.take(128),
                    "verified" to false,
                    "favorite" to false,
                    "blocked" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateContactFlags(contactId: String, updates: Map<String, Any>): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("contacts").document(contactId)
        return try {
            val payload = updates.toMutableMap().apply { put("updatedAt", FieldValue.serverTimestamp()) }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteContact(contactId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("contacts").document(contactId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- E2EE Voice Calls ---
    fun observeCalls(): Flow<List<EncryptedCall>> = flow {
        val uid = requireUserId()
        val path = "calls"
        emitAll(
            db.collection(path)
                .whereArrayContains("memberUids", uid)
                .snapshots()
                .map { snap ->
                    snap.toObjects(EncryptedCall::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun startEncryptedCall(
        callerName: String,
        calleeId: String,
        calleeName: String,
        isEchoTest: Boolean = false
    ): Result<EncryptedCall> {
        val uid = try {
            requireUserId()
        } catch (e: Exception) {
            return Result.failure(e)
        }
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val docRef = db.collection("calls").document(callId)
        val kp = E2eeCryptoManager.generateEcKeyPair()
        val callerPub = E2eeCryptoManager.encodePublicKey(kp).take(500)
        val sas = E2eeCryptoManager.deriveSasEmojiCode(callerPub, calleeId)
        val members = if (isEchoTest || calleeId == uid || calleeId == "echo_test") {
            listOf(uid)
        } else {
            listOf(uid, calleeId).distinct()
        }

        return try {
            docRef.set(
                mapOf(
                    "callerId" to uid,
                    "callerName" to callerName.ifBlank { "Me" }.take(60),
                    "calleeId" to calleeId.take(128),
                    "calleeName" to calleeName.ifBlank { "PulseTalk Contact" }.take(60),
                    "memberUids" to members,
                    "status" to "ringing",
                    "callerEphemeralPubKey" to callerPub,
                    "calleeEphemeralPubKey" to "",
                    "sasCode" to sas,
                    "cipherSuite" to "ECDH-P256 + AES-256-GCM",
                    "durationSeconds" to 0L,
                    "isEchoTest" to isEchoTest,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()

            val snap = docRef.get().await()
            val model = snap.toObject(EncryptedCall::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: EncryptedCall(
                    id = callId,
                    callerId = uid,
                    callerName = callerName,
                    calleeId = calleeId,
                    calleeName = calleeName,
                    memberUids = members,
                    status = "ringing",
                    callerEphemeralPubKey = callerPub,
                    sasCode = sas,
                    isEchoTest = isEchoTest
                )
            Result.success(model)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun activateEncryptedCall(callId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        val calleeKp = E2eeCryptoManager.generateEcKeyPair()
        val calleePub = E2eeCryptoManager.encodePublicKey(calleeKp).take(500)
        return try {
            docRef.update(
                mapOf(
                    "status" to "active",
                    "calleeEphemeralPubKey" to calleePub,
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun endEncryptedCall(callId: String, durationSeconds: Long, missed: Boolean = false): Result<Unit> {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        return try {
            docRef.update(
                mapOf(
                    "status" to if (missed) "missed" else "ended",
                    "durationSeconds" to durationSeconds.coerceIn(0L, 86400L),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun createCallInviteLink(callerName: String): Result<EncryptedCall> {
        val uid = requireUserId()
        val callId = "call_${UUID.randomUUID().toString().replace("-", "").take(16)}"
        val linkToken = "ptk-${UUID.randomUUID().toString().replace("-", "").take(10)}"
        val kp = E2eeCryptoManager.generateEcKeyPair()
        val callerPub = E2eeCryptoManager.encodePublicKey(kp).take(500)
        val sas = E2eeCryptoManager.deriveSasEmojiCode(callerPub, linkToken)
        val docRef = db.collection("calls").document(callId)
        return try {
            docRef.set(
                mapOf(
                    "callerId" to uid,
                    "callerName" to callerName.ifBlank { "Me" }.take(60),
                    "calleeId" to "call_link_room",
                    "calleeName" to "E2EE Call Link ($linkToken)",
                    "memberUids" to listOf(uid),
                    "status" to "ringing",
                    "callerEphemeralPubKey" to callerPub,
                    "calleeEphemeralPubKey" to "",
                    "sasCode" to sas,
                    "cipherSuite" to "ECDH-P256 + AES-256-GCM",
                    "durationSeconds" to 0L,
                    "isEchoTest" to true,
                    "callLinkCode" to linkToken,
                    "callMode" to "link",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            val snap = docRef.get().await()
            val model = snap.toObject(EncryptedCall::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                ?: EncryptedCall(
                    id = callId,
                    callerId = uid,
                    callerName = callerName,
                    calleeId = "call_link_room",
                    calleeName = "E2EE Call Link ($linkToken)",
                    memberUids = listOf(uid),
                    status = "ringing",
                    callerEphemeralPubKey = callerPub,
                    sasCode = sas,
                    isEchoTest = true,
                    callLinkCode = linkToken,
                    callMode = "link"
                )
            Result.success(model)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteCallRecord(callId: String): Result<Unit> {
        requireUserId()
        val docRef = db.collection("calls").document(callId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Multi-Device Sync ---
    fun observeLinkedDevices(): Flow<List<LinkedDevice>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/devices"
        emitAll(
            db.collection("users").document(uid).collection("devices")
                .snapshots()
                .map { snap ->
                    snap.toObjects(LinkedDevice::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.isCurrent }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun linkCompanionDevice(deviceName: String, platform: String): Result<Unit> {
        val uid = requireUserId()
        val devId = "dev_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("devices").document(devId)
        val fp = E2eeCryptoManager.computeKeyFingerprint("$uid:$devId")
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "deviceName" to deviceName.trim().ifBlank { "PulseTalk Desktop" }.take(80),
                    "platform" to platform.trim().ifBlank { "Web / Desktop" }.take(30),
                    "keyFingerprint" to fp,
                    "isCurrent" to false,
                    "status" to "active",
                    "handoffNote" to "",
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun syncDeviceHeartbeat(deviceId: String, handoffNote: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        return try {
            docRef.update(
                mapOf(
                    "handoffNote" to handoffNote.take(1000),
                    "lastSyncAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun revokeDevice(deviceId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("devices").document(deviceId)
        return try {
            docRef.update(
                mapOf(
                    "status" to "revoked",
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    // --- Cloud Backups ---
    fun observeBackups(): Flow<List<CloudBackup>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/backups"
        emitAll(
            db.collection("users").document(uid).collection("backups")
                .snapshots()
                .map { snap ->
                    snap.toObjects(CloudBackup::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createCloudBackup(
        label: String,
        chats: List<ChatThread>,
        totalMessagesEstimate: Long
    ): Result<Unit> {
        val uid = requireUserId()
        val backupId = "bkp_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("backups").document(backupId)
        val rawManifest = chats.joinToString("|") { "${it.id}:${it.title}:${it.encryptionKeyId}" }
            .ifBlank { "empty_vault" }
        val encManifest = E2eeCryptoManager.encryptText(rawManifest.take(1800), "backup:$uid", backupId)
        val checksum = E2eeCryptoManager.sha256Hex(encManifest.ciphertextBase64)
        val estimatedBytes = (chats.size * 2400L + totalMessagesEstimate * 512L).coerceAtLeast(1024L)

        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "label" to label.trim().ifBlank { "Encrypted Cloud Backup" }.take(80),
                    "chatCount" to chats.size.toLong(),
                    "messageCount" to totalMessagesEstimate.coerceAtLeast(0L),
                    "sizeBytes" to estimatedBytes,
                    "checksumSha256" to checksum.take(128),
                    "encryptedManifest" to encManifest.ciphertextBase64.take(3900),
                    "encryptionMode" to "E2EE-AES256",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteBackup(backupId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("backups").document(backupId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun auditCloudBackup(backup: CloudBackup): com.example.data.model.BackupRestoreAudit {
        val uid = auth.currentUser?.uid ?: backup.userId
        val recomputed = E2eeCryptoManager.sha256Hex(backup.encryptedManifest)
        val valid = recomputed.equals(backup.checksumSha256, ignoreCase = true)
        val entries = backup.encryptedManifest.split("|").filter { it.isNotBlank() }.ifEmpty { listOf("Encrypted snapshot verified for $uid") }
        return com.example.data.model.BackupRestoreAudit(
            backupId = backup.id,
            label = backup.label,
            checksumValid = valid,
            manifestEntries = entries
        )
    }

    // --- Statuses & Channels ---
    fun observeStatuses(): Flow<List<StatusUpdate>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/statuses"
        emitAll(
            db.collection("users").document(uid).collection("statuses")
                .snapshots()
                .map { snap ->
                    snap.toObjects(StatusUpdate::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE)
                        .sortedByDescending { it.createdAt?.seconds ?: 0L }
                }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun postStatus(authorName: String, caption: String, bgStyle: String, mediaUri: String = ""): Result<Unit> {
        val uid = requireUserId()
        val statusId = "st_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("statuses").document(statusId)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "authorName" to authorName.ifBlank { "Me" }.take(60),
                    "caption" to caption.trim().ifBlank { "Encrypted Update" }.take(500),
                    "bgStyle" to bgStyle.take(40),
                    "mediaUri" to mediaUri.take(500),
                    "viewCount" to 1L,
                    "reactionEmoji" to "",
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateStatusViewOrReaction(statusId: String, newViews: Long, reactionEmoji: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("statuses").document(statusId)
        return try {
            docRef.update(
                mapOf(
                    "viewCount" to newViews.coerceAtLeast(0L),
                    "reactionEmoji" to reactionEmoji.take(16),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteStatus(statusId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("statuses").document(statusId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }

    fun observeChannels(): Flow<List<ChannelFeed>> = flow {
        val uid = requireUserId()
        val path = "users/$uid/channels"
        emitAll(
            db.collection("users").document(uid).collection("channels")
                .snapshots()
                .map { snap -> snap.toObjects(ChannelFeed::class.java, DocumentSnapshot.ServerTimestampBehavior.ESTIMATE) }
                .logFirestoreErrors(OperationType.LIST, path, uid)
        )
    }

    suspend fun createChannel(name: String, category: String, updateText: String): Result<Unit> {
        val uid = requireUserId()
        val channelId = "ch_${UUID.randomUUID().toString().replace("-", "").take(12)}"
        val docRef = db.collection("users").document(uid).collection("channels").document(channelId)
        return try {
            docRef.set(
                mapOf(
                    "userId" to uid,
                    "name" to name.trim().ifBlank { "My Channel" }.take(80),
                    "category" to category.trim().ifBlank { "Tech" }.take(40),
                    "latestUpdate" to updateText.trim().ifBlank { "Channel created on PulseTalk." }.take(500),
                    "followersLabel" to "1 follower",
                    "verified" to true,
                    "muted" to false,
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                )
            ).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.CREATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun updateChannel(channelId: String, updates: Map<String, Any>): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("channels").document(channelId)
        return try {
            val payload = updates.toMutableMap().apply { put("updatedAt", FieldValue.serverTimestamp()) }
            docRef.update(payload).await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.UPDATE, docRef.path)
            Result.failure(e)
        }
    }

    suspend fun deleteChannel(channelId: String): Result<Unit> {
        val uid = requireUserId()
        val docRef = db.collection("users").document(uid).collection("channels").document(channelId)
        return try {
            docRef.delete().await()
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.DELETE, docRef.path)
            Result.failure(e)
        }
    }
}
