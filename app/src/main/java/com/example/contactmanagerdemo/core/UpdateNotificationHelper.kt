package com.example.contactmanagerdemo.core

import android.Manifest
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.app.NotificationManagerCompat
import androidx.core.content.ContextCompat
import com.example.contactmanagerdemo.R

object UpdateNotificationHelper {

    private const val CHANNEL_ID = "updates_channel"
    private const val NOTIFICATION_ID = 1403
    private const val REQUEST_OPEN = 1404
    private const val REQUEST_DOWNLOAD = 1405

    fun createChannel(context: Context) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val manager = context.getSystemService(NotificationManager::class.java) ?: return
        if (manager.getNotificationChannel(CHANNEL_ID) != null) return

        val channel = NotificationChannel(
            CHANNEL_ID,
            context.getString(R.string.update_channel_name),
            NotificationManager.IMPORTANCE_HIGH,
        ).apply {
            description = context.getString(R.string.update_channel_description)
        }
        manager.createNotificationChannel(channel)
    }

    fun showUpdateNotification(
        context: Context,
        versionName: String,
        downloadUrl: String?,
        releaseUrl: String,
    ) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU &&
            ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED
        ) {
            AppEventLogger.warn("UPDATE", "Notification permission not granted")
            return
        }

        val effectiveDownloadUrl = downloadUrl?.trim().takeUnless { it.isNullOrBlank() } ?: releaseUrl
        val openPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_OPEN,
            Intent(Intent.ACTION_VIEW, Uri.parse(effectiveDownloadUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )
        val downloadPendingIntent = PendingIntent.getActivity(
            context,
            REQUEST_DOWNLOAD,
            Intent(Intent.ACTION_VIEW, Uri.parse(effectiveDownloadUrl)),
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE,
        )

        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(R.drawable.ic_contacts_header_white)
            .setContentTitle(context.getString(R.string.update_notification_title, versionName))
            .setContentText(context.getString(R.string.update_notification_text))
            .setStyle(
                NotificationCompat.BigTextStyle()
                    .bigText(context.getString(R.string.update_notification_text)),
            )
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setCategory(NotificationCompat.CATEGORY_RECOMMENDATION)
            .setOngoing(true)
            .setAutoCancel(false)
            .setOnlyAlertOnce(true)
            .setContentIntent(openPendingIntent)
            .addAction(
                R.drawable.ic_import_white_20,
                context.getString(R.string.update_notification_action_download),
                downloadPendingIntent,
            )
            .build()

        NotificationManagerCompat.from(context).notify(NOTIFICATION_ID, notification)
    }
}
