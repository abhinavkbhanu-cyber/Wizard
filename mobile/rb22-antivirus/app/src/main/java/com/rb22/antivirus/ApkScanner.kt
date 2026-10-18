package com.rb22.antivirus

import android.content.Context
import android.content.pm.PackageInfo
import android.content.pm.PackageManager
import android.os.Build
import java.io.File
import java.io.FileInputStream
import java.util.zip.ZipFile

internal enum class ScanVerdict { SUSPICIOUS, NO_KNOWN_INDICATORS, UNABLE_TO_SCAN }
internal data class ScanFinding(val title: String, val detail: String, val points: Int)
internal data class ScanReport(val appName: String, val packageName: String?, val sha256: String?, val verdict: ScanVerdict, val findings: List<ScanFinding>, val notes: List<String> = emptyList())

/** Local static triage, not a full antivirus engine. Heuristics can miss malware and flag legitimate apps. */
internal object ApkScanner {
    private const val MAX_DEX_BYTES = 32L * 1024 * 1024
    private val sensitivePermissions = mapOf(
        "android.permission.READ_SMS" to "Can read SMS messages",
        "android.permission.RECEIVE_SMS" to "Can receive SMS messages",
        "android.permission.SEND_SMS" to "Can send SMS messages",
        "android.permission.READ_CALL_LOG" to "Can read call history",
        "android.permission.WRITE_CALL_LOG" to "Can modify call history",
        "android.permission.REQUEST_INSTALL_PACKAGES" to "Can request installation of other packages",
        "android.permission.SYSTEM_ALERT_WINDOW" to "Can request drawing over other apps",
        "android.permission.BIND_ACCESSIBILITY_SERVICE" to "Accessibility-service capability requested"
    )

    fun scanSelectedApk(context: Context, apk: File): ScanReport {
        if (!apk.isFile || apk.length() <= 0L || apk.length() > ApkFingerprint.MAX_FILE_BYTES) {
            return ScanReport(apk.name, null, null, ScanVerdict.UNABLE_TO_SCAN, emptyList(), listOf("APK is missing, empty, or exceeds the 200 MiB scan limit."))
        }
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        val parsed = pm.getPackageArchiveInfo(apk.absolutePath, flags)
            ?: return ScanReport(apk.name, null, hash(apk), ScanVerdict.UNABLE_TO_SCAN, emptyList(), listOf("Android could not parse this file as an APK."))
        parsed.applicationInfo?.sourceDir = apk.absolutePath
        parsed.applicationInfo?.publicSourceDir = apk.absolutePath
        val label = try { parsed.applicationInfo?.loadLabel(pm)?.toString()?.takeIf { it.isNotBlank() } ?: apk.name } catch (_: Exception) { apk.name }
        return analyze(apk, parsed, label, inspectDex = true)
    }

    fun scanInstalledPackage(context: Context, packageName: String): ScanReport {
        val pm = context.packageManager
        return try {
            val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
            val info = if (Build.VERSION.SDK_INT >= 33) pm.getPackageInfo(packageName, PackageManager.PackageInfoFlags.of(flags.toLong())) else {
                @Suppress("DEPRECATION")
                pm.getPackageInfo(packageName, flags)
            }
            val app = info.applicationInfo
            val label = try { app.loadLabel(pm).toString() } catch (_: Exception) { packageName }
            analyze(File(app.sourceDir), info, label, inspectDex = true)
        } catch (_: Exception) {
            ScanReport(packageName, packageName, null, ScanVerdict.UNABLE_TO_SCAN, emptyList(), listOf("Android did not allow this installed package to be inspected."))
        }
    }

    fun scanInstalledInventory(context: Context): List<ScanReport> {
        val pm = context.packageManager
        val flags = PackageManager.GET_PERMISSIONS or PackageManager.GET_ACTIVITIES or PackageManager.GET_SERVICES or PackageManager.GET_RECEIVERS
        val packages = try {
            if (Build.VERSION.SDK_INT >= 33) pm.getInstalledPackages(PackageManager.PackageInfoFlags.of(flags.toLong())) else {
                @Suppress("DEPRECATION")
                pm.getInstalledPackages(flags)
            }
        } catch (_: Exception) { emptyList() }
        return packages.asSequence()
            .filter { it.packageName != context.packageName && it.applicationInfo != null }
            .map { info ->
                val label = try { info.applicationInfo.loadLabel(pm).toString() } catch (_: Exception) { info.packageName }
                analyze(File(info.applicationInfo.sourceDir), info, label, inspectDex = false)
            }.toList()
    }

