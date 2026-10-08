package com.rb22

import android.app.AppOpsManager
import android.content.Context
import android.content.Intent
import android.net.Uri
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
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    private val dnsOptions = listOf(
        "Cloudflare" to "one.one.one.one",
        "Google" to "dns.google",
        "Quad9" to "dns.quad9.net",
        "AdGuard" to "dns.adguard-dns.com",
        "OpenDNS" to "dns.opendns.com"
    )

    private val gamingEndpoints = listOf("Cloudflare" to "1.1.1.1", "Google" to "8.8.8.8", "Quad9" to "9.9.9.9")

    private val gamePrefs by lazy {
        getSharedPreferences("rb22_games", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updateStats()
        setupDeviceRefresh()
        setupBoostModes()
        setupMobileControls()
        startUltimateEngine()

        findViewById<Button>(R.id.start_booster).setOnClickListener { startGameBooster() }
        findViewById<Button>(R.id.stop_booster).setOnClickListener { stopGameBooster() }
        findViewById<TextView>(R.id.booster_status).text = if (gamePrefs.getBoolean("booster_enabled", false)) "● BOOSTER ON" else "○ BOOSTER OFF"

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

        val dnsButtons = listOf(
            R.id.dns_cloudflare to dnsOptions[0],
            R.id.dns_google to dnsOptions[1],
            R.id.dns_quad9 to dnsOptions[2],
            R.id.dns_adguard to dnsOptions[3],
            R.id.dns_opendns to dnsOptions[4]
        )
        dnsButtons.forEach { (id, dns) ->
            findViewById<Button>(id).setOnClickListener {
                openPrivateDns(dns.first, dns.second)
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



    private fun setupMobileControls() {
        findViewById<Button>(R.id.mobile_game_mode).setOnClickListener {
            gamePrefs.edit().putBoolean("game_mode", true).putBoolean("max_fps", true).putString("profile", "MAX FPS").apply()
            findViewById<TextView>(R.id.boost_status).text = "✓ MAX FPS MODE ACTIVE • LOW OVERHEAD"
            Toast.makeText(this, "MAX FPS mode active — RB22 is minimizing its own background work.", Toast.LENGTH_LONG).show()
        }

        findViewById<Button>(R.id.mobile_tools).setOnClickListener { showExtraTools() }

        findViewById<Button>(R.id.mobile_charging).setOnClickListener {
            val enabled = !gamePrefs.getBoolean("charging_mode", false)
            gamePrefs.edit().putBoolean("charging_mode", enabled).apply()
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
            Toast.makeText(this, "Basic Boost applied", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.advanced).setOnClickListener {
            status.text = "✓ ADVANCED BOOST APPLIED"
            gamePrefs.edit().putBoolean("max_fps", false).putString("profile", "Performance").apply()
            Toast.makeText(this, "Advanced Boost applied", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.extreme).setOnClickListener {
            status.text = "✓ MAX FPS PROFILE APPLIED"
            gamePrefs.edit().putBoolean("max_fps", true).putString("profile", "MAX FPS").apply()
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

    private fun openPrivateDns(name: String, hostname: String) {
        Toast.makeText(
            this,
            "$name selected: $hostname. Set this hostname in Private DNS.",
            Toast.LENGTH_LONG
        ).show()
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.P) {
                startActivity(Intent("android.settings.PRIVATE_DNS_SETTINGS"))
            } else {
                startActivity(Intent(Settings.ACTION_WIRELESS_SETTINGS))
            }
        } catch (_: Exception) {
            startActivity(Intent(Settings.ACTION_SETTINGS))
        }
    }

    private fun testDns() {
        val status = findViewById<TextView>(R.id.dns_status)
        status.text = "DNS: Testing 5 resolvers…"
        Executors.newSingleThreadExecutor().execute {
            val results = dnsOptions.mapNotNull { (name, host) ->
                val start = System.nanoTime()
                val ok = try { InetAddress.getByName(host); true } catch (_: Exception) { false }
                val ms = (System.nanoTime() - start) / 1_000_000
                if (ok) name to ms else null
            }.sortedBy { it.second }

            runOnUiThread {
                if (results.isEmpty()) {
                    status.text = "DNS: Test failed — check your connection."
                } else {
                    val best = results.first()
                    gamePrefs.edit().putString("best_dns_name", best.first).apply()
                    status.text = "BEST DNS: " + best.first + "  " + best.second + " ms"
                    openPrivateDns(best.first, dnsOptions.first { it.first == best.first }.second)
                }
            }
        }
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

    private fun hasUsageAccess(): Boolean {
        val appOps = getSystemService(APP_OPS_SERVICE) as AppOpsManager
        @Suppress("DEPRECATION")
        val mode = appOps.checkOpNoThrow(
            AppOpsManager.OPSTR_GET_USAGE_STATS,
            android.os.Process.myUid(),
            packageName
        )
        return mode == AppOpsManager.MODE_ALLOWED
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
