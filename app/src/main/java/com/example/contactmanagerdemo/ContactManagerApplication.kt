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
        AppEventLogger.info(
            "APP",
            "Application started, version=$version, sdk=${Build.VERSION.SDK_INT}",
        )
    }
}
