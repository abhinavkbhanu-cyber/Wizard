package com.rb22.antivirus

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.darkColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import java.io.File
import java.net.HttpURLConnection
import java.net.URL

private const val RELEASE_TAG_PREFIX = "rb22-antivirus-v"
private const val RELEASES_API = "https://api.github.com/repos/abhinavkbhanu-cyber/Wizard/releases?per_page=100"
private const val RELEASES_PAGE = "https://github.com/abhinavkbhanu-cyber/Wizard/releases"
private val RbBlack = Color(0xFF101010)
private val RbPanel = Color(0xFF1C1C1C)
private val RbRed = Color(0xFFE53935)
private val RbWhite = Color(0xFFF4F4F4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        UpdateCheckWorker.schedule(this)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = RbRed, background = RbBlack, surface = RbPanel, onPrimary = Color.White, onBackground = RbWhite, onSurface = RbWhite)) {
                Surface(modifier = Modifier.fillMaxSize(), color = RbBlack) {
                    AntivirusHome(
                        onScanApk = { uri, report -> scanSelectedFile(uri, report) },
                        onScanInventory = { report -> scanInstalledApps(report) },
                        onProtectionChanged = { enabled ->
                            getSharedPreferences(PROTECTION_PREFS, MODE_PRIVATE).edit().putBoolean(PROTECTION_ENABLED, enabled).apply()
                        },
                        onCheckUpdates = { report -> checkForUpdates(report) },
                        onOpenReleases = { openReleases() }
                    )
                }
            }
        }
    }

    private fun scanSelectedFile(uri: Uri, report: (String) -> Unit) {
        lifecycleScope.launch {
            report("Scanning APK locally…")
            val result = withContext(Dispatchers.IO) {
                val temp = File(cacheDir, "rb22-scan-${System.nanoTime()}.apk")
                try {
                    val input = contentResolver.openInputStream(uri) ?: return@withContext "Unable to open selected file."
                    input.use { source ->
                        temp.outputStream().use { output ->
                            val buffer = ByteArray(64 * 1024)
                            var total = 0L
                            while (true) {
                                val count = source.read(buffer)
                                if (count < 0) break
                                if (count == 0) continue
                                total += count
                                if (total > ApkFingerprint.MAX_FILE_BYTES) return@withContext "File exceeds the 200 MiB scan limit."
                                output.write(buffer, 0, count)
                            }
                        }
                    }
                    formatReport(ApkScanner.scanSelectedApk(this@MainActivity, temp))
                } catch (e: Exception) {
                    "Unable to scan selected file (${e.javaClass.simpleName})."
                } finally {
                    temp.delete()
                }
            }
            report(result)
        }
    }

    private fun scanInstalledApps(report: (String) -> Unit) {
        lifecycleScope.launch {
            report("Reviewing installed-app permissions and metadata…")
            val result = withContext(Dispatchers.IO) {
                try {
                    val reports = ApkScanner.scanInstalledInventory(this@MainActivity)
                    val flagged = reports.filter { it.verdict == ScanVerdict.SUSPICIOUS }
                    buildString {
                        appendLine("Installed apps reviewed: ${reports.size}")
                        appendLine("Apps with indicators to review: ${flagged.size}")
                        appendLine()
                        if (flagged.isEmpty()) appendLine("No known heuristic indicators were found in accessible app metadata. This is not proof that installed apps are safe.")
                        flagged.take(12).forEach { item ->
                            appendLine("• ${item.appName} (${item.packageName})")
                            item.findings.take(3).forEach { appendLine("  - ${it.detail}") }
                        }
                        appendLine()
                        append("Inventory mode checks permissions and app metadata only; it does not inspect every app's code.")
                    }
                } catch (e: Exception) {
                    "Installed-app inventory scan failed (${e.javaClass.simpleName})."
                }
            }
            report(result)
        }
    }

    private fun formatReport(scan: ScanReport): String = buildString {
        appendLine("Result: ${when (scan.verdict) {
            ScanVerdict.SUSPICIOUS -> "SUSPICIOUS — review required"
            ScanVerdict.NO_KNOWN_INDICATORS -> "No known indicators found"
            ScanVerdict.UNABLE_TO_SCAN -> "Unable to scan"
        }}")
        appendLine("App: ${scan.appName}")
        scan.packageName?.let { appendLine("Package: $it") }
        scan.sha256?.let { appendLine("SHA-256: $it") }
        if (scan.findings.isNotEmpty()) {
            appendLine()
            appendLine("Indicators:")
            scan.findings.forEach { appendLine("• ${it.title}: ${it.detail}") }
        }
        appendLine()
        scan.notes.forEach { appendLine("Note: $it") }
        append("RB22 does not block or uninstall apps.")
    }

    private fun checkForUpdates(report: (String) -> Unit) {
        lifecycleScope.launch {
            report("Checking the official RB22 Antivirus release feed…")
            val result = withContext(Dispatchers.IO) {
                try {
                    val connection = (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                        connectTimeout = 7000
                        readTimeout = 7000
                        requestMethod = "GET"
                        setRequestProperty("Accept", "application/vnd.github+json")
                        setRequestProperty("User-Agent", "RB22-Antivirus-Android")
                    }
                    try {
                        if (connection.responseCode != HttpURLConnection.HTTP_OK) return@withContext "Update check unavailable (HTTP ${connection.responseCode}). Try again later."
                        val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
                        var newestVersion: String? = null
                        for (i in 0 until releases.length()) {
                            val release = releases.getJSONObject(i)
                            val tag = release.optString("tag_name")
                            if (tag.startsWith(RELEASE_TAG_PREFIX) && !release.optBoolean("draft", true)) {
                                newestVersion = tag.removePrefix(RELEASE_TAG_PREFIX)
                                break
                            }
                        }
                        when {
                            newestVersion == null -> "No Antivirus release is published yet. This is a developer alpha."
                            newestVersion == BuildConfig.VERSION_NAME -> "You're using the latest published Antivirus version (${BuildConfig.VERSION_NAME})."
                            compareVersions(newestVersion, BuildConfig.VERSION_NAME) > 0 -> "Update available: $newestVersion. Open Releases to review and install it."
                            else -> "Installed version: ${BuildConfig.VERSION_NAME}. The release feed is older; no downgrade offered."
                        }
                    } finally { connection.disconnect() }
                } catch (_: Exception) { "Couldn't check updates. Check your internet connection and try again." }
            }
            report(result)
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        fun parts(value: String): List<Int> = value.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(left); val b = parts(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (difference != 0) return difference.compareTo(0)
        }
        val leftStable = '-' !in left; val rightStable = '-' !in right
        return when { leftStable == rightStable -> 0; leftStable -> 1; else -> -1 }
    }

    private fun openReleases() = startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)))
}

