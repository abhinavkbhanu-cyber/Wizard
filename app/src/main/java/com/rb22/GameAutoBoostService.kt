package com.rb22

import android.app.*
import android.app.usage.UsageStatsManager
import android.content.Context
import android.content.Intent
import android.os.Build
import android.os.IBinder
import java.util.concurrent.Executors
import java.util.concurrent.TimeUnit

class GameAutoBoostService : Service() {
    private val executor = Executors.newSingleThreadScheduledExecutor()
    private var lastGame = ""
    override fun onCreate() {
        super.onCreate()
        if (Build.VERSION.SDK_INT >= 26) getSystemService(NotificationManager::class.java).createNotificationChannel(
            NotificationChannel("rb22_auto", "RB22 Auto Boost", NotificationManager.IMPORTANCE_LOW))
        startForeground(23, Notification.Builder(this, "rb22_auto").setSmallIcon(android.R.drawable.ic_media_play)
            .setContentTitle("RB22 Game Booster").setContentText("Watching your Game Library").setOngoing(true).build())
        executor.scheduleWithFixedDelay({ checkForegroundGame() }, 0, 2, TimeUnit.SECONDS)
    }
    private fun checkForegroundGame() {
        val games = getSharedPreferences("rb22_games", Context.MODE_PRIVATE).getStringSet("packages", emptySet()) ?: emptySet()
        if (games.isEmpty()) return
        val usm = getSystemService(USAGE_STATS_SERVICE) as UsageStatsManager
        val now = System.currentTimeMillis()
        val stats = usm.queryUsageStats(UsageStatsManager.INTERVAL_DAILY, now - 15000, now)
        val current = stats.maxByOrNull { it.lastTimeUsed }?.packageName ?: return
        if (current in games && current != lastGame) {
            lastGame = current
            val label = runCatching { packageManager.getApplicationLabel(packageManager.getApplicationInfo(current, 0)).toString() }.getOrDefault("Game")
            getSystemService(NotificationManager::class.java).notify(24, Notification.Builder(this, "rb22_auto").setSmallIcon(android.R.drawable.ic_media_play)
                .setContentTitle("RB22 AUTO BOOST").setContentText("Boost profile active • " + label).build())
        } else if (current !in games) lastGame = ""
    }
    override fun onDestroy() { executor.shutdownNow(); super.onDestroy() }
    override fun onBind(intent: Intent?): IBinder? = null
}