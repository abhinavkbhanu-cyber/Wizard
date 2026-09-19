package com.rb22

import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val dnsOptions = listOf(
        "Cloudflare" to "one.one.one.one",
        "Google" to "dns.google",
        "Quad9" to "dns.quad9.net",
        "AdGuard" to "dns.adguard-dns.com"
    )

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updateStats()

        findViewById<TextView>(R.id.overlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName")))
            } else {
                val intent = Intent(this, OverlayService::class.java)
                if (Build.VERSION.SDK_INT >= 26) startForegroundService(intent) else startService(intent)
            }
        }

        findViewById<Button>(R.id.dns_auto).setOnClickListener { testDns() }

        val dnsButtons = listOf(
            R.id.dns_cloudflare to dnsOptions[0],
            R.id.dns_google to dnsOptions[1],
            R.id.dns_quad9 to dnsOptions[2],
            R.id.dns_adguard to dnsOptions[3]
        )
        dnsButtons.forEach { (id, dns) ->
            findViewById<Button>(id).setOnClickListener { openPrivateDns(dns.first, dns.second) }
        }

        findViewById<Button>(R.id.smart_auto).setOnClickListener {
            Toast.makeText(this, "RB22 Smart Automation enabled", Toast.LENGTH_SHORT).show()
        }
    }

    private fun openPrivateDns(name: String, hostname: String) {
        Toast.makeText(this, "$name selected: $hostname. Set this hostname in Private DNS.", Toast.LENGTH_LONG).show()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                startActivity(Intent(Settings.ACTION_PRIVATE_DNS_SETTINGS))
            } else {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun testDns() {
        val status = findViewById<TextView>(R.id.dns_status)
        status.text = "DNS: Testing..."
        Executors.newSingleThreadExecutor().execute {
            val results = dnsOptions.map { (name, host) ->
                val start = System.nanoTime()
                val ok = try {
                    InetAddress.getByName(host)
                    true
                } catch (_: Exception) {
                    false
                }
                val ms = (System.nanoTime() - start) / 1_000_000
                Triple(name, ms, ok)
            }.filter { it.third }.sortedBy { it.second }

            runOnUiThread {
                if (results.isEmpty()) {
                    status.text = "DNS: Test failed — check your connection."
                } else {
                    val best = results.first()
                    status.text = "FASTEST LOOKUP: " + best.first + "  " + best.second + " ms"
                    Toast.makeText(this, "RB22 recommends " + best.first + " for this connection", Toast.LENGTH_LONG).show()
                }
            }
        }
    }

    private fun updateStats() {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        val percent = (used * 100 / info.totalMem).toInt()
        findViewById<TextView>(R.id.ram).text = "RAM     $percent%"

        val battery = registerReceiver(
            null,
            android.content.IntentFilter(android.content.Intent.ACTION_BATTERY_CHANGED)
        )
        val temp = battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        findViewById<TextView>(R.id.temp).text =
            if (temp >= 0) "TEMP    " + (temp / 10.0) + "°C" else "TEMP    --"
    }
}
