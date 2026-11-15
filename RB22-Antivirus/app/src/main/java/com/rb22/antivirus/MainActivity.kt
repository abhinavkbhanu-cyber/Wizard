package com.rb22.antivirus

import android.app.Activity
import android.graphics.Color
import android.os.Bundle
import android.view.ViewGroup
import android.widget.Button
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.io.File
import java.io.FileInputStream
import java.security.MessageDigest
import java.util.concurrent.Executors

private data class ScanItem(
    val appName: String,
    val packageName: String,
    val sha256: String?,
    val error: String? = null,
    val knownThreat: Boolean = false
)

private data class ScanReport(
    val checked: Int,
    val unreadable: Int,
    val matches: List<ScanItem>,
    val items: List<ScanItem>
)

private object LocalThreatDatabase {
    fun load(activity: Activity): Set<String> = runCatching {
        val raw = activity.assets.open("threat-indicators.json")
            .bufferedReader().use { it.readText() }
        val json = org.json.JSONObject(raw)
        require(json.optInt("schemaVersion") == 1)
        val entries = json.getJSONArray("indicators")
        buildSet {
            for (index in 0 until entries.length()) {
                val hash = entries.optString(index).trim().lowercase()
                if (hash.matches(Regex("[a-f0-9]{64}"))) add(hash)
            }
        }
    }.getOrDefault(emptySet())
}

private class ApkScanner(private val activity: Activity) {
    fun scan(): ScanReport {
        val indicators = LocalThreatDatabase.load(activity)
        val pm = activity.packageManager
        val apps = pm.getInstalledApplications(0)
        val items = ArrayList<ScanItem>(apps.size)

        for (app in apps) {
            val name = runCatching { pm.getApplicationLabel(app).toString() }
                .getOrDefault(app.packageName)
            val result = runCatching {
                val path = app.sourceDir ?: error("APK path unavailable")
                hashFile(File(path))
            }
            val hash = result.getOrNull()
            items += ScanItem(
                appName = name,
                packageName = app.packageName,
                sha256 = hash,
                error = result.exceptionOrNull()?.message,
                knownThreat = hash != null && indicators.contains(hash)
            )
        }

        return ScanReport(
            checked = items.count { it.sha256 != null },
            unreadable = items.count { it.sha256 == null },
            matches = items.filter { it.knownThreat },
            items = items.sortedBy { it.appName.lowercase() }
        )
    }

    private fun hashFile(file: File): String {
        require(file.isFile && file.canRead()) { "APK could not be read" }
        val digest = MessageDigest.getInstance("SHA-256")
        FileInputStream(file).use { input ->
            val buffer = ByteArray(64 * 1024)
            while (true) {
                val count = input.read(buffer)
                if (count < 0) break
                digest.update(buffer, 0, count)
            }
        }
        return digest.digest().joinToString("") { "%02x".format(it) }
    }
}

class MainActivity : Activity() {
    private val executor = Executors.newSingleThreadExecutor()
    private lateinit var scanButton: Button
    private lateinit var statusText: TextView
    private lateinit var resultText: TextView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        window.statusBarColor = Color.rgb(8, 10, 16)
        window.navigationBarColor = Color.rgb(8, 10, 16)

        val content = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(24, 32, 24, 24)
            setBackgroundColor(Color.rgb(8, 10, 16))
        }
        content.addView(label("RB22", 15f, Color.rgb(255, 75, 75), true))
        content.addView(label("ANTIVIRUS", 28f, Color.WHITE, true))
        content.addView(label("Local APK security scanner • Alpha 0.1.0", 14f, Color.LTGRAY))
        content.addView(spacer(24))

        statusText = label(
            "Protection status: manual scan only\nNo continuous monitoring is active.",
            15f, Color.rgb(220, 225, 235)
        )
        content.addView(statusText)
        content.addView(spacer(18))

        scanButton = Button(this).apply {
            text = "SCAN INSTALLED APPS"
            isAllCaps = false
            setTextColor(Color.WHITE)
            setBackgroundColor(Color.rgb(180, 35, 48))
        }
        content.addView(scanButton, LinearLayout.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        ))
        content.addView(spacer(18))
        content.addView(label("Scan results", 19f, Color.WHITE, true))
        resultText = label(
            "No scan has been run yet.\n\nThe scanner calculates SHA-256 fingerprints for readable installed APKs and compares them with the bundled local indicator list.",
            14f, Color.rgb(205, 210, 220)
        )
        content.addView(resultText)
        setContentView(ScrollView(this).apply { addView(content) })
        scanButton.setOnClickListener { startScan() }
    }

    private fun startScan() {
        scanButton.isEnabled = false
        statusText.text = "Scanning installed applications…\nKeep RB22 open until the scan finishes."
        resultText.text = "Calculating APK fingerprints. This may take a little while."
        executor.execute {
            val outcome = runCatching { ApkScanner(this).scan() }
            runOnUiThread {
                scanButton.isEnabled = true
                val report = outcome.getOrNull()
                if (report == null) {
                    statusText.text = "Scan could not complete."
                    resultText.text = outcome.exceptionOrNull()?.message ?: "Unknown scan error"
                    return@runOnUiThread
                }

                statusText.text = "Scan complete • ${report.checked} APKs fingerprinted"
                resultText.text = buildString {
                    append("Known signature matches: ${report.matches.size}\n")
                    append("Unreadable APKs: ${report.unreadable}\n\n")
                    if (report.matches.isEmpty()) {
                        append("No matches were found in the bundled local signature list. This is not proof that the device is malware-free. The initial database is empty until verified indicators are added.\n")
                    } else {
                        append("MATCHES — review immediately\n")
                        report.matches.forEach {
                            append("• ${it.appName}\n  ${it.packageName}\n  SHA-256: ${it.sha256}\n\n")
                        }
                    }
                    if (report.unreadable > 0) append("\nSome APK files could not be read. They were not treated as clean.")
                    append("\n\nScanned applications\n")
                    report.items.forEach {
                        append("• ${it.appName} — ")
                        append(if (it.sha256 == null) "Not scanned" else if (it.knownThreat) "Signature match" else "No local match")
                        append("\n  ${it.packageName}\n")
                    }
                }
            }
        }
    }

    private fun label(value: String, size: Float, color: Int, bold: Boolean = false) =
        TextView(this).apply {
            text = value
            textSize = size
            setTextColor(color)
            if (bold) setTypeface(typeface, android.graphics.Typeface.BOLD)
            setPadding(0, 4, 0, 4)
        }

    private fun spacer(height: Int) = TextView(this).apply {
        text = ""
        layoutParams = LinearLayout.LayoutParams(1, height)
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }
}
