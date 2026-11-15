package com.rb22.antivirus

import java.io.ByteArrayInputStream
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ApkFingerprintTest {
    @Test
    fun computesKnownSha256ForAbc() {
        val result = ApkFingerprint.calculate(ByteArrayInputStream("abc".toByteArray()))
        assertTrue(result is FingerprintResult.Success)
        result as FingerprintResult.Success
        assertEquals("ba7816bf8f01cfea414140de5dae2223b00361a396177a9cb410ff61f20015ad", result.sha256)
        assertEquals(3L, result.bytesRead)
    }

    @Test
    fun acceptsFileExactlyAtLimit() {
        val result = ApkFingerprint.calculate(ByteArrayInputStream(byteArrayOf(1, 2, 3)), maxBytes = 3)
        assertTrue(result is FingerprintResult.Success)
    }

    @Test
    fun rejectsFileOverLimit() {
        val result = ApkFingerprint.calculate(ByteArrayInputStream(byteArrayOf(1, 2, 3, 4)), maxBytes = 3)
        assertEquals(FingerprintResult.TooLarge(3), result)
    }

    @Test(expected = IllegalArgumentException::class)
    fun rejectsNegativeLimit() {
        ApkFingerprint.calculate(ByteArrayInputStream(byteArrayOf()), maxBytes = -1)
    }
}
