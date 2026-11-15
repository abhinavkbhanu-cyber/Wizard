package com.rb22.antivirus

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager

class PackageProtectionReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        if (intent.action != Intent.ACTION_PACKAGE_ADDED && intent.action != Intent.ACTION_PACKAGE_REPLACED) return
        val prefs = context.getSharedPreferences(PROTECTION_PREFS, Context.MODE_PRIVATE)
        if (!prefs.getBoolean(PROTECTION_ENABLED, true)) return
        val packageName = intent.data?.schemeSpecificPart ?: return
        if (packageName == context.packageName) return
        val request = OneTimeWorkRequestBuilder<InstalledPackageScanWorker>()
            .setInputData(Data.Builder().putString(PACKAGE_NAME_KEY, packageName).build())
            .build()
        WorkManager.getInstance(context).enqueue(request)
    }
}
