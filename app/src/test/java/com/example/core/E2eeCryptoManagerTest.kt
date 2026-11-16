package com.example.core

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [36])
class E2eeCryptoManagerTest {

    @Test
    fun encryptAndDecrypt_roundTripsPlaintextWithRandomIv() {
        val plaintext = "PulseTalk E2EE message with Unicode 🔐🚀"
        val p1 = E2eeCryptoManager.encryptText(plaintext, "key_fp_1", "chat_abc")
        val p2 = E2eeCryptoManager.encryptText(plaintext, "key_fp_1", "chat_abc")

        assertNotEquals(p1.ivBase64, p2.ivBase64)
        assertNotEquals(plaintext, p1.ciphertextBase64)

        val d1 = E2eeCryptoManager.decryptText(p1.ciphertextBase64, p1.ivBase64, "key_fp_1", "chat_abc")
        val d2 = E2eeCryptoManager.decryptText(p2.ciphertextBase64, p2.ivBase64, "key_fp_1", "chat_abc")
        assertEquals(plaintext, d1)
        assertEquals(plaintext, d2)
    }

    @Test
    fun safetyNumber_isSymmetricAndSixtyDigits() {
        val s1 = E2eeCryptoManager.computeSafetyNumber("alice_uid", "bob_uid")
        val s2 = E2eeCryptoManager.computeSafetyNumber("bob_uid", "alice_uid")
        assertEquals(s1, s2)
        val digitsOnly = s1.replace(" ", "")
        assertEquals(60, digitsOnly.length)
        assertTrue(digitsOnly.all { it.isDigit() })
    }
}
