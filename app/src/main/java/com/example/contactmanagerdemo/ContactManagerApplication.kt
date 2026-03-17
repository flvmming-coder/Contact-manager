package com.example.contactmanagerdemo

import android.app.Application
import android.os.Build
import com.example.contactmanagerdemo.core.AppEventLogger
import com.example.contactmanagerdemo.core.ThemeManager
import com.example.contactmanagerdemo.core.UpdateNotificationHelper
import com.example.contactmanagerdemo.core.UpdateWorkScheduler

class ContactManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        ThemeManager.applySavedTheme(this)
        AppEventLogger.init(this)
        UpdateNotificationHelper.createChannel(this)
        UpdateWorkScheduler.schedule(this)
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("unknown")
        val prefs = getSharedPreferences(PREFS_NAME, MODE_PRIVATE)
        val previousVersion = prefs.getString(KEY_LAST_RUN_VERSION, null)
        if (!previousVersion.isNullOrBlank() && previousVersion != version) {
            AppEventLogger.info("UPDATE", "Application updated from version=$previousVersion to version=$version")
        }
        prefs.edit()
            .putString(KEY_LAST_RUN_VERSION, version)
            .putLong(KEY_LAST_RUN_AT, System.currentTimeMillis())
            .apply()
        AppEventLogger.info(
            "APP",
            "Application started, version=$version, sdk=${Build.VERSION.SDK_INT}, device=${Build.MANUFACTURER} ${Build.MODEL}",
        )
    }

    companion object {
        private const val PREFS_NAME = "contact_manager_dev_settings"
        private const val KEY_LAST_RUN_VERSION = "last_run_version"
        private const val KEY_LAST_RUN_AT = "last_run_at"
    }
}
