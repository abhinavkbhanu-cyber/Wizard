package com.rb22

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

/**
 * RB22 v1.2 device-aware profile engine.
 *
 * This engine can tune RB22's own workload and provide game graphics guidance.
 * Android does not grant ordinary apps authority to overclock the device, change
 * another app's CPU/GPU scheduler priority, or force that app's FPS.
 */
class V12PerformanceEngine(private val context: Context) {
    enum class Profile { BALANCED, GAMING, STREAMING, BATTERY }

    private val prefs = context.getSharedPreferences("rb22_games", Context.MODE_PRIVATE)

    fun setProfile(profile: Profile) {
        prefs.edit().putString("v12_profile", profile.name).apply()
        prefs.edit().putBoolean("game_mode", profile == Profile.GAMING).apply()
        prefs.edit().putBoolean("max_fps", profile == Profile.GAMING).apply()
        prefs.edit().putBoolean("charging_mode", profile == Profile.BATTERY).apply()
    }

    fun currentProfile(): Profile = runCatching {
        Profile.valueOf(prefs.getString("v12_profile", Profile.BALANCED.name)!!)
    }.getOrDefault(Profile.BALANCED)

    fun thermalLabel(): String {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) return "Thermal API unavailable"
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        return when (pm.currentThermalStatus) {
            PowerManager.THERMAL_STATUS_NONE -> "Normal"
            PowerManager.THERMAL_STATUS_LIGHT -> "Light"
            PowerManager.THERMAL_STATUS_MODERATE -> "Moderate"
            PowerManager.THERMAL_STATUS_SEVERE -> "Severe"
            PowerManager.THERMAL_STATUS_CRITICAL -> "Critical"
            else -> "Unknown"
        }
    }

    fun memoryPressurePercent(): Int {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val info = ActivityManager.MemoryInfo()
        am.getMemoryInfo(info)
        return if (info.totalMem > 0L) {
            (((info.totalMem - info.availMem).coerceAtLeast(0L) * 100L) / info.totalMem).toInt()
        } else 0
    }

    fun graphicsRecommendation(): String {
        val ram = memoryPressurePercent()
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus
        } else PowerManager.THERMAL_STATUS_NONE

        return when {
            thermal >= PowerManager.THERMAL_STATUS_SEVERE || ram >= 90 ->
                "Use Low graphics and a stable 30 FPS cap; let the device cool before raising settings."
            thermal >= PowerManager.THERMAL_STATUS_MODERATE || ram >= 80 ->
                "Use Low-to-Medium graphics and a stable 30–40 FPS cap."
            ram >= 65 ->
                "Use Medium graphics; choose a stable frame-rate option supported by the game."
            else ->
                "Try Medium graphics first. Raise graphics or FPS one step at a time and check stability."
        }
    }

    fun statusText(): String {
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val battery = if (pm.isPowerSaveMode) "System battery saver ON" else "System battery saver OFF"
        val thermal = thermalLabel()
        val ram = memoryPressurePercent()
        val api = Build.VERSION.SDK_INT
        val support = when {
            api >= 29 -> "thermal safeguards available"
            else -> "basic thermal guidance (Android 9 compatibility)"
        }
        return "RB22 v1.2 • ${currentProfile()} • RAM pressure ${ram}% • ${thermal} • ${battery} • ${support}"
    }

    fun isThermalCaution(): Boolean =
        Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
            (context.getSystemService(Context.POWER_SERVICE) as PowerManager).currentThermalStatus >=
            PowerManager.THERMAL_STATUS_MODERATE
}