    private fun analyze(file: File, info: PackageInfo, label: String, inspectDex: Boolean): ScanReport {
        val findings = analyzeIndicators(info.requestedPermissions?.toSet().orEmpty())
        val appInfo = info.applicationInfo
        if (appInfo != null && appInfo.flags and android.content.pm.ApplicationInfo.FLAG_DEBUGGABLE != 0) {
            findings.toMutableList().also { it.add(ScanFinding("Debug build", "The APK is marked debuggable; production apps normally disable this", 1)) }
                .let { return finish(file, info, label, it, inspectDex) }
        }
        val allFindings = findings.toMutableList()
        if (inspectDex && file.isFile) {
            val indicators = inspectDexStrings(file)
            if ("process-execution" in indicators) allFindings += ScanFinding("Code indicator", "DEX contains a process-execution API reference", 2)
            if ("dynamic-loading" in indicators) allFindings += ScanFinding("Code indicator", "DEX contains a dynamic-code-loading API reference", 2)
            if ("accessibility" in indicators) allFindings += ScanFinding("Code indicator", "DEX references accessibility-service APIs", 1)
            if ("obfuscation" in indicators) allFindings += ScanFinding("Code indicator", "DEX contains a high density of encoded-looking strings", 1)
        }
        return finish(file, info, label, allFindings, inspectDex)
    }

    private fun finish(file: File, info: PackageInfo, label: String, findings: List<ScanFinding>, inspectDex: Boolean): ScanReport {
        val unique = findings.distinctBy { it.title + it.detail }
        val verdict = if (unique.isEmpty()) ScanVerdict.NO_KNOWN_INDICATORS else ScanVerdict.SUSPICIOUS
        val hash = if (file.isFile && file.length() in 1..ApkFingerprint.MAX_FILE_BYTES) hash(file) else null
        return ScanReport(label, info.packageName, hash, verdict, unique, listOf(
            "Static heuristic scan only; findings are indicators, not proof of malware.",
            "No trusted malware hash/signature feed is configured. A clean result does not prove safety.",
            if (inspectDex) "DEX inspection is bounded and may miss packed, encrypted, or native payloads." else "Inventory mode checks app metadata and permissions only; it does not inspect DEX code."
        ))
    }

    internal fun analyzeIndicators(permissions: Set<String>, dexText: String = ""): List<ScanFinding> {
        val findings = mutableListOf<ScanFinding>()
        for ((permission, description) in sensitivePermissions) {
            if (permission in permissions) {
                val points = if (permission == "android.permission.SEND_SMS" || permission == "android.permission.REQUEST_INSTALL_PACKAGES" || permission == "android.permission.BIND_ACCESSIBILITY_SERVICE") 2 else 1
                findings += ScanFinding("Sensitive capability", description, points)
            }
        }
        if ("android.permission.RECEIVE_BOOT_COMPLETED" in permissions && ("android.permission.RECEIVE_SMS" in permissions || "android.permission.SEND_SMS" in permissions)) {
            findings += ScanFinding("Permission combination", "SMS access combined with startup-at-boot permission needs review", 2)
        }
        if ("java/lang/Runtime;->exec" in dexText || ("java.lang.Runtime" in dexText && ".exec" in dexText)) findings += ScanFinding("Code indicator", "DEX contains a process-execution API reference", 2)
        if ("dalvik/system/DexClassLoader" in dexText || "InMemoryDexClassLoader" in dexText) findings += ScanFinding("Code indicator", "DEX contains a dynamic-code-loading API reference", 2)
        if ("AccessibilityService" in dexText) findings += ScanFinding("Code indicator", "DEX references accessibility-service APIs", 1)
        return findings.distinctBy { it.title + it.detail }
    }

    private fun inspectDexStrings(apk: File): Set<String> {
        val output = mutableSetOf<String>()
        return try {
            ZipFile(apk).use { zip ->
                val entries = zip.entries()
                var total = 0L
                while (entries.hasMoreElements() && total < MAX_DEX_BYTES) {
                    val entry = entries.nextElement()
                    if (!entry.name.matches(Regex("classes(\\d*)\\.dex"))) continue
                    zip.getInputStream(entry).use { stream ->
                        val bytes = stream.readBytesBounded(MAX_DEX_BYTES - total)
                        total += bytes.size
                        val text = bytes.toString(Charsets.ISO_8859_1)
                        if ("java/lang/Runtime;->exec" in text || ("java.lang.Runtime" in text && ".exec" in text)) output += "process-execution"
                        if ("dalvik/system/DexClassLoader" in text || "InMemoryDexClassLoader" in text) output += "dynamic-loading"
                        if ("AccessibilityService" in text) output += "accessibility"
                        if (text.count { it == ';' || it == '/' } > 12_000 && text.count { it in 'a'..'z' } < 5_000) output += "obfuscation"
                    }
                }
            }
            output
        } catch (_: Exception) { emptySet() }
    }

    private fun java.io.InputStream.readBytesBounded(max: Long): ByteArray {
        if (max <= 0L) return byteArrayOf()
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(16 * 1024)
        var total = 0L
        while (total < max) {
            val count = read(buffer, 0, minOf(buffer.size.toLong(), max - total).toInt())
            if (count <= 0) break
            out.write(buffer, 0, count)
            total += count
        }
        return out.toByteArray()
    }

    private fun hash(file: File): String? = try {
        FileInputStream(file).use { input ->
            when (val result = ApkFingerprint.calculate(input)) {
                is FingerprintResult.Success -> result.sha256
                is FingerprintResult.TooLarge -> null
            }
        }
    } catch (_: Exception) { null }
}
