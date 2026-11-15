package com.rb22.antivirus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.content.Context
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.CoroutineWorker
import androidx.work.WorkerParameters

internal const val PROTECTION_PREFS = "rb22_protection"
internal const val PROTECTION_ENABLED = "real_time_enabled"
internal const val PACKAGE_NAME_KEY = "package_name"
private const val PROTECTION_CHANNEL_ID = "rb22_protection_alerts"

class InstalledPackageScanWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val prefs = applicationContext.getSharedPreferences(PROTECTION_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PROTECTION_ENABLED, true)) return Result.success()
        val packageName = inputData.getString(PACKAGE_NAME_KEY) ?: return Result.failure()
        if (packageName == applicationContext.packageName) return Result.success()
        val report = ApkScanner.scanInstalledPackage(applicationContext, packageName)
        if (report.verdict == ScanVerdict.SUSPICIOUS) notifyFindings(report)
        return Result.success()
    }

    private fun notifyFindings(report: ScanReport) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) manager.createNotificationChannel(
            NotificationChannel(PROTECTION_CHANNEL_ID, "RB22 app security alerts", NotificationManager.IMPORTANCE_HIGH)
        )
        val details = report.findings.take(4).joinToString("; ") { it.detail }
        val notification = NotificationCompat.Builder(applicationContext, PROTECTION_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_notify_error)
            .setContentTitle("RB22: review ${report.appName}")
            .setContentText("Potentially risky app indicators found")
            .setStyle(NotificationCompat.BigTextStyle().bigText(
                "${report.appName} (${report.packageName}) has indicators that deserve review: $details. This is a heuristic warning, not a confirmed malware verdict. RB22 has not blocked or removed the app."
            ))
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify((report.packageName ?: report.appName).hashCode(), notification)
        } catch (_: SecurityException) { }
    }
}