@Composable
private fun AntivirusHome(
    onScanApk: (Uri, (String) -> Unit) -> Unit,
    onScanInventory: ((String) -> Unit) -> Unit,
    onProtectionChanged: (Boolean) -> Unit,
    onCheckUpdates: ((String) -> Unit) -> Unit,
    onOpenReleases: () -> Unit
) {
    val context = LocalContext.current
    var scanResult by remember { mutableStateOf("No scan run yet.") }
    var updateStatus by remember { mutableStateOf("Checking for updates…") }
    var protectionEnabled by remember {
        mutableStateOf(context.getSharedPreferences(PROTECTION_PREFS, android.content.Context.MODE_PRIVATE).getBoolean(PROTECTION_ENABLED, true))
    }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { scanResult = "Scanning selected APK locally…"; onScanApk(uri) { scanResult = it } }
    }
    val notificationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onCheckUpdates { updateStatus = it }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("RB22", color = RbRed, style = MaterialTheme.typography.labelLarge)
        Text("ANTIVIRUS", color = RbWhite, style = MaterialTheme.typography.headlineLarge)
        Text("On-device app checks • Privacy first", color = Color.LightGray)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = RbPanel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("APK security scan", style = MaterialTheme.typography.titleLarge)
                Text("Inspects APK permissions, selected DEX indicators, and a local SHA-256 fingerprint. Results are heuristic warnings, not a definitive malware verdict.")
                Button(onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }) { Text("Choose APK and scan") }
                Text(scanResult, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RbPanel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Installed-app review", style = MaterialTheme.typography.titleLarge)
                Text("Review installed app permissions and metadata. Inventory mode does not inspect all app code.")
                OutlinedButton(onClick = { scanResult = "Reviewing installed apps…"; onScanInventory { scanResult = it } }) { Text("Scan installed apps") }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RbPanel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Real-time install/update alerts", style = MaterialTheme.typography.titleLarge)
                Text(if (protectionEnabled) "Enabled: when Android reports an app install or update, RB22 queues a background heuristic check and can notify you about indicators." else "Disabled: RB22 will not queue install/update checks.")
                androidx.compose.foundation.layout.Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(if (protectionEnabled) "Protection alerts ON" else "Protection alerts OFF")
                    Switch(checked = protectionEnabled, onCheckedChange = {
                        protectionEnabled = it
                        onProtectionChanged(it)
                    })
                }
                Text("This is not system-wide real-time antivirus: it cannot block installs, inspect app behavior continuously, or guarantee every event is received. Keep Google Play Protect enabled.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RbPanel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("RB22 updates", style = MaterialTheme.typography.titleLarge)
                Text(updateStatus, color = Color.LightGray)
                OutlinedButton(onClick = { onCheckUpdates { updateStatus = it } }) { Text("Check now") }
                OutlinedButton(onClick = onOpenReleases) { Text("Open official releases") }
                Text("Background update notifications are separate from malware detection. Installation always requires Android's supported user-confirmed flow.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Developer alpha • Android 9+ • Scans stay on device • False positives and missed threats are possible", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}
