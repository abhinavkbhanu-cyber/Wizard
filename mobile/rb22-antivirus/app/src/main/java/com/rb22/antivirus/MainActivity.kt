package com.rb22.antivirus

import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
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
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.unit.dp
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.security.MessageDigest

private val RbBlack = Color(0xFF101010)
private val RbPanel = Color(0xFF1C1C1C)
private val RbRed = Color(0xFFE53935)
private val RbWhite = Color(0xFFF4F4F4)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(colorScheme = darkColorScheme(primary = RbRed, background = RbBlack, surface = RbPanel, onPrimary = Color.White, onBackground = RbWhite, onSurface = RbWhite)) {
                Surface(modifier = Modifier.fillMaxSize(), color = RbBlack) {
                    AntivirusHome { uri, report -> hashSelectedFile(uri, report) }
                }
            }
        }
    }

    private fun hashSelectedFile(uri: Uri, report: (String) -> Unit) {
        lifecycleScope.launch {
            val result = withContext(Dispatchers.IO) {
                try {
                    contentResolver.openInputStream(uri)?.use { input ->
                        val digest = MessageDigest.getInstance("SHA-256")
                        val buffer = ByteArray(64 * 1024)
                        var total = 0L
                        while (true) {
                            val n = input.read(buffer)
                            if (n < 0) break
                            total += n
                            if (total > 200L * 1024 * 1024) return@withContext "File exceeds this prototype's 200 MB limit. No fingerprint produced."
                            digest.update(buffer, 0, n)
                        }
                        "SHA-256 fingerprint:\n" + digest.digest().joinToString("") { "%02x".format(it) } + "\n\nA fingerprint is not a malware verdict."
                    } ?: "Unable to open selected file."
                } catch (e: Exception) { "Could not inspect file (${e.javaClass.simpleName})." }
            }
            report(result)
        }
    }
}

@Composable
private fun AntivirusHome(onPick: (Uri, (String) -> Unit) -> Unit) {
    var result by remember { mutableStateOf("No file selected yet.") }
    var updateStatus by remember { mutableStateOf("Update service not configured yet") }
    val picker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) { result = "Calculating fingerprint locally…"; onPick(uri) { result = it } }
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
                Text("Automatic updates", style = MaterialTheme.typography.titleLarge)
                Text(updateStatus, color = Color.LightGray)
                OutlinedButton(onClick = { updateStatus = "Not active: authenticated release metadata and a distribution channel must be configured." }) { Text("Update status") }
            }
        }
        Spacer(Modifier.height(2.dp))
        Text("Alpha prototype • Android 9+ • Selected files stay on device", color = Color.Gray, style = MaterialTheme.typography.bodySmall)
    }
}
