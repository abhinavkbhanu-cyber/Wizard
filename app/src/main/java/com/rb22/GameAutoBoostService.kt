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
    @Volatile private var previousProfile = "BALANCED"

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
        val prefs = getSharedPreferences("rb22_games", Context.MODE_PRIVATE)
        val games = prefs.getStringSet("packages", emptySet()) ?: emptySet()
        if (games.isEmpty()) return

        val usage = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val events = usage.queryEvents(now - 12000L, now)
        val event = android.app.usage.UsageEvents.Event()
        var foregroundPackage: String? = null
        while (events.hasNextEvent()) {
            events.getNextEvent(event)
            val isForeground = event.eventType == android.app.usage.UsageEvents.Event.MOVE_TO_FOREGROUND ||
                (Build.VERSION.SDK_INT >= 29 &&
                    event.eventType == android.app.usage.UsageEvents.Event.ACTIVITY_RESUMED)
            if (isForeground && event.packageName != packageName) {
                foregroundPackage = event.packageName
            }
        }
        val current = foregroundPackage ?: return

        if (current in games && current != lastGame) {
            if (lastGame.isEmpty()) {
                previousProfile = prefs.getString("v12_profile", "BALANCED") ?: "BALANCED"
            }
            lastGame = current
            val power = getSystemService(Context.POWER_SERVICE) as android.os.PowerManager
            val thermalCaution = Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                power.currentThermalStatus >= android.os.PowerManager.THERMAL_STATUS_MODERATE
            val selectedProfile = if (thermalCaution) "BALANCED" else "GAMING"
            prefs.edit()
                .putBoolean("game_mode", !thermalCaution)
                .putBoolean("max_fps", !thermalCaution)
                .putString("profile", selectedProfile)
                .putString("v12_profile", selectedProfile)
                .apply()

            val label = runCatching {
                packageManager.getApplicationLabel(packageManager.getApplicationInfo(current, 0)).toString()
            }.getOrDefault("Game")
            val notification = Notification.Builder(this, "rb22_auto")
                .setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("RB22 AUTO BOOST")
                .setContentText((if (thermalCaution) "Thermal caution • Balanced profile • " else "Gaming profile selected • ") + label)
                .setAutoCancel(true)
                .build()
            getSystemService(NotificationManager::class.java).notify(24, notification)
        } else if (current !in games && lastGame.isNotEmpty()) {
            lastGame = ""
            val restoreGaming = previousProfile == "GAMING"
            prefs.edit()
                .putBoolean("game_mode", restoreGaming)
                .putBoolean("max_fps", restoreGaming)
                .putBoolean("charging_mode", previousProfile == "BATTERY")
                .putString("profile", previousProfile)
                .putString("v12_profile", previousProfile)
                .apply()
        }
    }

    override fun onDestroy() {
        executor.shutdownNow()
        super.onDestroy()
    }

    override fun onBind(intent: Intent?): IBinder? = null
}
