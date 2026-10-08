package com.rb22

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.nio.ByteBuffer
import java.util.concurrent.atomic.AtomicBoolean

class DnsVpnService : VpnService() {
    companion object {
        const val EXTRA_DNS_IP = "dns_ip"
        const val ACTION_STOP = "com.rb22.DNS_STOP"
        private const val CHANNEL = "rb22_dns"
        private const val NOTIFICATION_ID = 2201
    }

    private var vpnInterface: ParcelFileDescriptor? = null
    private var worker: Thread? = null
    private val running = AtomicBoolean(false)

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        if (intent?.action == ACTION_STOP) {
            stopDnsVpn()
            return START_NOT_STICKY
        }
        val dnsIp = intent?.getStringExtra(EXTRA_DNS_IP) ?: "1.1.1.1"
        startForeground(NOTIFICATION_ID, notification(dnsIp))
        establish(dnsIp)
        return START_STICKY
    }

    private fun establish(dnsIp: String) {
        stopDnsVpn()
        val dns = InetAddress.getByName(dnsIp)
        vpnInterface = Builder()
            .setSession("RB22 Auto DNS")
            .setMtu(1500)
            .addAddress("10.0.0.2", 32)
            .addRoute(dnsIp, 32)
            .addDnsServer(dns)
            .establish()

        val fd = vpnInterface?.fileDescriptor ?: return
        running.set(true)
        worker = Thread {
            val input = FileInputStream(fd)
            val output = FileOutputStream(fd)
            val packet = ByteArray(32767)
            while (running.get()) {
                val length = try { input.read(packet) } catch (_: Exception) { break }
                if (length <= 0) continue
                handlePacket(packet, length, output, dnsIp)
            }
        }.apply {
            name = "RB22-DnsVpn"
            start()
        }
    }

    private fun handlePacket(packet: ByteArray, length: Int, output: FileOutputStream, dnsIp: String) {
        if (length < 28) return
        val version = (packet[0].toInt() ushr 4) and 0xF
        val ihl = (packet[0].toInt() and 0xF) * 4
        val protocol = packet[9].toInt() and 0xFF
        if (version != 4 || ihl < 20 || protocol != 17 || length < ihl + 8) return

        val destination = ((packet[16].toInt() and 255) shl 24) or
                ((packet[17].toInt() and 255) shl 16) or
                ((packet[18].toInt() and 255) shl 8) or
                (packet[19].toInt() and 255)
        val expected = InetAddress.getByName(dnsIp).address
        val expectedInt = ByteBuffer.wrap(expected).int
        if (destination != expectedInt) return

        val udpOffset = ihl
        val srcPort = ((packet[udpOffset].toInt() and 255) shl 8) or (packet[udpOffset + 1].toInt() and 255)
        val udpLength = ((packet[udpOffset + 4].toInt() and 255) shl 8) or (packet[udpOffset + 5].toInt() and 255)
        if (udpLength < 8 || udpOffset + udpLength > length) return

        val dnsPayload = packet.copyOfRange(udpOffset + 8, udpOffset + udpLength)
        val socket = DatagramSocket()
        try {
            if (!protect(socket)) return
            socket.soTimeout = 2000
            val upstream = DatagramPacket(dnsPayload, dnsPayload.size, InetAddress.getByName(dnsIp), 53)
            socket.send(upstream)

            val response = ByteArray(4096)
            val reply = DatagramPacket(response, response.size)
            socket.receive(reply)

            val out = ByteArray(20 + 8 + reply.length)
            out[0] = 0x45
            write16(out, 2, out.size)
            write16(out, 4, 0)
            write16(out, 6, 0)
            out[8] = 64
            out[9] = 17
            System.arraycopy(packet, 16, out, 12, 4)
            System.arraycopy(packet, 12, out, 16, 4)

            write16(out, 20, 53)
            write16(out, 22, srcPort)
            write16(out, 24, 8 + reply.length)
            System.arraycopy(response, 0, out, 28, reply.length)

            write16(out, 10, 0)
            write16(out, 10, checksum(out, 0, 20))
            write16(out, 26, 0)
            write16(out, 26, udpChecksum(out))
            output.write(out)
        } catch (_: Exception) {
        } finally {
            socket.close()
        }
    }

    private fun udpChecksum(packet: ByteArray): Int {
        var sum = 0L
        for (i in 12 until 20 step 2) sum += u16(packet, i)
        sum += 17
        sum += u16(packet, 24)
        var i = 20
        while (i + 1 < packet.size) {
            sum += u16(packet, i)
            i += 2
        }
        if (i < packet.size) sum += (packet[i].toInt() and 255) shl 8
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        val result = sum.toInt() and 0xFFFF
        return if (result == 0) 0xFFFF else result.inv() and 0xFFFF
    }

    private fun checksum(bytes: ByteArray, offset: Int, length: Int): Int {
        var sum = 0L
        var i = offset
        while (i + 1 < offset + length) {
            sum += u16(bytes, i)
            i += 2
        }
        while (sum ushr 16 != 0L) sum = (sum and 0xFFFF) + (sum ushr 16)
        return sum.toInt().inv() and 0xFFFF
    }

    private fun u16(b: ByteArray, i: Int): Long =
        (((b[i].toInt() and 255) shl 8) or (b[i + 1].toInt() and 255)).toLong()

    private fun write16(b: ByteArray, i: Int, value: Int) {
        b[i] = (value ushr 8).toByte()
        b[i + 1] = value.toByte()
    }

    private fun notification(dnsIp: String): Notification {
        if (Build.VERSION.SDK_INT >= 26) {
            val manager = getSystemService(NotificationManager::class.java)
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL, "RB22 DNS", NotificationManager.IMPORTANCE_LOW)
            )
        }
        return if (Build.VERSION.SDK_INT >= 26) {
            Notification.Builder(this, CHANNEL)
                .setContentTitle("RB22 Auto DNS")
                .setContentText("Using $dnsIp")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build()
        } else {
            @Suppress("DEPRECATION")
            Notification.Builder(this)
                .setContentTitle("RB22 Auto DNS")
                .setContentText("Using $dnsIp")
                .setSmallIcon(android.R.drawable.stat_sys_warning)
                .setOngoing(true)
                .build()
        }
    }

    private fun stopDnsVpn() {
        running.set(false)
        worker?.interrupt()
        worker = null
        vpnInterface?.close()
        vpnInterface = null
    }

    override fun onDestroy() {
        stopDnsVpn()
        super.onDestroy()
    }

    override fun onRevoke() {
        stopDnsVpn()
        stopSelf()
        super.onRevoke()
    }
}
