package com.rb22

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.net.TrafficStats
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.TextView
import android.widget.Toast
import android.os.CountDownTimer
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress
import java.net.DatagramPacket
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val dnsOptions = listOf(
        "Cloudflare" to ("one.one.one.one" to "1.1.1.1"),
        "Google" to ("dns.google" to "8.8.8.8"),
        "Quad9" to ("dns.quad9.net" to "9.9.9.9"),
        "AdGuard" to ("dns.adguard-dns.com" to "94.140.14.14"),
        "OpenDNS" to ("dns.opendns.com" to "208.67.222.222")
    )

    private val gamingEndpoints = listOf("Cloudflare" to "1.1.1.1", "Google" to "8.8.8.8", "Quad9" to "9.9.9.9")
    private var pendingDnsIp: String? = null
    private val networkHandler by lazy { android.os.Handler(mainLooper) }
    private var networkFocusRunning = false
    private var previousRxBytes = TrafficStats.UNSUPPORTED.toLong()
    private var previousTxBytes = TrafficStats.UNSUPPORTED.toLong()
    private var previousNetworkSampleTime = 0L
    private var networkFocusDeviceFallback = false
    private var focusedPackage: String? = null
    private var lastUsageEventTime = 0L
    private var pendingNetworkFocusStart = false
    private val networkFocusTick = object : Runnable {
        override fun run() {
            updateNetworkFocusStats()
            if (networkFocusRunning) networkHandler.postDelayed(this, 2000L)
        }
    }

    private var networkRecoveryAssist: NetworkRecoveryAssist? = null

    private val gamePrefs by lazy {
        getSharedPreferences("rb22_games", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        UpdateCheckWorker.schedule(this)
        UpdateCheckWorker.checkNow(this)
        updateStats()
        applyActiveTheme()
        setupDeviceRefresh()
        setupBoostModes()
        setupMobileControls()
        setupV12Controls()
        startUltimateEngine()

        findViewById<Button>(R.id.start_booster).setOnClickListener { startGameBooster() }
        findViewById<Button>(R.id.stop_booster).setOnClickListener { stopGameBooster() }
        findViewById<TextView>(R.id.booster_status).text = if (gamePrefs.getBoolean("booster_enabled", false)) "● BOOSTER ON" else "○ BOOSTER OFF"
        applyActiveTheme()

        findViewById<Button>(R.id.overlay).setOnClickListener {
            if (!Settings.canDrawOverlays(this)) {
                startActivity(
                    Intent(
                        Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                        Uri.parse("package:$packageName")
                    )
                )
            } else {
                requestOverlayWithFps()
            }
        }

        findViewById<Button>(R.id.dns_auto).setOnClickListener { testDns() }
        findViewById<Button>(R.id.dns_disable).setOnClickListener { disableDns() }
        findViewById<Button>(R.id.gaming_ping).setOnClickListener { runGamingPing() }
        findViewById<Button>(R.id.check_updates).setOnClickListener {
            UpdateCheckWorker.checkNow(this)
            findViewById<TextView>(R.id.update_status).text = "Checking official RB22 releases… Connect to the internet and wait a moment."
            Toast.makeText(this, "Update check queued", Toast.LENGTH_SHORT).show()
        }

        val dnsButtons = listOf(
            R.id.dns_cloudflare to dnsOptions[0],
            R.id.dns_google to dnsOptions[1],
            R.id.dns_quad9 to dnsOptions[2],
            R.id.dns_adguard to dnsOptions[3],
            R.id.dns_opendns to dnsOptions[4]
        )
        dnsButtons.forEach { (id, dns) ->
            findViewById<Button>(id).setOnClickListener {
                activateDns(dns.first, dns.second.second)
            }
        }

        findViewById<Button>(R.id.add_game).setOnClickListener { showGamePicker() }
        findViewById<Button>(R.id.game_library).setOnClickListener { showGameLibrary() }
        updateGameLibraryText()

        findViewById<Button>(R.id.smart_auto).setOnClickListener {
            startAutoBoostMonitor()
            Toast.makeText(this, "RB22 Smart Automation enabled", Toast.LENGTH_SHORT).show()
        }

        if (gamePrefs.getBoolean("booster_enabled", false) && savedGames().isNotEmpty()) startAutoBoostMonitor()
    }



    private fun setupV12Controls() {
        val engine = V12PerformanceEngine(this)
        val status = findViewById<TextView>(R.id.v12_engine_status)
        fun refresh(message: String? = null) {
            status.text = message ?: engine.statusText()
            findViewById<TextView>(R.id.v12_graphics_advice).text =
                "GRAPHICS ADVICE • " + engine.graphicsRecommendation()
        }
        fun select(profile: V12PerformanceEngine.Profile, label: String) {
            engine.setProfile(profile)
            refresh("✓ $label PROFILE ACTIVE • " + engine.statusText())
            Toast.makeText(this, "$label profile selected", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.v12_balanced).setOnClickListener {
            select(V12PerformanceEngine.Profile.BALANCED, "BALANCED")
        }
        findViewById<Button>(R.id.v12_gaming).setOnClickListener {
            select(V12PerformanceEngine.Profile.GAMING, "GAMING")
        }
        findViewById<Button>(R.id.v12_streaming).setOnClickListener {
            select(V12PerformanceEngine.Profile.STREAMING, "STREAMING")
        }
        findViewById<Button>(R.id.v12_battery).setOnClickListener {
            select(V12PerformanceEngine.Profile.BATTERY, "BATTERY")
        }
        findViewById<Button>(R.id.v12_quick_boost).setOnClickListener {
            val caution = engine.isThermalCaution()
            val profile = if (caution) V12PerformanceEngine.Profile.BALANCED
                else V12PerformanceEngine.Profile.GAMING
            engine.setProfile(profile)
            refresh(if (caution) "THERMAL CAUTION • BALANCED PROFILE SELECTED" else "QUICK BOOST • GAMING PROFILE SELECTED")
            Toast.makeText(this, if (caution) "Device is warm. Balanced profile selected to help avoid extra heat." else "Gaming profile selected.", Toast.LENGTH_LONG).show()
        }
        val networkStatus = findViewById<TextView>(R.id.v12_network_status)
        val networkPrefs = getSharedPreferences("rb22_games", MODE_PRIVATE)
        fun setNetworkProfile(profile: String) {
            networkPrefs.edit().putString("network_focus_profile", profile).apply()
            networkStatus.text = "NETWORK FOCUS: $profile"
            Toast.makeText(this, "$profile focus selected", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.v12_network_gaming).setOnClickListener { setNetworkProfile("GAMING") }
        findViewById<Button>(R.id.v12_network_streaming).setOnClickListener { setNetworkProfile("STREAMING") }
        findViewById<Button>(R.id.v12_network_default).setOnClickListener { setNetworkProfile("DEFAULT") }
        networkStatus.text = "NETWORK FOCUS: " + networkPrefs.getString("network_focus_profile", "DEFAULT")

        findViewById<Button>(R.id.v12_graphics_button).setOnClickListener {
            refresh()
            AlertDialog.Builder(this)
                .setTitle("RB22 Graphics Recommendations")
                .setMessage(engine.graphicsRecommendation() + "\\n\\nThese are recommendations for the game's own graphics menu. RB22 cannot directly change another app's graphics settings.")
                .setPositiveButton("OK", null)
                .show()
        }
        refresh()
    }

    private fun setupMobileControls() {
        findViewById<Button>(R.id.mobile_game_mode).setOnClickListener {
            gamePrefs.edit().putBoolean("game_mode", true).putBoolean("max_fps", true).putString("profile", "MAX FPS").apply()
            applyActiveTheme()
            findViewById<TextView>(R.id.boost_status).text = "✓ MAX FPS MODE ACTIVE • LOW OVERHEAD"
            Toast.makeText(this, "MAX FPS mode active — RB22 is minimizing its own background work.", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.mobile_tools).setOnClickListener { showExtraTools() }

        findViewById<Button>(R.id.mobile_charging).setOnClickListener {
            val enabled = !gamePrefs.getBoolean("charging_mode", false)
            gamePrefs.edit().putBoolean("charging_mode", enabled).apply()
            applyActiveTheme()
            findViewById<TextView>(R.id.boost_status).text =
                if (enabled) "✓ CHARGING MODE ACTIVE • LOW OVERHEAD" else "CHARGING MODE OFF"
            Toast.makeText(this, if (enabled) "Charging Mode active — RB22 will reduce monitoring overhead." else "Charging Mode disabled.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun showExtraTools() {
        val tools = arrayOf("⏱ Gaming Timer", "📡 Gaming Ping Test", "📊 Refresh Device Stats")
        AlertDialog.Builder(this).setTitle("RB22 Extra Tools").setItems(tools) { _, which ->
            when (which) {
                0 -> showGamingTimer()
                1 -> runGamingPing()
                2 -> { updateStats(); Toast.makeText(this, "RAM and temperature refreshed.", Toast.LENGTH_SHORT).show() }
            }
        }.setNegativeButton("CLOSE", null).show()
    }

    private fun showGamingTimer() {
        val options = arrayOf("15 minutes", "30 minutes", "60 minutes")
        val minutes = intArrayOf(15, 30, 60)
        AlertDialog.Builder(this).setTitle("Gaming Timer").setItems(options) { _, which ->
            val totalMs = minutes[which] * 60_000L
            object : CountDownTimer(totalMs, 1_000L) {
                override fun onTick(ms: Long) {
                    val m = ms / 60_000L
                    val s = (ms / 1_000L) % 60
                    findViewById<TextView>(R.id.boost_status).text =
                        String.format("⏱ GAMING TIMER • %02d:%02d", m, s)
                }
                override fun onFinish() {
                    Toast.makeText(this@MainActivity, "Gaming timer finished.", Toast.LENGTH_LONG).show()
                    findViewById<TextView>(R.id.boost_status).text = "READY • TIMER FINISHED"
                }
            }.start()
            Toast.makeText(this, "Gaming timer started.", Toast.LENGTH_SHORT).show()
        }.setNegativeButton("CLOSE", null).show()
    }

    private fun runGamingPing() {
        Toast.makeText(this, "Testing gaming network latency…", Toast.LENGTH_SHORT).show()
        Executors.newSingleThreadExecutor().execute {
            val results = gamingEndpoints.mapNotNull { (name, host) ->
                val ms = runCatching {
                    java.net.Socket().use { socket ->
                        val t = System.nanoTime()
                        socket.connect(java.net.InetSocketAddress(host, 443), 1200)
                        (System.nanoTime() - t) / 1_000_000
                    }
                }.getOrNull()
                if (ms != null) name to ms else null
            }.sortedBy { it.second }
            runOnUiThread {
                if (results.isEmpty()) {
                    findViewById<TextView>(R.id.boost_status).text = "GAMING PING: TEST FAILED"
                    Toast.makeText(this, "Latency test failed — check your connection.", Toast.LENGTH_LONG).show()
                } else {
                    val best = results.first()
                    gamePrefs.edit().putString("best_ping_endpoint", best.first).putLong("best_ping_ms", best.second).apply()
                    val message = "BEST NETWORK PING: " + best.first + " • " + best.second + " ms"
                    findViewById<TextView>(R.id.boost_status).text = message
                    Toast.makeText(this, message, Toast.LENGTH_LONG).show()
                }
            }
        }
    }
    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(Context.APP_OPS_SERVICE) as AppOpsManager
        return appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        ) == AppOpsManager.MODE_ALLOWED
    }

    override fun onStart() {
        super.onStart()
        networkRecoveryAssist = NetworkRecoveryAssist(this) { message, networkChanged ->
            runOnUiThread {
                val profile = gamePrefs.getString("network_focus_profile", "DEFAULT")
                findViewById<TextView>(R.id.v12_network_status)?.text = "NETWORK FOCUS: $profile • $message"
                if (networkChanged) {
                    val activeDns = gamePrefs.getString("active_dns_ip", null)
                    if (!activeDns.isNullOrBlank()) {
                        startDnsService(activeDns)
                    }
                }
            }
        }.also { it.start() }
    }

    override fun onStop() {
        networkRecoveryAssist?.stop()
        networkRecoveryAssist = null
        super.onStop()
    }

    override fun onResume() {
        super.onResume()
        if (pendingNetworkFocusStart && hasUsageAccess()) {
            pendingNetworkFocusStart = false
            beginNetworkFocus()
        }
    }

    private fun startNetworkFocus() {
        if (!hasUsageAccess()) {
            pendingNetworkFocusStart = true
            AlertDialog.Builder(this)
                .setTitle("Allow App Focus")
                .setMessage("RB22 needs Usage Access to detect which app is currently on screen and follow app switches. It does not read messages or personal content.")
                .setPositiveButton("OPEN SETTINGS") { _, _ ->
                    startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
                }
                .setNegativeButton("CANCEL") { _, _ -> pendingNetworkFocusStart = false }
                .show()
            return
        }
        beginNetworkFocus()
    }

    private fun beginNetworkFocus() {
        previousRxBytes = TrafficStats.UNSUPPORTED.toLong()
        previousTxBytes = TrafficStats.UNSUPPORTED.toLong()
        networkFocusDeviceFallback = false
        focusedPackage = null
        lastUsageEventTime = System.currentTimeMillis() - 5000L
        networkFocusRunning = true
        networkHandler.removeCallbacks(networkFocusTick)
        findViewById<TextView>(R.id.network_focus_status).text =
            "APP FOCUS ON • Open any app. RB22 will follow it automatically."
        networkHandler.post(networkFocusTick)
    }

    private fun stopNetworkFocus() {
        pendingNetworkFocusStart = false
        networkFocusRunning = false
        networkHandler.removeCallbacks(networkFocusTick)
        focusedPackage = null
        findViewById<TextView>(R.id.network_focus_status).text = "APP FOCUS OFF"
    }

    private fun detectForegroundPackage(): String? {
        val usage = getSystemService(Context.USAGE_STATS_SERVICE) as android.app.usage.UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(lastUsageEventTime, now)
        val event = android.app.usage.UsageEvents.Event()
        var foreground: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            if (event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= 29 &&
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED)) {
                if (event.packageName != packageName) foreground = event.packageName
            }
        }
        lastUsageEventTime = now
        return foreground
    }

    private fun updateNetworkFocusStats() {
        val detected = detectForegroundPackage()
        if (!detected.isNullOrBlank() && detected != focusedPackage) {
            focusedPackage = detected
            val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(detected, 0)).toString()
            }.getOrDefault(detected)
            val uid = runCatching { packageManager.getApplicationInfo(detected, 0).uid }.getOrNull()
            previousRxBytes = uid?.let { TrafficStats.getUidRxBytes(it) } ?: TrafficStats.UNSUPPORTED.toLong()
            previousTxBytes = uid?.let { TrafficStats.getUidTxBytes(it) } ?: TrafficStats.UNSUPPORTED.toLong()
            networkFocusDeviceFallback =
                previousRxBytes == TrafficStats.UNSUPPORTED.toLong() ||
                previousTxBytes == TrafficStats.UNSUPPORTED.toLong()
            if (networkFocusDeviceFallback) {
                previousRxBytes = TrafficStats.getTotalRxBytes()
                previousTxBytes = TrafficStats.getTotalTxBytes()
            }
            previousNetworkSampleTime = System.currentTimeMillis()
            findViewById<TextView>(R.id.network_focus_status).text =
                "FOCUSING: $label • monitoring its network activity"
            return
        }

        val pkg = focusedPackage ?: run {
            findViewById<TextView>(R.id.network_focus_status).text =
                "APP FOCUS ON • Waiting for you to open another app…"
            return
        }
        val uid = runCatching { packageManager.getApplicationInfo(pkg, 0).uid }.getOrNull()
        val rx = if (networkFocusDeviceFallback) TrafficStats.getTotalRxBytes()
            else uid?.let { TrafficStats.getUidRxBytes(it) } ?: TrafficStats.UNSUPPORTED.toLong()
        val tx = if (networkFocusDeviceFallback) TrafficStats.getTotalTxBytes()
            else uid?.let { TrafficStats.getUidTxBytes(it) } ?: TrafficStats.UNSUPPORTED.toLong()
        if (rx == TrafficStats.UNSUPPORTED.toLong() || tx == TrafficStats.UNSUPPORTED.toLong()) {
            findViewById<TextView>(R.id.network_focus_status).text =
                "FOCUSING: $pkg • this Android build does not expose app traffic counters."
            return
        }
        val now = System.currentTimeMillis()
        val elapsed = ((now - previousNetworkSampleTime).coerceAtLeast(1L)) / 1000.0
        val down = ((rx - previousRxBytes).coerceAtLeast(0L) * 8.0 / elapsed) / 1_000_000.0
        val up = ((tx - previousTxBytes).coerceAtLeast(0L) * 8.0 / elapsed) / 1_000_000.0
        val label = runCatching {
            packageManager.getApplicationLabel(packageManager.getApplicationInfo(pkg, 0)).toString()
        }.getOrDefault(pkg)
        val scope = if (networkFocusDeviceFallback) "DEVICE-WIDE COUNTERS" else "APP TRAFFIC"
        findViewById<TextView>(R.id.network_focus_status).text = String.format(
            java.util.Locale.US,
            "FOCUSING: %s • %s\\n↓ %.2f Mbps   ↑ %.2f Mbps\\nAutomatic focus follows app switches.",
            label, scope, down, up
        )
        previousRxBytes = rx
        previousTxBytes = tx
        previousNetworkSampleTime = now
    }

    private fun applyActiveTheme() {
        val active = gamePrefs.getBoolean("max_fps", false) ||
                gamePrefs.getBoolean("game_mode", false) ||
                !gamePrefs.getString("active_dns_ip", "").isNullOrEmpty()
        val root = findViewById<android.view.View>(R.id.rb22_root) ?: return
        root.setBackgroundResource(if (active) R.drawable.rb22_bg_active else R.drawable.rb22_bg)
        val windowColor = if (active) android.graphics.Color.rgb(12, 0, 2) else android.graphics.Color.rgb(5, 8, 18)
        window.statusBarColor = windowColor
        window.navigationBarColor = windowColor
        fun style(v: android.view.View) {
            if (v.tag == "rb22_card") v.setBackgroundResource(if (active) R.drawable.rb22_card_active else R.drawable.rb22_card)
            if (v is Button) v.setBackgroundResource(if (active) R.drawable.rb22_button_active else R.drawable.rb22_button)
            if (v is android.view.ViewGroup) {
                for (i in 0 until v.childCount) style(v.getChildAt(i))
            }
        }
        style(root)
        findViewById<TextView>(R.id.boost_status)?.setTextColor(if (active) android.graphics.Color.rgb(255, 86, 97) else android.graphics.Color.rgb(184, 255, 231))
        findViewById<TextView>(R.id.dns_status)?.setTextColor(if (active) android.graphics.Color.rgb(255, 86, 97) else android.graphics.Color.rgb(184, 255, 231))
    }

    private fun startGameBooster() {
        gamePrefs.edit().putBoolean("booster_enabled", true).apply()
        findViewById<TextView>(R.id.booster_status).text = "● BOOSTER ON"
        if (savedGames().isEmpty()) {
            Toast.makeText(this, "Booster ON — add a game for automatic detection.", Toast.LENGTH_LONG).show()
        } else {
            startAutoBoostMonitor()
            Toast.makeText(this, "RB22 GAME BOOSTER ON", Toast.LENGTH_SHORT).show()
        }
    }

    private fun stopGameBooster() {
        gamePrefs.edit().putBoolean("booster_enabled", false).apply()
        stopService(Intent(this, GameAutoBoostService::class.java))
        stopService(Intent(this, OverlayService::class.java))
        findViewById<TextView>(R.id.booster_status).text = "○ BOOSTER OFF"
        Toast.makeText(this, "RB22 Game Booster OFF", Toast.LENGTH_SHORT).show()
    }

    private fun requestOverlayWithFps() {
        // RB22 requests only the Android overlay permission.
        // No screen-recording / MediaProjection permission is requested.
        startForegroundCompat(Intent(this, OverlayService::class.java))
    }

    private fun startUltimateEngine() {
        val engine = AdaptivePerformanceEngine(this)
        val status = findViewById<TextView>(R.id.ultimate_status)
        val handler = android.os.Handler(mainLooper)
        val tick = object : Runnable {
            override fun run() {
                status.text = engine.statusText()
                handler.postDelayed(this, 2000)
            }
        }
        handler.post(tick)
    }

    private fun setupBoostModes() {
        val status = findViewById<TextView>(R.id.boost_status)
        findViewById<Button>(R.id.basic).setOnClickListener {
            status.text = "✓ BASIC BOOST APPLIED"
            gamePrefs.edit().putBoolean("max_fps", false).putString("profile", "Balanced").apply()
            applyActiveTheme()
            Toast.makeText(this, "Basic Boost applied", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.advanced).setOnClickListener {
            status.text = "✓ ADVANCED BOOST APPLIED"
            gamePrefs.edit().putBoolean("max_fps", false).putString("profile", "Performance").apply()
            applyActiveTheme()
            Toast.makeText(this, "Advanced Boost applied", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.extreme).setOnClickListener {
            status.text = "✓ MAX FPS PROFILE APPLIED"
            gamePrefs.edit().putBoolean("max_fps", true).putString("profile", "MAX FPS").apply()
            applyActiveTheme()
            Toast.makeText(this, "MAX FPS profile applied — RB22 overhead minimized.", Toast.LENGTH_SHORT).show()
        }
    }

    private fun startForegroundCompat(intent: Intent) {
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent)
            }
        } catch (_: Exception) {
            Toast.makeText(this, "RB22 could not start the background service", Toast.LENGTH_LONG).show()
        }
    }

    private fun activateDns(name: String, ip: String) {
        pendingDnsIp = ip
        val prepare = VpnService.prepare(this)
        if (prepare != null) {
            Toast.makeText(this, "$name selected — Android will ask once for VPN permission.", Toast.LENGTH_LONG).show()
            startActivityForResult(prepare, VPN_REQUEST)
        } else {
            startDnsService(ip)
        }
    }

    private fun disableDns() {
        stopService(Intent(this, DnsVpnService::class.java))
        gamePrefs.edit().remove("active_dns_ip").apply()
        applyActiveTheme()
        findViewById<TextView>(R.id.dns_status).text =
            "RB22 DNS OFF • Android default DNS restored"
        Toast.makeText(this, "RB22 DNS disabled.", Toast.LENGTH_SHORT).show()
    }

    private fun startDnsService(ip: String) {
        gamePrefs.edit().putString("active_dns_ip", ip).apply()
        applyActiveTheme()
        val intent = Intent(this, DnsVpnService::class.java)
            .putExtra(DnsVpnService.EXTRA_DNS_IP, ip)
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) startForegroundService(intent) else startService(intent)
            findViewById<TextView>(R.id.dns_status).text = "DNS: ACTIVE • $ip"
            Toast.makeText(this, "RB22 DNS active: $ip", Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            findViewById<TextView>(R.id.dns_status).text = "DNS: FAILED TO START"
            Toast.makeText(this, "Could not start RB22 DNS.", Toast.LENGTH_LONG).show()
        }
    }

    private fun probeDnsResolver(ip: String): Long? {
        val id = java.security.SecureRandom().nextInt(65536)
        val name = "www.google.com"
        val query = java.io.ByteArrayOutputStream().apply {
            write(byteArrayOf((id ushr 8).toByte(), id.toByte(), 0x01, 0x00, 0x00, 0x01, 0x00, 0x00, 0x00, 0x00, 0x00, 0x00))
            name.split(".").forEach { label ->
                val bytes = label.toByteArray(Charsets.US_ASCII)
                write(bytes.size)
                write(bytes)
            }
            write(0)
            write(byteArrayOf(0x00, 0x01, 0x00, 0x01))
        }.toByteArray()
        return try {
            java.net.DatagramSocket().use { socket ->
                socket.soTimeout = 1400
                socket.connect(InetAddress.getByName(ip), 53)
                val start = System.nanoTime()
                socket.send(DatagramPacket(query, query.size))
                val response = ByteArray(2048)
                val reply = DatagramPacket(response, response.size)
                socket.receive(reply)
                val elapsed = (System.nanoTime() - start) / 1_000_000
                if (reply.length < 12) return null
                val responseId = ((response[0].toInt() and 255) shl 8) or (response[1].toInt() and 255)
                val flags = ((response[2].toInt() and 255) shl 8) or (response[3].toInt() and 255)
                val rcode = flags and 0x000F
                if (responseId == id && (flags and 0x8000) != 0 && rcode == 0) elapsed else null
            }
        } catch (_: Exception) {
            null
        }
    }

    private fun testDns() {
        val status = findViewById<TextView>(R.id.dns_status)
        status.text = "NETWORK OPTIMIZER: Testing DNS response times…"
        // Stop an existing RB22 DNS tunnel so probes go directly to each resolver,
        // rather than being measured through the resolver currently under test.
        stopService(Intent(this, DnsVpnService::class.java))
        gamePrefs.edit().remove("active_dns_ip").apply()
        applyActiveTheme()
        Executors.newSingleThreadExecutor().execute {
            val results = dnsOptions.mapNotNull { (name, pair) ->
                val ms = probeDnsResolver(pair.second)
                if (ms != null) Triple(name, pair.second, ms) else null
            }.sortedBy { it.third }

            runOnUiThread {
                if (results.isEmpty()) {
                    status.text = "NETWORK OPTIMIZER: No DNS resolver responded. Your current network may block direct DNS tests."
                } else {
                    val best = results.first()
                    gamePrefs.edit().putString("best_dns_name", best.first).apply()
                    status.text = "BEST DNS RECOMMENDATION: " + best.first + " • " + best.third + " ms • NOT ACTIVATED"
                    Toast.makeText(
                        this,
                        "Best resolver: " + best.first + " (" + best.third + " ms). Select it manually if you want to test it.",
                        Toast.LENGTH_LONG
                    ).show()
                }
            }
        }
    }

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode == VPN_REQUEST) {
            val ip = pendingDnsIp
            pendingDnsIp = null
            if (resultCode == RESULT_OK && ip != null) {
                startDnsService(ip)
            } else {
                findViewById<TextView>(R.id.dns_status).text = "DNS: VPN permission declined"
                Toast.makeText(this, "DNS was not activated.", Toast.LENGTH_SHORT).show()
            }
        }
    }

    companion object {
        private const val VPN_REQUEST = 2202
    }

    private fun savedGames(): Set<String> =
        gamePrefs.getStringSet("packages", emptySet()) ?: emptySet()

    private fun showGamePicker() {
        val intent = Intent(Intent.ACTION_MAIN).addCategory(Intent.CATEGORY_LAUNCHER)
        val apps = packageManager.queryIntentActivities(intent, 0)
            .filter { it.activityInfo.packageName != packageName }
            .distinctBy { it.activityInfo.packageName }
            .sortedBy { it.loadLabel(packageManager).toString().lowercase() }

        if (apps.isEmpty()) {
            Toast.makeText(this, "No launchable apps found", Toast.LENGTH_SHORT).show()
            return
        }

        val labels = apps.map { it.loadLabel(packageManager).toString() }.toTypedArray()
        val selected = savedGames()
        val checked = BooleanArray(labels.size) { apps[it].activityInfo.packageName in selected }

        AlertDialog.Builder(this)
            .setTitle("Add games to RB22")
            .setMultiChoiceItems(labels, checked) { _, which, isChecked ->
                val updated = savedGames().toMutableSet()
                val pkg = apps[which].activityInfo.packageName
                if (isChecked) updated.add(pkg) else updated.remove(pkg)
                gamePrefs.edit().putStringSet("packages", updated).apply()
                updateGameLibraryText()
                if (updated.isNotEmpty()) startAutoBoostMonitor()
            }
            .setPositiveButton("DONE", null)
            .show()
    }

    private fun showGameLibrary() {
        val games = savedGames()
        if (games.isEmpty()) {
            AlertDialog.Builder(this)
                .setTitle("RB22 Game Library")
                .setMessage("No games added yet. Add a game and RB22 will watch for it.")
                .setPositiveButton("ADD GAME") { _, _ -> showGamePicker() }
                .setNegativeButton("CLOSE", null)
                .show()
            return
        }

        val labels = games.mapNotNull { pkg ->
            runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(pkg, 0)
                ).toString()
            }.getOrNull()
        }.sorted()

        AlertDialog.Builder(this)
            .setTitle("RB22 Game Library")
            .setItems(labels.toTypedArray(), null)
            .setPositiveButton("AUTO BOOST") { _, _ -> startAutoBoostMonitor() }
            .setNeutralButton("EDIT") { _, _ -> showGamePicker() }
            .setNegativeButton("CLOSE", null)
            .show()
    }

    private fun updateGameLibraryText() {
        val view = findViewById<TextView>(R.id.game_library_status)
        val count = savedGames().size
        view.text = if (count == 0) {
            "No games added • Auto Boost is ready"
        } else {
            count.toString() + " game" + if (count == 1) "" else "s" + " saved • Auto Boost monitoring"
        }
    }


    private fun startAutoBoostMonitor() {
        if (savedGames().isEmpty()) {
            updateGameLibraryText()
            return
        }

        if (!hasUsageAccess()) {
            Toast.makeText(
                this,
                "Allow Usage Access so RB22 can detect when a saved game is open.",
                Toast.LENGTH_LONG
            ).show()
            try {
                startActivity(Intent(Settings.ACTION_USAGE_ACCESS_SETTINGS))
            } catch (_: Exception) {
                startActivity(Intent(Settings.ACTION_SETTINGS))
            }
            return
        }

        startForegroundCompat(Intent(this, GameAutoBoostService::class.java))
        updateGameLibraryText()
    }

    private fun setupDeviceRefresh() {
        findViewById<Button>(R.id.device_refresh)?.setOnClickListener { updateStats(); Toast.makeText(this, "Device stats refreshed", Toast.LENGTH_SHORT).show() }
    }

    private fun updateStats() {
        val am = getSystemService(ACTIVITY_SERVICE) as android.app.ActivityManager
        val info = android.app.ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        val used = info.totalMem - info.availMem
        val percent = if (info.totalMem > 0) (used * 100 / info.totalMem).toInt() else 0
        findViewById<TextView>(R.id.ram).text = "RAM     " + percent + "%"

        val battery = registerReceiver(
            null,
            android.content.IntentFilter(Intent.ACTION_BATTERY_CHANGED)
        )
        val temp = battery?.getIntExtra(android.os.BatteryManager.EXTRA_TEMPERATURE, -1) ?: -1
        findViewById<TextView>(R.id.temp).text =
            if (temp >= 0) "TEMP    " + (temp / 10.0) + "°C" else "TEMP    --"
    }
}
