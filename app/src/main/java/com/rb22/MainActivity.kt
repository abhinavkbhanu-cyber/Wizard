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
import androidx.appcompat.app.AlertDialog
import androidx.appcompat.app.AppCompatActivity
import java.net.InetAddress
import java.util.concurrent.Executors

class MainActivity : AppCompatActivity() {
    companion object { private const val REQUEST_SCREEN_CAPTURE = 2201 }
    private var waitingForOverlay = false
    private val dnsOptions = listOf(
        "Cloudflare" to "one.one.one.one",
        "Google" to "dns.google",
        "Quad9" to "dns.quad9.net",
        "AdGuard" to "dns.adguard-dns.com",
        "OpenDNS" to "dns.opendns.com"
    )

    private val gamePrefs by lazy {
        getSharedPreferences("rb22_games", Context.MODE_PRIVATE)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)
        updateStats()
        setupBoostModes()

        findViewById<TextView>(R.id.overlay).setOnClickListener {
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

        if (savedGames().isNotEmpty()) startAutoBoostMonitor()
    }

    private fun requestOverlayWithFps() {
        waitingForOverlay = true
        val manager = getSystemService(android.media.projection.MediaProjectionManager::class.java)
        startActivityForResult(manager.createScreenCaptureIntent(), REQUEST_SCREEN_CAPTURE)
    }

    @Deprecated("Activity result API kept compatible with the existing RB22 project")
    override fun onActivityResult(requestCode: Int, resultCode: Int, data: android.content.Intent?) {
        super.onActivityResult(requestCode, resultCode, data)
        if (requestCode != REQUEST_SCREEN_CAPTURE) return
        waitingForOverlay = false
        val intent = Intent(this, OverlayService::class.java)
        if (resultCode == RESULT_OK && data != null) {
            intent.putExtra(ScreenFpsMonitor.EXTRA_RESULT_CODE, resultCode)
            intent.putExtra(ScreenFpsMonitor.EXTRA_RESULT_DATA, data)
        }
        startForegroundCompat(intent)
    }
    private fun setupBoostModes() {
        val status = findViewById<TextView>(R.id.boost_status)
        findViewById<Button>(R.id.basic).setOnClickListener {
            status.text = "✓ BASIC BOOST APPLIED"
            gamePrefs.edit().putString("profile", "Basic").apply()
            Toast.makeText(this, "Basic Boost applied", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.advanced).setOnClickListener {
            status.text = "✓ ADVANCED BOOST APPLIED"
            gamePrefs.edit().putString("profile", "Advanced").apply()
            Toast.makeText(this, "Advanced Boost applied", Toast.LENGTH_SHORT).show()
        }
        findViewById<Button>(R.id.extreme).setOnClickListener {
            status.text = "✓ TURBO BOOST APPLIED"
            gamePrefs.edit().putString("profile", "Turbo").apply()
            Toast.makeText(this, "Turbo Boost applied", Toast.LENGTH_SHORT).show()
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
                    Toast.makeText(
                        this,
                        "RB22 recommends " + best.first + " for this connection",
                        Toast.LENGTH_LONG
                    ).show()
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
