package com.rb22

import android.app.ActivityManager
import android.content.Context
import android.os.Build
import android.os.PowerManager

class AdaptivePerformanceEngine(private val context: Context) {
    fun statusText(): String {
        val am = context.getSystemService(Context.ACTIVITY_SERVICE) as ActivityManager
        val mem = ActivityManager.MemoryInfo()
        am.getMemoryInfo(mem)
        val ram = if (mem.totalMem > 0) ((mem.totalMem - mem.availMem) * 100 / mem.totalMem).toInt() else 0
        val thermal = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
            when (pm.currentThermalStatus) {
                PowerManager.THERMAL_STATUS_NONE -> "THERMAL OK"
                PowerManager.THERMAL_STATUS_LIGHT -> "THERMAL LIGHT"
                PowerManager.THERMAL_STATUS_MODERATE -> "THERMAL MODERATE"
                PowerManager.THERMAL_STATUS_SEVERE -> "THERMAL SEVERE"
                PowerManager.THERMAL_STATUS_CRITICAL -> "THERMAL CRITICAL"
                else -> "THERMAL UNKNOWN"
            }
        } else "THERMAL LIMITED"
        val pm = context.getSystemService(Context.POWER_SERVICE) as PowerManager
        val power = if (pm.isPowerSaveMode) "POWER-SAVE" else "PERFORMANCE"
        val caps = when {
            Build.VERSION.SDK_INT >= 36 -> "Android 16+ headroom capable"
            Build.VERSION.SDK_INT >= 31 -> "Android 12+ performance APIs"
            Build.VERSION.SDK_INT >= 29 -> "thermal-aware"
            else -> "compatibility mode"
        }
        return "ULTIMATE ENGINE • " + thermal + " • RAM " + ram + "% • " + power + " • " + caps
    }
}
