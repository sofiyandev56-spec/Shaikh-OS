package dev.shaikh.stark.security

import org.junit.Assert.assertEquals
import org.junit.Test

class PinKdfTest {

    @Test
    fun `PBKDF2-HMAC-SHA256 matches the published test vector`() {
        // P = "password", S = "salt", c = 1, dkLen = 32 (RFC 7914 §11 / widely published vector).
        val out = PinKdf.stretch("password".toCharArray(), "salt".toByteArray(), iterations = 1)
        assertEquals("120fb6cffcf8b32c43e7225256c4f837a86548c92ccc35480805987cb70be17b", out.hex())
    }

    @Test
    fun `four wrong PINs are free`() {
        (0..4).forEach { assertEquals(0L, LockoutPolicy.delayAfter(it)) }
    }

    @Test
    fun `delays start at 30 seconds and double`() {
        assertEquals(30_000L, LockoutPolicy.delayAfter(5))
        assertEquals(60_000L, LockoutPolicy.delayAfter(6))
        assertEquals(120_000L, LockoutPolicy.delayAfter(7))
    }

    @Test
    fun `delays cap at one hour`() {
        assertEquals(3_600_000L, LockoutPolicy.delayAfter(12))
        assertEquals(3_600_000L, LockoutPolicy.delayAfter(500))
    }

    private fun ByteArray.hex() = joinToString("") { "%02x".format(it) }
}
