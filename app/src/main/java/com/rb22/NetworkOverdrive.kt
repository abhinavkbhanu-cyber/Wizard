package com.rb22

import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Callable
import java.util.concurrent.Executors
import kotlin.math.max

/** Bounded, user-triggered multi-connection bandwidth benchmark. */
object NetworkOverdrive {
    private const val DOWNLOAD_BYTES_PER_STREAM = 2 * 1024 * 1024
    private const val UPLOAD_BYTES_PER_STREAM = 512 * 1024
    private const val STREAMS = 4
    private const val CONNECT_TIMEOUT_MS = 8_000
    private const val READ_TIMEOUT_MS = 12_000

    data class Result(
        val downloadMbps: Double,
        val uploadMbps: Double,
        val downloadBytes: Long,
        val uploadBytes: Long
    )

    fun run(onComplete: (Result?, String?) -> Unit) {
        Thread {
            try {
                val download = transfer(download = true)
                val upload = transfer(download = false)
                onComplete(
                    Result(download.first, upload.first, download.second, upload.second),
                    null
                )
            } catch (e: Exception) {
                onComplete(null, e.message ?: "Network test failed")
            }
        }.start()
    }

    private fun transfer(download: Boolean): Pair<Double, Long> {
        val pool = Executors.newFixedThreadPool(STREAMS)
        try {
            val bytesPerStream = if (download) DOWNLOAD_BYTES_PER_STREAM else UPLOAD_BYTES_PER_STREAM
            val payload = if (download) null else ByteArray(bytesPerStream) { (it * 31).toByte() }
            val startNs = System.nanoTime()
            val jobs = (0 until STREAMS).map {
                pool.submit(Callable<Long> {
                    if (download) downloadStream(bytesPerStream) else uploadStream(payload!!)
                })
            }
            var totalBytes = 0L
            jobs.forEach { totalBytes += it.get() }
            val elapsedNs = max(1L, System.nanoTime() - startNs)
            val mbps = (totalBytes * 8.0) / (elapsedNs / 1_000_000_000.0) / 1_000_000.0
            return mbps to totalBytes
        } finally {
            pool.shutdownNow()
        }
    }

    private fun downloadStream(bytes: Int): Long {
        val connection = (URL("https://speed.cloudflare.com/__down?bytes=$bytes")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "GET"
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Cache-Control", "no-cache")
        }
        try {
            val code = connection.responseCode
            if (code !in 200..299) error("Download test server returned HTTP $code")
            var count = 0L
            connection.inputStream.use { input ->
                val buffer = ByteArray(32 * 1024)
                while (true) {
                    val read = input.read(buffer)
                    if (read < 0) break
                    count += read
                }
            }
            return count
        } finally {
            connection.disconnect()
        }
    }

    private fun uploadStream(payload: ByteArray): Long {
        val connection = (URL("https://speed.cloudflare.com/__up")
            .openConnection() as HttpURLConnection).apply {
            requestMethod = "POST"
            doOutput = true
            connectTimeout = CONNECT_TIMEOUT_MS
            readTimeout = READ_TIMEOUT_MS
            useCaches = false
            setRequestProperty("Content-Type", "application/octet-stream")
            setFixedLengthStreamingMode(payload.size)
        }
        try {
            connection.outputStream.use { output ->
                output.write(payload)
                output.flush()
            }
            val code = connection.responseCode
            if (code !in 200..299) error("Upload test server returned HTTP $code")
            connection.inputStream.use { input -> while (input.read() != -1) { } }
            return payload.size.toLong()
        } finally {
            connection.disconnect()
        }
    }
}
