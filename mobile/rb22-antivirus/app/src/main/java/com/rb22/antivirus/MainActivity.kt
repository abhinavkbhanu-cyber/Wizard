package com.rb22.antivirus

import android.Manifest
import android.content.Intent
import android.net.Uri
import android.net.VpnService
import android.os.Build
import android.os.Bundle
import android.content.pm.PackageManager
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.compose.foundation.Canvas
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
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
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
        val vpnPermissionLauncher = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result ->
            if (result.resultCode == RESULT_OK) {
                ContextCompat.startForegroundService(this, Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START))
            }
        }
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = RbRed, background = RbBlack, surface = RbPanel, onPrimary = Color.White, onBackground = RbWhite, onSurface = RbWhite)) {
                Surface(modifier = Modifier.fillMaxSize(), color = RbBlack) {
                    AntivirusHome(
                        onScanApk = { uri, report -> scanSelectedFile(uri, report) },
                        onScanInventory = { report -> scanInstalledApps(report) },
                        onProtectionChanged = { enabled ->
                            getSharedPreferences(PROTECTION_PREFS, MODE_PRIVATE).edit().putBoolean(PROTECTION_ENABLED, enabled).apply()
                        },
                        onToggleAdBlock = { enabled ->
                            if (enabled) {
                                val permissionIntent = VpnService.prepare(this)
                                if (permissionIntent == null) {
                                    ContextCompat.startForegroundService(this, Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_START))
                                } else {
                                    vpnPermissionLauncher.launch(permissionIntent)
                                }
                            } else {
                                startService(Intent(this, AdBlockVpnService::class.java).setAction(AdBlockVpnService.ACTION_STOP))
                            }
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
                    val reports = ApkScanner.scanInstalledInventory(this@MainActivity) { done, total, appName ->
                        runOnUiThread { report("Scanning installed apps: $done/$total\\n$appName") }
                    }
                    val flagged = reports.filter { it.verdict == ScanVerdict.SUSPICIOUS }
                    val unable = reports.count { it.verdict == ScanVerdict.UNABLE_TO_SCAN }
                    buildString {
                        appendLine("FULL INSTALLED-APP SCAN COMPLETE")
                        appendLine("Apps scanned: ${reports.size}")
                        appendLine("Apps needing review: ${flagged.size}")
                        appendLine("Apps Android did not allow RB22 to inspect: $unable")
                        appendLine()
                        if (flagged.isEmpty()) appendLine("No known heuristic indicators were detected in the inspected apps. This does NOT prove they are safe.")
                        flagged.take(20).forEach { item ->
                            appendLine("• ${item.appName} (${item.packageName})")
                            item.findings.take(4).forEach { appendLine("  - ${it.detail}") }
                        }
                        appendLine()
                        append("RB22 inspected accessible APK metadata and bounded DEX content sequentially. Findings are heuristic; unknown malware can be missed and legitimate apps can be flagged.")
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
    onToggleAdBlock: (Boolean) -> Unit,
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
        RB22ShieldLogo()
        Text("RB22 ANTIVIRUS", color = RbWhite, style = MaterialTheme.typography.headlineLarge)
        Text("On-device app checks • Privacy first", color = Color.LightGray, fontSize = 14.sp)
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
                Text("Full device app scan", style = MaterialTheme.typography.titleLarge)
                Text("Scan all visible installed apps in one run. RB22 checks permissions and bounded APK code indicators sequentially, then lists apps that need review.")
                OutlinedButton(onClick = { scanResult = "Reviewing installed apps…"; onScanInventory { scanResult = it } }) { Text("Scan installed apps") }
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RbPanel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Ad & tracker blocker", style = MaterialTheme.typography.titleLarge)
                Text("Optional DNS filtering blocks a starter list of known ad and tracking domains. Android will ask permission to create a local VPN connection.")
                Button(onClick = { onToggleAdBlock(true) }) { Text("Enable DNS blocker") }
                OutlinedButton(onClick = { onToggleAdBlock(false) }) { Text("Disable DNS blocker") }
                Text("Limits: DNS filtering cannot reliably remove YouTube video ads or ads served from the same domain as wanted content. It may conflict with another VPN and some apps or sites may need an allowlist.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
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


@Composable
private fun RB22ShieldLogo() {
    Canvas(Modifier.fillMaxWidth().height(92.dp)) {
        val shield = Path().apply {
            moveTo(size.width * 0.5f, size.height * 0.04f)
            lineTo(size.width * 0.79f, size.height * 0.16f)
            lineTo(size.width * 0.76f, size.height * 0.57f)
            quadraticTo(size.width * 0.72f, size.height * 0.82f, size.width * 0.5f, size.height * 0.96f)
            quadraticTo(size.width * 0.28f, size.height * 0.82f, size.width * 0.24f, size.height * 0.57f)
            lineTo(size.width * 0.21f, size.height * 0.16f)
            close()
        }
        drawPath(shield, color = RbRed)
        val check = Path().apply {
            moveTo(size.width * 0.39f, size.height * 0.48f)
            lineTo(size.width * 0.47f, size.height * 0.58f)
            lineTo(size.width * 0.64f, size.height * 0.37f)
        }
        drawPath(check, color = Color.White, style = Stroke(width = 5.dp.toPx(), cap = androidx.compose.ui.graphics.StrokeCap.Round, join = androidx.compose.ui.graphics.StrokeJoin.Round))
        drawPath(shield, color = Color.White.copy(alpha = 0.7f), style = Stroke(width = 1.5.dp.toPx()))
    }
}
