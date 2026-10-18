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
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.CardDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
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
                        onPick = { uri, report -> hashSelectedFile(uri, report) },
                        onCheckUpdates = { report -> checkForUpdates(report) },
                        onOpenReleases = { openReleases() }
                    )
                }
            }
        }
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
                        if (connection.responseCode != HttpURLConnection.HTTP_OK) {
                            return@withContext "Update check unavailable (HTTP ${connection.responseCode}). Try again later."
                        }
                        val body = connection.inputStream.bufferedReader().use { it.readText() }
                        val releases = JSONArray(body)
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
                            newestVersion == null -> "No Antivirus release is published yet. This app is still an alpha prototype."
                            newestVersion == BuildConfig.VERSION_NAME -> "You're using the latest published Antivirus version (${BuildConfig.VERSION_NAME})."
                            compareVersions(newestVersion, BuildConfig.VERSION_NAME) > 0 ->
                                "Update available: $newestVersion. Open Releases to review and install it."
                            else -> "Installed version: ${BuildConfig.VERSION_NAME}. The release feed is older; no downgrade offered."
                        }
                    } finally {
                        connection.disconnect()
                    }
                } catch (_: Exception) {
                    "Couldn't check updates. Check your internet connection and try again."
                }
            }
            report(result)
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        fun parts(value: String): List<Int> =
            value.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(left)
        val b = parts(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = (a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 })
            if (difference != 0) return difference.sign()
        }
        val leftStable = '-' !in left
        val rightStable = '-' !in right
        return when {
            leftStable == rightStable -> 0
            leftStable -> 1
            else -> -1
        }
    }

    private fun Int.sign(): Int = when {
        this < 0 -> -1
        this > 0 -> 1
        else -> 0
    }

    private fun openReleases() {
        startActivity(Intent(Intent.ACTION_VIEW, Uri.parse(RELEASES_PAGE)))
    }

    private fun hashSelectedFile(uri: Uri, report: (String) -> Unit) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        when (val fingerprint = ApkFingerprint.calculate(input)) {
                            is FingerprintResult.Success ->
                                "SHA-256 fingerprint:\n${fingerprint.sha256}\n\nBytes read: ${fingerprint.bytesRead}\n\nA fingerprint is not a malware verdict."
                            is FingerprintResult.TooLarge ->
                                "File exceeds this prototype's ${fingerprint.limitBytes / (1024 * 1024)} MB limit. No fingerprint produced."
                        }
                    } ?: "Unable to open selected file."
                } catch (e: Exception) {
                    "Could not inspect file (${e.javaClass.simpleName})."
                }
            }
            report(result)
        }
    }
}

@Composable
private fun AntivirusHome(
    onPick: (Uri, (String) -> Unit) -> Unit,
    onCheckUpdates: ((String) -> Unit) -> Unit,
    onOpenReleases: () -> Unit
) {
    var result by remember { mutableStateOf("No file selected yet.") }
    var updateStatus by remember { mutableStateOf("Checking for updates…") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { result = "Calculating fingerprint locally…"; onPick(uri) { result = it } }
    }
    val context = LocalContext.current
    val notificationPermissionLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.RequestPermission()
    ) { }
    LaunchedEffect(Unit) {
        if (Build.VERSION.SDK_INT >= 33 &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            notificationPermissionLauncher.launch(Manifest.permission.POST_NOTIFICATIONS)
        }
        onCheckUpdates { updateStatus = it }
    }
    Column(Modifier.fillMaxSize().padding(20.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        Text("RB22", color = RbRed, style = MaterialTheme.typography.labelLarge)
        Text("ANTIVIRUS", color = RbWhite, style = MaterialTheme.typography.headlineLarge)
        Text("Free security. Privacy first.", color = Color.LightGray)
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = RbPanel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("APK file inspection", style = MaterialTheme.typography.titleLarge)
                Text("Choose an APK to calculate its SHA-256 fingerprint on this device. This prototype does not yet detect malware.")
                Button(onClick = { picker.launch(arrayOf("application/vnd.android.package-archive", "application/octet-stream")) }) { Text("Choose APK") }
                Text(result, style = MaterialTheme.typography.bodySmall)
            }
        }
        Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = RbPanel)) {
            Column(Modifier.padding(18.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Automatic update checks", style = MaterialTheme.typography.titleLarge)
                Text(updateStatus, color = Color.LightGray)
                OutlinedButton(onClick = { onCheckUpdates { updateStatus = it } }) { Text("Check now") }
                OutlinedButton(onClick = onOpenReleases) { Text("Open official releases") }
                Text("Background checks can notify you when a newer release is published. Downloads are not installed silently: Android requires supported installer confirmation. Update authenticity verification and automatic APK download are not implemented yet.", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Alpha prototype • Android 9+ • Selected files stay on device", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}
