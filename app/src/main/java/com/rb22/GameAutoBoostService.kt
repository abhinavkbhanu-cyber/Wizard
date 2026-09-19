package com.rb22

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.Service
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GameAutoBoostService : Service() {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    @Volatile private var lastGame = ""

    override fun onCreate() {
        super.onCreate()
        createNotificationChannel()

        val notification = Notification.Builder(this, "rb22_auto")
            .setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("RB22 Game Booster")
            .setContentText("Watching your Game Library")
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.UPSIDE_DOWN_CAKE) {
            startForeground(
                23,
                notification,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_SPECIAL_USE
            )
        } else {
            startForeground(23, notification)
        }

        executor.scheduleWithFixedDelay(
            { runCatching { checkForegroundGame() } },
            0,
            2,
            TimeUnit.SECONDS
        )
    }

    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            getSystemService(NotificationManager::class.java).createNotificationChannel(
                NotificationChannel(
                    "rb22_auto",
                    "RB22 Auto Boost",
                    NotificationManager.IMPORTANCE_LOW
                )
            )
        }
    }

    private fun checkForegroundGame() {
        val games = getSharedPreferences("rb22_games", Context.MODE_PRIVATE)
            .getStringSet("packages", emptySet()) ?: emptySet()

        if (games.isEmpty()) return

        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(
            UsageStatsManager.INTERVAL_DAILY,
            now - 15000,
            now
        )
        val current = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: return

        if (current in games && current != lastGame) {
            lastGame = current
            val label = runCatching {
                packageManager.getApplicationLabel(
                    packageManager.getApplicationInfo(current, 0)
                ).toString()
            }.getOrDefault("Game")

            val notification = Notification.Builder(this, "rb22_auto")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("RB22 AUTO BOOST")
                .setContentText("Boost profile active • " + label)
                .setAutoCancel(true)
                .build()

            getSystemService(NotificationManager::class.java).notify(24, notification)
        } else if (current !in games) {
            lastGame = ""
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
