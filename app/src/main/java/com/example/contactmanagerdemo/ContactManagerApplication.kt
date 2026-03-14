package com.example.contactmanagerdemo

import android.app.Application
import android.os.Build
import com.example.contactmanagerdemo.core.AppEventLogger

class ContactManagerApplication : Application() {
    override fun onCreate() {
        super.onCreate()
        AppEventLogger.init(this)
        val version = runCatching {
            packageManager.getPackageInfo(packageName, 0).versionName
        }.getOrDefault("unknown")
        AppEventLogger.info(
            "APP",
            "Application started, version=$version, sdk=${Build.VERSION.SDK_INT}",
        )
    }
}
