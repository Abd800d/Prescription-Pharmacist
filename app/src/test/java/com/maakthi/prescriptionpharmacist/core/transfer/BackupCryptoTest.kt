package com.maakthi.prescriptionpharmacist.core.transfer

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test

/** Encrypted backup: roundtrip, wrong passphrase and tampering. */
class BackupCryptoTest {

    private val iterations = 1_000 // low value keeps the test fast

    @Test
    fun `encrypt then decrypt roundtrip`() {
        val plain = """{"drugs":[{"name":"بنادول"}],"prescriptions":[]}""".toByteArray()
        val encrypted = BackupCrypto.encrypt("correct horse", plain, iterations)
        assertFalse(String(encrypted).contains("بنادول"))
        val decrypted = BackupCrypto.decrypt("correct horse", encrypted)
        assertNotNull(decrypted)
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun `wrong passphrase fails cleanly`() {
        val encrypted = BackupCrypto.encrypt("right", "secret data".toByteArray(), iterations)
        assertNull(BackupCrypto.decrypt("wrong", encrypted))
    }

    @Test
    fun `tampered ciphertext fails cleanly`() {
        val encrypted = BackupCrypto.encrypt("pw", "secret data".toByteArray(), iterations)
        val tampered = encrypted.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertNull(BackupCrypto.decrypt("pw", tampered))
    }

    @Test
    fun `garbage and truncated files fail cleanly`() {
        assertNull(BackupCrypto.decrypt("pw", "not encrypted at all".toByteArray()))
        val encrypted = BackupCrypto.encrypt("pw", "data".toByteArray(), iterations)
        assertNull(BackupCrypto.decrypt("pw", encrypted.copyOf(10)))
        assertNull(BackupCrypto.decrypt("pw", ByteArray(0)))
    }

    @Test
    fun `each encryption uses a fresh salt and iv`() {
        val a = BackupCrypto.encrypt("pw", "same".toByteArray(), iterations)
        val b = BackupCrypto.encrypt("pw", "same".toByteArray(), iterations)
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `empty passphrase is rejected`() {
        try {
            BackupCrypto.encrypt("", "x".toByteArray(), iterations)
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("passphrase required", e.message)
        }
    }
}
