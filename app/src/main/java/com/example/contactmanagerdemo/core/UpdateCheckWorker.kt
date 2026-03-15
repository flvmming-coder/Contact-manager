package com.example.contactmanagerdemo.core

import android.content.Context
import android.content.pm.PackageInfo
import androidx.work.Worker
import androidx.work.WorkerParameters

class UpdateCheckWorker(
    appContext: Context,
    params: WorkerParameters,
) : Worker(appContext, params) {

    override fun doWork(): Result {
        AppEventLogger.init(applicationContext)
        return runCatching {
            val latest = UpdateChecker.fetchLatestReleaseOrPrerelease()
            val currentVersion = getCurrentVersionName(applicationContext)

            val latestNormalized = UpdateChecker.normalizeVersionTag(latest.tagName)
            val currentNormalized = UpdateChecker.normalizeVersionTag(currentVersion)

            val hasUpdate = UpdateChecker.compareVersions(latestNormalized, currentNormalized) > 0
            if (!hasUpdate) {
                AppEventLogger.info("UPDATE", "No new updates: current=$currentNormalized")
                return Result.success()
            }

            val prefs = applicationContext.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)
            val lastNotifiedVersion = prefs.getString(KEY_LAST_NOTIFIED_VERSION, null)
            if (lastNotifiedVersion == latestNormalized) {
                AppEventLogger.info("UPDATE", "Update $latestNormalized already notified")
                return Result.success()
            }

            UpdateNotificationHelper.showUpdateNotification(
                context = applicationContext,
                versionName = latestNormalized,
                downloadUrl = latest.downloadUrl,
                releaseUrl = latest.htmlUrl.ifBlank { RELEASES_PAGE_URL },
            )
            prefs.edit().putString(KEY_LAST_NOTIFIED_VERSION, latestNormalized).apply()
            AppEventLogger.info("UPDATE", "Notification sent for version=$latestNormalized")
            Result.success()
        }.getOrElse { error ->
            AppEventLogger.error("UPDATE", "Update check worker failed", error)
            Result.retry()
        }
    }

    private fun getCurrentVersionName(context: Context): String {
        return runCatching {
            @Suppress("DEPRECATION")
            val info: PackageInfo = context.packageManager.getPackageInfo(context.packageName, 0)
            info.versionName
        }.getOrDefault("0.0.0")
    }

    companion object {
        private const val PREFS_NAME = "contact_manager_dev_settings"
        private const val KEY_LAST_NOTIFIED_VERSION = "last_notified_version"
        private const val RELEASES_PAGE_URL = "https://github.com/flvmming-coder/Contact-manager/releases"
    }
}
