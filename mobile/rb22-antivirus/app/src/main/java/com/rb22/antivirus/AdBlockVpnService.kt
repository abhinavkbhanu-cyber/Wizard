package com.rb22.antivirus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import androidx.core.app.NotificationCompat
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.nio.ByteOrder
import java.util.concurrent.atomic.AtomicBoolean

/**
 * DNS-only local VPN. Only DNS packets sent to the virtual DNS address are routed here;
 * all other IP traffic remains on Android's normal network route.
 */
class AdBlockVpnService : VpnService() {
    companion object {
        const val ACTION_START = "com.rb22.antivirus.AD_BLOCK_START"
        const val ACTION_STOP = "com.rb22.antivirus.AD_BLOCK_STOP"
        private const val CHANNEL_ID = "rb22_dns_protection"
        private const val NOTIFICATION_ID = 2230
        private const val VIRTUAL_DNS = "10.22.0.2"
        private const val UPSTREAM_DNS = "1.1.1.1"
    }

    private var tunnel: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopProtection()
            return START_NOT_STICKY
        }
        startForeground(NOTIFICATION_ID, buildNotification())
        if (!running.get()) startProtection()
        return START_STICKY
    }

    private fun startProtection() {
        try {
            val descriptor = Builder()
                .setSession("RB22 DNS Protection")
                .setMtu(1500)
                .addAddress("10.22.0.1", 32)
                .addDnsServer(VIRTUAL_DNS)
                .addRoute(VIRTUAL_DNS, 32)
                .establish() ?: run { stopSelf(); return }
            tunnel = descriptor
            running.set(true)
            worker = Thread({ packetLoop(descriptor) }, "RB22-DNS-Filter").apply { start() }
            getSharedPreferences(PROTECTION_PREFS, MODE_PRIVATE).edit().putBoolean("dns_filter_enabled", true).apply()
        } catch (_: Exception) {
            stopProtection()
        }
    }

    private fun packetLoop(descriptor: ParcelFileDescriptor) {
        val input = FileInputStream(descriptor.fileDescriptor)
        val output = FileOutputStream(descriptor.fileDescriptor)
        val buffer = ByteArray(32767)
        try {
            while (running.get()) {
                val length = input.read(buffer)
                if (length <= 0) continue
                val response = handleDnsPacket(buffer.copyOf(length))
                if (response != null && running.get()) {
                    output.write(response)
                    output.flush()
                }
            }
        } catch (_: Exception) {
            // Closing the tunnel interrupts the blocking read during shutdown.
        } finally {
            try { input.close() } catch (_: Exception) { }
            try { output.close() } catch (_: Exception) { }
        }
    }

    internal fun handleDnsPacket(packet: ByteArray): ByteArray? {
        if (packet.size < 28 || (packet[0].toInt() ushr 4 and 0x0f) != 4) return null
        val ipHeaderLength = (packet[0].toInt() and 0x0f) * 4
        if (ipHeaderLength < 20 || packet.size < ipHeaderLength + 8) return null
        if ((packet[9].toInt() and 0xff) != 17) return null // UDP only
        val udpOffset = ipHeaderLength
        val sourcePort = u16(packet, udpOffset)
        val destinationPort = u16(packet, udpOffset + 2)
        if (destinationPort != 53) return null
        val udpLength = u16(packet, udpOffset + 4)
        if (udpLength < 20 || packet.size < udpOffset + udpLength) return null
        val dnsStart = udpOffset + 8
        val dns = packet.copyOfRange(dnsStart, udpOffset + udpLength)
        if (dns.size < 12) return null
        val host = readQuestionHost(dns) ?: return null
        val answer = if (DnsBlocklist.isBlocked(host)) blockedResponse(dns) else forwardDns(dns) ?: return null
        return buildUdpIpv4Response(packet, ipHeaderLength, sourcePort, answer)
    }

    private fun forwardDns(query: ByteArray): ByteArray? {
        return try {
            DatagramSocket().use { socket ->
                if (!protect(socket)) return null
                socket.soTimeout = 3500
                val server = InetAddress.getByName(UPSTREAM_DNS)
                socket.send(DatagramPacket(query, query.size, server, 53))
                val data = ByteArray(4096)
                val response = DatagramPacket(data, data.size)
                socket.receive(response)
                data.copyOf(response.length)
            }
        } catch (_: Exception) { null }
    }

    private fun blockedResponse(query: ByteArray): ByteArray {
        val response = query.copyOf()
        val flags = u16(response, 2)
        val nextFlags = (flags or 0x8000 or 0x0080) and 0xfff0 or 0x0003 // QR + RA + NXDOMAIN
        put16(response, 2, nextFlags)
        for (offset in 6..11 step 2) put16(response, offset, 0)
        return response
    }

    private fun readQuestionHost(dns: ByteArray): String? {
        var offset = 12
        val labels = mutableListOf<String>()
        while (offset < dns.size) {
            val size = dns[offset++].toInt() and 0xff
            if (size == 0) return labels.joinToString(".").takeIf { it.isNotBlank() }
            if (size > 63 || offset + size > dns.size) return null
            labels += dns.copyOfRange(offset, offset + size).toString(Charsets.US_ASCII)
            offset += size
        }
        return null
    }

    private fun buildUdpIpv4Response(request: ByteArray, ipLength: Int, clientPort: Int, dns: ByteArray): ByteArray {
        val udpLength = 8 + dns.size
        val response = ByteArray(20 + udpLength)
        response[0] = 0x45
        put16(response, 2, response.size)
        response[4] = request[4]; response[5] = request[5]
        put16(response, 6, 0x4000)
        response[8] = 64
        response[9] = 17
        // Reverse the IPv4 endpoints.
        System.arraycopy(request, 16, response, 12, 4)
        System.arraycopy(request, 12, response, 16, 4)
        put16(response, 10, ipv4Checksum(response, 20))
        put16(response, 20, 53)
        put16(response, 22, clientPort)
        put16(response, 24, udpLength)
        put16(response, 26, 0) // UDP checksum may be zero for IPv4.
        System.arraycopy(dns, 0, response, 28, dns.size)
        return response
    }

    private fun ipv4Checksum(data: ByteArray, length: Int): Int {
        var sum = 0L
        var i = 0
        while (i < length) {
            sum += (((data[i].toInt() and 0xff) shl 8) or (data[i + 1].toInt() and 0xff)).toLong()
            i += 2
        }
        while ((sum shr 16) != 0L) sum = (sum and 0xffff) + (sum shr 16)
        return sum.inv().toInt() and 0xffff
    }

    private fun u16(data: ByteArray, offset: Int): Int =
        ((data[offset].toInt() and 0xff) shl 8) or (data[offset + 1].toInt() and 0xff)

    private fun put16(data: ByteArray, offset: Int, value: Int) {
        data[offset] = (value ushr 8).toByte()
        data[offset + 1] = value.toByte()
    }

    private fun buildNotification(): Notification {
        val manager = getSystemService(NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(NotificationChannel(CHANNEL_ID, "RB22 DNS ad and tracker protection", NotificationManager.IMPORTANCE_LOW))
        }
        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_secure)
            .setContentTitle("RB22 DNS protection is active")
            .setContentText("Filtering known ad and tracker domains")
            .setOngoing(true)
            .build()
    }

    private fun stopProtection() {
        running.set(false)
        try { tunnel?.close() } catch (_: Exception) { }
        tunnel = null
        worker = null
        getSharedPreferences(PROTECTION_PREFS, MODE_PRIVATE).edit().putBoolean("dns_filter_enabled", false).apply()
        stopForeground(STOP_FOREGROUND_REMOVE)
        stopSelf()
    }

    override fun onDestroy() {
        running.set(false)
        try { tunnel?.close() } catch (_: Exception) { }
        tunnel = null
        super.onDestroy()
    }
}
