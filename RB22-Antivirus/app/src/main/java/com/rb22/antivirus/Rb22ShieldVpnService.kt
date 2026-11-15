package com.rb22.antivirus

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Intent
import android.net.VpnService
import android.os.Build
import android.os.ParcelFileDescriptor
import java.io.FileInputStream
import java.io.FileOutputStream
import java.net.DatagramPacket
import java.net.DatagramSocket
import java.net.InetAddress
import java.util.concurrent.atomic.AtomicBoolean

class Rb22ShieldVpnService : VpnService() {
    companion object {
        const val START = "com.rb22.antivirus.SHIELD_START"
        const val STOP = "com.rb22.antivirus.SHIELD_STOP"
        private const val CHANNEL = "rb22_shield"
        private const val PREFS = "rb22_shield"
        private const val DNS = "10.22.0.2"
        private const val ADDRESS = "10.22.0.1"
        private const val UPSTREAM = "1.1.1.1"
    }
    private val active = AtomicBoolean(false)
    private var pfd: ParcelFileDescriptor? = null
    private var thread: Thread? = null

    override fun onStartCommand(intent: Intent?, flags: Int, id: Int): Int {
        if (intent?.action == STOP) { stopTunnel(); stopSelf(); return START_NOT_STICKY }
        if (!active.get()) startTunnel()
        return START_STICKY
    }
    private fun startTunnel() {
        createChannel()
        startForeground(2201, notification())
        val fd = try {
            Builder().setSession("RB22 Shield").setMtu(1500)
                .addAddress(ADDRESS, 32).addDnsServer(DNS).addRoute(DNS, 32).establish()
        } catch (_: Exception) { null }
        if (fd == null) {
            getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", false).apply()
            stopForeground(STOP_FOREGROUND_REMOVE); stopSelf(); return
        }
        pfd = fd; active.set(true)
        getSharedPreferences(PREFS, MODE_PRIVATE).edit().putBoolean("enabled", true).apply()
        thread = Thread({ loop(fd) }, "RB22-Shield-DNS").also { it.start() }
    }
    private fun loop(fd: ParcelFileDescriptor) {
        val input = FileInputStream(fd.fileDescriptor); val output = FileOutputStream(fd.fileDescriptor)
        val buffer = ByteArray(32767)
        while (active.get()) {
            try {
                val n=input.read(buffer); if(n<=0)continue
                val response=processPacket(buffer,n)?:continue
                output.write(response)
            } catch (_:Exception) { if(!active.get())break }
        }
    }
    private fun processPacket(p:ByteArray,size:Int):ByteArray? {
        if(size<28||(p[0].toInt() ushr 4)!=4)return null
        val ihl=(p[0].toInt() and 15)*4
        if(ihl<20||size<ihl+8||(p[9].toInt() and 255)!=17)return null
        val u=ihl; if(u16(p,u+2)!=53)return null
        val ul=u16(p,u+4); if(ul<8||u+ul>size)return null
        val query=p.copyOfRange(u+8,u+ul); val host=questionHost(query)?:return null
        val answer=if(DomainFilter.blocked(host)) nxdomain(query) ?: return null else forward(query) ?: return null
        return packet(p.copyOfRange(16,20),p.copyOfRange(12,16),53,u16(p,u),answer)
    }
    private fun forward(query:ByteArray):ByteArray?=try {
        DatagramSocket().use { socket ->
            if(!protect(socket))return null
            socket.soTimeout=3000
            socket.send(DatagramPacket(query,query.size,InetAddress.getByName(UPSTREAM),53))
            val b=ByteArray(4096);val reply=DatagramPacket(b,b.size);socket.receive(reply)
            reply.data.copyOf(reply.length)
        }
    }catch(_:Exception){null}
    private fun questionHost(d:ByteArray):String? {
        if(d.size<17||u16(d,4)<1)return null
        var i=12;val labels=mutableListOf<String>();var total=0
        while(i<d.size){val n=d[i++].toInt() and 255;if(n==0)break
            if(n>63||i+n>d.size)return null
            total+=n+1;if(total>253)return null
            labels+=String(d,i,n,Charsets.US_ASCII);i+=n
        }
        if(labels.isEmpty()||i+4>d.size)return null
        return labels.joinToString(".").lowercase()
    }
    private fun nxdomain(q:ByteArray):ByteArray? {
        if(q.size<17)return null
        var i=12;val count=u16(q,4);if(count<1||count>16)return null
        repeat(count){while(i<q.size){val n=q[i++].toInt() and 255;if(n==0)break
            if(n>63||i+n>q.size)return null;i+=n}
            if(i+4>q.size)return null;i+=4}
        val r=ByteArray(i);q.copyInto(r,0,0,2);r[2]=0x81.toByte();r[3]=0x83.toByte()
        q.copyInto(r,4,4,6);q.copyInto(r,12,12,i);return r
    }
    private fun packet(src:ByteArray,dst:ByteArray,sp:Int,dp:Int,dns:ByteArray):ByteArray {
        val udp=8+dns.size;val total=20+udp;val p=ByteArray(total)
        p[0]=0x45;put16(p,2,total);put16(p,6,0x4000);p[8]=64;p[9]=17
        src.copyInto(p,12);dst.copyInto(p,16);put16(p,10,checksum(p,0,20))
        put16(p,20,sp);put16(p,22,dp);put16(p,24,udp);put16(p,26,0);dns.copyInto(p,28);return p
    }
    private fun checksum(b:ByteArray,o:Int,n:Int):Int {
        var s=0L;var i=o
        while(i<o+n){val a=b[i].toInt() and 255;val c=if(i+1<o+n)b[i+1].toInt() and 255 else 0;s+=(a shl 8) or c;i+=2}
        while((s shr 16)!=0L)s=(s and 65535)+(s shr 16)
        return s.inv().toInt() and 65535
    }
    private fun u16(b:ByteArray,i:Int)=((b[i].toInt() and 255) shl 8) or (b[i+1].toInt() and 255)
    private fun put16(b:ByteArray,i:Int,v:Int){b[i]=(v ushr 8).toByte();b[i+1]=v.toByte()}
    private fun createChannel(){if(Build.VERSION.SDK_INT>=26)getSystemService(NotificationManager::class.java)
        .createNotificationChannel(NotificationChannel(CHANNEL,"RB22 Shield",NotificationManager.IMPORTANCE_LOW))}
    private fun notification():Notification {
        val pi=PendingIntent.getActivity(this,0,Intent(this,MainActivity::class.java),PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE)
        return if(Build.VERSION.SDK_INT>=26)Notification.Builder(this,CHANNEL).setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("RB22 Shield active").setContentText("Filtering DNS requests against the local blocklist").setContentIntent(pi).setOngoing(true).build()
        else @Suppress("DEPRECATION") Notification.Builder(this).setSmallIcon(android.R.drawable.ic_lock_lock)
            .setContentTitle("RB22 Shield active").setContentText("Filtering DNS requests").setContentIntent(pi).setOngoing(true).build()
    }
    private fun stopTunnel(){
        active.set(false);try{pfd?.close()}catch(_:Exception){};pfd=null;thread?.interrupt();thread=null
        getSharedPreferences(PREFS,MODE_PRIVATE).edit().putBoolean("enabled",false).apply();stopForeground(STOP_FOREGROUND_REMOVE)
    }
    override fun onRevoke(){stopTunnel();stopSelf();super.onRevoke()}
    override fun onDestroy(){stopTunnel();super.onDestroy()}
}
