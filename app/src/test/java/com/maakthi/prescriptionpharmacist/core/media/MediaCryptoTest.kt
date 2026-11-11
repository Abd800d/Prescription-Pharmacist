package com.maakthi.prescriptionpharmacist.core.media

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Test

/** At-rest encryption: roundtrip, wrong key, tampering, sensitive strings. */
class MediaCryptoTest {

    private val key = ByteArray(32) { it.toByte() }
    private val otherKey = ByteArray(32) { (it + 1).toByte() }

    @Test
    fun `encrypt then decrypt roundtrip`() {
        val plain = "prescription image bytes مشفّر".toByteArray()
        val encrypted = MediaCrypto.encrypt(key, plain)
        assertFalse(String(encrypted).contains("prescription"))
        val decrypted = MediaCrypto.decrypt(key, encrypted)
        assertArrayEquals(plain, decrypted)
    }

    @Test
    fun `each encryption uses a fresh iv`() {
        val a = MediaCrypto.encrypt(key, "same".toByteArray())
        val b = MediaCrypto.encrypt(key, "same".toByteArray())
        assertFalse(a.contentEquals(b))
    }

    @Test
    fun `wrong key fails cleanly`() {
        val encrypted = MediaCrypto.encrypt(key, "secret".toByteArray())
        assertNull(MediaCrypto.decrypt(otherKey, encrypted))
    }

    @Test
    fun `tampered file fails cleanly`() {
        val encrypted = MediaCrypto.encrypt(key, "secret".toByteArray())
        val tampered = encrypted.copyOf().also { it[it.size - 1] = (it.last() + 1).toByte() }
        assertNull(MediaCrypto.decrypt(key, tampered))
    }

    @Test
    fun `garbage and truncated files fail cleanly`() {
        assertNull(MediaCrypto.decrypt(key, "not encrypted".toByteArray()))
        assertNull(MediaCrypto.decrypt(key, ByteArray(0)))
        val encrypted = MediaCrypto.encrypt(key, "data".toByteArray())
        assertNull(MediaCrypto.decrypt(key, encrypted.copyOf(10)))
    }

    @Test
    fun `key must be 32 bytes`() {
        try {
            MediaCrypto.encrypt(ByteArray(16), "x".toByteArray())
            throw AssertionError("expected IllegalArgumentException")
        } catch (e: IllegalArgumentException) {
            assertEquals("key must be 32 bytes", e.message)
        }
    }

    @Test
    fun `sensitive strings roundtrip and hide the value`() {
        val stored = MediaCrypto.encryptString(key, "أحمد الصيدلي")
        assertFalse(stored.contains("أحمد"))
        assertEquals("أحمد الصيدلي", MediaCrypto.decryptString(key, stored))
        assertNull(MediaCrypto.decryptString(otherKey, stored))
        assertNull(MediaCrypto.decryptString(key, "zz-not-hex"))
    }
}
