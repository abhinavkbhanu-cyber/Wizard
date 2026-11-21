package com.rb22

import android.content.Context
import android.os.Environment
import java.io.BufferedInputStream
import java.io.File
import java.io.FileOutputStream
import java.io.RandomAccessFile
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

class FastDownloadManager(context: Context) {
    private val appContext = context.applicationContext
    private val cancelled = AtomicBoolean(false)
    @Volatile private var worker = Executors.newSingleThreadExecutor().apply { shutdown() }

    interface Listener {
        fun onProgress(downloaded: Long, total: Long, parallel: Boolean)
        fun onComplete(file: File, parallel: Boolean)
        fun onError(message: String)
    }

    fun start(rawUrl: String, listener: Listener) {
        cancel()
        cancelled.set(false)
        worker = Executors.newSingleThreadExecutor()
        worker.execute {
            try {
                val url = URL(rawUrl.trim())
                require(url.protocol.equals("https", true) || url.protocol.equals("http", true)) {
                    "Use a valid HTTP or HTTPS download link."
                }
                require(url.host.isNotBlank()) { "The download link is invalid." }

                val directory = appContext.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS)
                    ?: File(appContext.filesDir, "downloads")
                if (!directory.exists() && !directory.mkdirs()) {
                    throw IllegalStateException("RB22 could not create its download folder.")
                }
                val file = File(directory, safeFileName(url))
                if (file.exists()) file.delete()

                val total = probeRange(url)
                var usedParallel = false
                if (total != null && total > 1L && !cancelled.get()) {
                    try {
                        downloadParallel(url, file, total, listener)
                        usedParallel = true
                    } catch (error: Exception) {
                        if (cancelled.get()) throw InterruptedException("Download cancelled")
                        file.delete()
                        downloadSingle(url, file, listener)
                    }
                } else {
                    downloadSingle(url, file, listener)
                }
                if (cancelled.get()) {
                    file.delete()
                    throw InterruptedException("Download cancelled")
                }
                listener.onComplete(file, usedParallel)
            } catch (_: InterruptedException) {
                listener.onError("Download cancelled.")
            } catch (error: Exception) {
                listener.onError(error.message ?: "Download failed. Check the link and try again.")
            }
        }
    }

    fun cancel() {
        cancelled.set(true)
        worker.shutdownNow()
    }

    private fun probeRange(url: URL): Long? {
        val connection = openConnection(url)
        return try {
            connection.setRequestProperty("Range", "bytes=0-0")
            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) return null
            val header = connection.getHeaderField("Content-Range") ?: return null
            val match = Regex("bytes\\s+0-0/(\\d+)", RegexOption.IGNORE_CASE).find(header) ?: return null
            match.groupValues[1].toLongOrNull()
        } finally {
            connection.disconnect()
        }
    }

    private fun downloadParallel(url: URL, file: File, total: Long, listener: Listener) {
        val count = minOf(4, total.toInt().coerceAtLeast(1))
        val completed = AtomicLong(0L)
        val pool = Executors.newFixedThreadPool(count)
        val futures = mutableListOf<Future<*>>()
        try {
            RandomAccessFile(file, "rw").use { random ->
                random.setLength(total)
                val channel = random.channel
                for (index in 0 until count) {
                    val start = total * index / count
                    val end = total * (index + 1) / count - 1L
                    futures += pool.submit {
                        val connection = openConnection(url)
                        try {
                            connection.setRequestProperty("Range", "bytes=$start-$end")
                            if (connection.responseCode != HttpURLConnection.HTTP_PARTIAL) {
                                throw IllegalStateException("The server does not support parallel ranges.")
                            }
                            val contentRange = connection.getHeaderField("Content-Range") ?: ""
                            if (!contentRange.startsWith("bytes $start-$end/", true)) {
                                throw IllegalStateException("The server returned an invalid download range.")
                            }
                            connection.inputStream.use { raw ->
                                BufferedInputStream(raw).use { input ->
                                    val buffer = ByteArray(64 * 1024)
                                    var position = start
                                    var segmentBytes = 0L
                                    while (true) {
                                        if (cancelled.get() || Thread.currentThread().isInterrupted) {
                                            throw InterruptedException("Download cancelled")
                                        }
                                        val read = input.read(buffer)
                                        if (read < 0) break
                                        if (segmentBytes + read > end - start + 1L) {
                                            throw IllegalStateException("The server sent more data than requested.")
                                        }
                                        val byteBuffer = java.nio.ByteBuffer.wrap(buffer, 0, read)
                                        while (byteBuffer.hasRemaining()) {
                                            position += channel.write(byteBuffer, position)
                                        }
                                        segmentBytes += read
                                        val now = completed.addAndGet(read.toLong())
                                        listener.onProgress(now, total, true)
                                    }
                                    if (segmentBytes != end - start + 1L) {
                                        throw IllegalStateException("A download segment ended early.")
                                    }
                                }
                            }
                        } finally {
                            connection.disconnect()
                        }
                    }
                }
                futures.forEach { it.get() }
            }
        } catch (error: Exception) {
            futures.forEach { it.cancel(true) }
            throw error
        } finally {
            pool.shutdownNow()
        }
    }

    private fun downloadSingle(url: URL, file: File, listener: Listener) {
        val connection = openConnection(url)
        try {
            val code = connection.responseCode
            if (code !in 200..299) throw IllegalStateException("Download server returned HTTP $code.")
            val total = connection.getHeaderFieldLong("Content-Length", -1L)
            var downloaded = 0L
            connection.inputStream.use { raw ->
                BufferedInputStream(raw).use { input ->
                    FileOutputStream(file).use { output ->
                        val buffer = ByteArray(64 * 1024)
                        while (true) {
                            if (cancelled.get() || Thread.currentThread().isInterrupted) {
                                throw InterruptedException("Download cancelled")
                            }
                            val read = input.read(buffer)
                            if (read < 0) break
                            output.write(buffer, 0, read)
                            downloaded += read
                            listener.onProgress(downloaded, total, false)
                        }
                        output.flush()
                    }
                }
            }
            if (total > 0L && downloaded != total) {
                throw IllegalStateException("The download was incomplete. Please retry.")
            }
        } catch (error: Exception) {
            file.delete()
            throw error
        } finally {
            connection.disconnect()
        }
    }

    private fun openConnection(url: URL): HttpURLConnection {
        return (url.openConnection() as HttpURLConnection).apply {
            connectTimeout = 15000
            readTimeout = 30000
            instanceFollowRedirects = true
            setRequestProperty("User-Agent", "RB22-FastDownload/4.1")
            setRequestProperty("Accept-Encoding", "identity")
        }
    }

    private fun safeFileName(url: URL): String {
        val raw = url.path.substringAfterLast('/').ifBlank { "rb22-download.bin" }
        val decoded = runCatching { java.net.URLDecoder.decode(raw, "UTF-8") }.getOrDefault(raw)
        return decoded.replace(Regex("[^A-Za-z0-9._ -]"), "_")
            .trim().take(100).ifBlank { "rb22-download.bin" }
    }
}
