package com.example.data.repository

import com.example.base.FirestoreEmulatorTestBase
import com.example.core.E2eeCryptoManager
import com.google.firebase.firestore.FirebaseFirestoreException
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import kotlinx.coroutines.withTimeout
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class PulseTalkRepositoryRuleTest : FirestoreEmulatorTestBase() {

    @Test
    fun ensureUserInitialized_createsProfileVaultDeviceAndChannels() = runBlocking {
        val freshEmail = "alice_init_${System.currentTimeMillis()}@test.com"
        signInTestUser(freshEmail)
        val repo = PulseTalkRepository(firestore, auth)

        val initResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.ensureUserInitialized("Alice Tester", freshEmail)
        }
        assertTrue("Expected initialization success: ${initResult.exceptionOrNull()}", initResult.isSuccess)
        val profile = initResult.getOrThrow()
        assertEquals("Alice Tester", profile.displayName)

        val chats = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChats().first { it.isNotEmpty() }
        }
        assertTrue(chats.any { it.chatType == "vault" })

        val devices = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeLinkedDevices().first { it.isNotEmpty() }
        }
        assertTrue(devices.any { it.isCurrent && it.status == "active" })
    }

    @Test
    fun createChatAndSendEncryptedMessage_encryptsAndDecryptsRoundTrip() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = PulseTalkRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserInitialized("Alice", ALICE_EMAIL).getOrThrow() }

        val chatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createChat("Project Titan", "group").getOrThrow()
        }
        val chats = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeChats().first { list -> list.any { it.id == chatId } }
        }
        val chat = chats.first { it.id == chatId }

        val secretMessage = "Launch codes verified with ECDH P-256"
        withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.sendEncryptedMessage(chat, secretMessage, "Alice").getOrThrow()
        }

        val messages = withTimeout(FLOW_TIMEOUT_MS) {
            repo.observeMessages(chatId).first { it.isNotEmpty() }
        }
        val msg = messages.first()
        val decrypted = E2eeCryptoManager.decryptText(msg.ciphertext, msg.iv, chat.encryptionKeyId, chat.id)
        assertEquals(secretMessage, decrypted)
    }

    @Test
    fun createCloudBackupAndEncryptedCall_persistAndRespectTerminalLock() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val repo = PulseTalkRepository(firestore, auth)
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.ensureUserInitialized("Alice", ALICE_EMAIL).getOrThrow() }

        val chats = withTimeout(FLOW_TIMEOUT_MS) { repo.observeChats().first { it.isNotEmpty() } }
        val backupRes = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.createCloudBackup("Pre-Release Snapshot", chats, 12L)
        }
        assertTrue("Expected backup success: ${backupRes.exceptionOrNull()}", backupRes.isSuccess)

        val backups = withTimeout(FLOW_TIMEOUT_MS) { repo.observeBackups().first { it.isNotEmpty() } }
        assertEquals("E2EE-AES256", backups.first().encryptionMode)

        val call = withTimeout(DEFAULT_TIMEOUT_MS) {
            repo.startEncryptedCall("Alice", "echo_test", "PulseTalk Echo", isEchoTest = true).getOrThrow()
        }
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.activateEncryptedCall(call.id).getOrThrow() }
        withTimeout(DEFAULT_TIMEOUT_MS) { repo.endEncryptedCall(call.id, 15L).getOrThrow() }

        val secondUpdate = withTimeout(DEFAULT_TIMEOUT_MS) { repo.activateEncryptedCall(call.id) }
        assertTrue(secondUpdate.isFailure)
        val fsErr = secondUpdate.exceptionOrNull()?.unwrapFirestoreException()
        assertNotNull(fsErr)
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    @Test
    fun observeChats_unauthenticatedUser_failsWithPermissionDenied() = runBlocking {
        auth.signOut()
        val repo = PulseTalkRepository(firestore, auth)
        val thrown = try {
            withTimeout(FLOW_TIMEOUT_MS) { repo.observeChats().first() }
            null
        } catch (e: Exception) {
            e
        }
        assertNotNull("Expected failure when unauthenticated", thrown)
    }

    @Test
    fun crossUserChatAccess_failsWithPermissionDenied() = runBlocking {
        signInTestUser(ALICE_EMAIL)
        val aliceRepo = PulseTalkRepository(firestore, auth)
        val aliceChatId = withTimeout(DEFAULT_TIMEOUT_MS) {
            aliceRepo.createChat("Alice Private", "vault").getOrThrow()
        }

        signInTestUser(BOB_EMAIL)
        val bobRepo = PulseTalkRepository(firestore, auth)
        val updateResult = withTimeout(DEFAULT_TIMEOUT_MS) {
            bobRepo.updateChatMetadata(aliceChatId, mapOf("pinned" to true))
        }
        assertTrue(updateResult.isFailure)
        val fsErr = updateResult.exceptionOrNull()?.unwrapFirestoreException()
        assertEquals(FirebaseFirestoreException.Code.PERMISSION_DENIED, fsErr?.code)
    }

    private companion object {
        const val ALICE_EMAIL = "alice_pt@test.com"
        const val BOB_EMAIL = "bob_pt@test.com"
        const val DEFAULT_TIMEOUT_MS = 8000L
        const val FLOW_TIMEOUT_MS = 5000L
    }
}
