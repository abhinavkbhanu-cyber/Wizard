package com.rb22.antivirus

import java.io.InputStream
import java.security.MessageDigest

internal sealed class FingerprintResult {
    data class Success(val sha256: String, val bytesRead: Long) : FingerprintResult()
    data class TooLarge(val limitBytes: Long) : FingerprintResult()
}

/**
 * Computes a local SHA-256 fingerprint only. It does not determine whether a file is malicious.
 * The byte limit bounds work on untrusted files.
 */
internal object ApkFingerprint {
    const val MAX_FILE_BYTES: Long = 200L * 1024 * 1024

    fun calculate(input: InputStream, maxBytes: Long = MAX_FILE_BYTES): FingerprintResult {
        require(maxBytes >= 0) { "maxBytes must not be negative" }
        val digest = MessageDigest.getInstance("SHA-256")
        val buffer = ByteArray(64 * 1024)
        var total = 0L
        while (true) {
            val count = input.read(buffer)
            if (count < 0) break
            if (count == 0) continue
            total += count
            if (total > maxBytes) return FingerprintResult.TooLarge(maxBytes)
            digest.update(buffer, 0, count)
        }
        val hex = digest.digest().joinToString("") { "%02x".format(it) }
        return FingerprintResult.Success(hex, total)
    }
}
