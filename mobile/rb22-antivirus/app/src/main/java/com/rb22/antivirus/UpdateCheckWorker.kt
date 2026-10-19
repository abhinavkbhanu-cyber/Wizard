package com.rb22.antivirus

import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.work.Constraints
import androidx.work.CoroutineWorker
import androidx.work.ExistingPeriodicWorkPolicy
import androidx.work.NetworkType
import androidx.work.PeriodicWorkRequestBuilder
import androidx.work.WorkManager
import androidx.work.WorkerParameters
import org.json.JSONArray
import java.net.HttpURLConnection
import java.net.URL
import java.util.concurrent.TimeUnit

private const val UPDATE_WORK_NAME = "rb22-antivirus-periodic-update-check"
private const val UPDATE_CHANNEL_ID = "rb22_antivirus_updates"
private const val RELEASES_API = "https://api.github.com/repos/abhinavkbhanu-cyber/Wizard/releases?per_page=100"
private const val RELEASES_PAGE = "https://github.com/abhinavkbhanu-cyber/Wizard/releases"
private const val RELEASE_TAG_PREFIX = "rb22-antivirus-v"

class UpdateCheckWorker(context: Context, params: WorkerParameters) : CoroutineWorker(context, params) {
    override suspend fun doWork(): Result {
        val connection = try {
            (URL(RELEASES_API).openConnection() as HttpURLConnection).apply {
                connectTimeout = 10_000
                readTimeout = 10_000
                requestMethod = "GET"
                setRequestProperty("Accept", "application/vnd.github+json")
                setRequestProperty("User-Agent", "RB22-Antivirus-Android")
            }
        } catch (_: Exception) {
            return Result.retry()
        }

        return try {
            if (connection.responseCode != HttpURLConnection.HTTP_OK) return Result.retry()
            val releases = JSONArray(connection.inputStream.bufferedReader().use { it.readText() })
            val versions = mutableListOf<Pair<String, String>>()
            for (i in 0 until releases.length()) {
                val release = releases.getJSONObject(i)
                val tag = release.optString("tag_name")
                if (tag.startsWith(RELEASE_TAG_PREFIX) && !release.optBoolean("draft", true)) {
                    versions += tag.removePrefix(RELEASE_TAG_PREFIX) to release.optString("html_url", RELEASES_PAGE)
                }
            }
            val latest = versions.maxWithOrNull { a, b -> compareVersions(a.first, b.first) }
                ?: return Result.success()
            if (compareVersions(latest.first, BuildConfig.VERSION_NAME) > 0) {
                val prefs = applicationContext.getSharedPreferences("rb22_update_checks", Context.MODE_PRIVATE)
                if (prefs.getString("last_notified_version", null) != latest.first) {
                    showUpdateNotification(latest.first, latest.second)
                    prefs.edit().putString("last_notified_version", latest.first).apply()
                }
            }
            Result.success()
        } catch (_: Exception) {
            Result.retry()
        } finally {
            connection.disconnect()
        }
    }

    private fun showUpdateNotification(version: String, releaseUrl: String) {
        val manager = applicationContext.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            manager.createNotificationChannel(
                NotificationChannel(UPDATE_CHANNEL_ID, "RB22 Antivirus updates", NotificationManager.IMPORTANCE_DEFAULT)
            )
        }
        val intent = Intent(Intent.ACTION_VIEW, Uri.parse(releaseUrl.ifBlank { RELEASES_PAGE }))
        val pendingIntent = PendingIntent.getActivity(
            applicationContext, 22, intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val notification = NotificationCompat.Builder(applicationContext, UPDATE_CHANNEL_ID)
            .setSmallIcon(android.R.drawable.stat_sys_download_done)
            .setContentTitle("RB22 Antivirus update available")
            .setContentText("Version $version is available. Tap to review the official release.")
            .setStyle(NotificationCompat.BigTextStyle().bigText("RB22 Antivirus $version is available. Review the official release before installing. Installation still requires Android's supported installer flow."))
            .setContentIntent(pendingIntent)
            .setAutoCancel(true)
            .build()
        try {
            NotificationManagerCompat.from(applicationContext).notify(2201, notification)
        } catch (_: SecurityException) {
            // Notification permission may be denied; do not fail the background worker.
        }
    }

    private fun compareVersions(left: String, right: String): Int {
        fun parts(value: String): List<Int> =
            value.substringBefore('-').split('.').map { it.toIntOrNull() ?: 0 }
        val a = parts(left)
        val b = parts(right)
        for (i in 0 until maxOf(a.size, b.size)) {
            val difference = a.getOrElse(i) { 0 } - b.getOrElse(i) { 0 }
            if (difference != 0) return difference.compareTo(0)
        }
        return when {
            ('-' !in left) && ('-' in right) -> 1
            ('-' in left) && ('-' !in right) -> -1
            else -> 0
        }
    }

    companion object {
        fun schedule(context: Context) {
            val constraints = Constraints.Builder()
                .setRequiredNetworkType(NetworkType.CONNECTED)
                .build()
            val request = PeriodicWorkRequestBuilder<UpdateCheckWorker>(24, TimeUnit.HOURS)
                .setConstraints(constraints)
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                UPDATE_WORK_NAME,
                ExistingPeriodicWorkPolicy.KEEP,
                request
            )
        }
    }
}
