package com.quack.online.monitoring

import android.app.Notification
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.Context
import android.content.Intent
import android.os.Build
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.quack.online.MainActivity
import com.quack.online.R

/**
 * Builds and posts the ongoing foreground-service notification that reports monitoring status.
 *
 * Notification title:  "ONLINE — 1.1.1.1" / "OFFLINE — 8.8.8.8" / "CHECKING — ..." / "ERROR — ..."
 * Notification body:   "Total attempts: 120 | Successful: 118 | Server IP: 1.1.1.1"
 *
 * The essential statistics live in the compact body; an expanded layout adds convenience details.
 */
class MonitoringNotificationManager(private val context: Context) : MonitoringNotifier {

    // Services do not inherit AppCompatActivity's locale-wrapped resources on pre-Android 13.
    private val localizedContext = ContextCompat.getContextForLanguage(context)

    companion object {
        internal const val CHANNEL_ID = "com.quack.online.monitoring"
        internal const val NOTIFICATION_ID = 1001
    }

    private val notificationManager: NotificationManager =
        context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager

    /** Builds the notification shown when monitoring starts or is updated after a cycle. */
    fun buildNotification(status: String, serverIp: String, stats: MonitoringStats): Notification {
        return NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_menu_info_details)
            .setContentTitle(MonitoringNotificationContent.title(localizedContext, status, serverIp))
            .setContentText(
                MonitoringNotificationContent.body(
                    localizedContext,
                    stats.totalAttempts,
                    stats.successfulAttempts,
                    serverIp,
                )
            )
            .setStyle(
                NotificationCompat.BigTextStyle().bigText(
                    MonitoringNotificationContent.expanded(localizedContext, status, serverIp, stats)
                )
            )
            .setOngoing(true)
            .setPriority(NotificationCompat.PRIORITY_LOW)
            .setWhen(System.currentTimeMillis())
            .setContentIntent(openAppPendingIntent())
            .addAction(
                0,
                localizedContext.getString(R.string.notification_action_stop),
                stopMonitoringPendingIntent()
            )
            .addAction(
                0,
                localizedContext.getString(R.string.notification_action_open),
                openAppPendingIntent()
            )
            .build()
    }

    /** Posts an updated notification while the service remains in the foreground. */
    fun post(status: String, serverIp: String, stats: MonitoringStats) {
        notificationManager.notify(NOTIFICATION_ID, buildNotification(status, serverIp, stats))
    }

    override fun showChecking(serverIp: String, stats: MonitoringStats) {
        post(MonitoringStatus.CHECKING, serverIp, stats)
    }

    override fun showOnline(serverIp: String, stats: MonitoringStats) {
        post(MonitoringStatus.ONLINE, serverIp, stats)
    }

    override fun showOffline(serverIp: String, stats: MonitoringStats, reason: String) {
        post(MonitoringStatus.OFFLINE, serverIp, stats)
    }

    override fun showError(serverIp: String, stats: MonitoringStats, reason: String) {
        post(MonitoringStatus.ERROR, serverIp, stats)
    }

    fun cancelNotification() {
        notificationManager.cancel(NOTIFICATION_ID)
    }

    /**
     * Creates the notification channel on Android 8.0+.
     * Ongoing + low priority: never interrupts the user and cannot be swiped away.
     */
    fun ensureChannel() {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        val channel = NotificationChannel(
            CHANNEL_ID,
            localizedContext.getString(R.string.notification_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = localizedContext.getString(R.string.notification_channel_description)
            lockscreenVisibility = NotificationCompat.VISIBILITY_PUBLIC
        }
        notificationManager.createNotificationChannel(channel)
    }

    private fun stopMonitoringPendingIntent(): PendingIntent {
        val intent = Intent(context, MonitoringService::class.java).apply {
            action = MonitoringService.ACTION_STOP_MONITORING
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return servicePendingIntent(intent, mutableFlags() or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    private fun openAppPendingIntent(): PendingIntent {
        val intent = Intent(context, MainActivity::class.java).apply {
            action = Intent.ACTION_MAIN
            flags = Intent.FLAG_ACTIVITY_NEW_TASK or Intent.FLAG_ACTIVITY_CLEAR_TOP
        }
        return activityPendingIntent(intent, mutableFlags() or PendingIntent.FLAG_UPDATE_CURRENT)
    }

    /**
     * PendingIntent mutability is required on Android 12+. FLAG_IMMUTABLE exists only from API 31;
     * on older APIs the flag simply does not exist (value 0).
     */
    private fun servicePendingIntent(intent: Intent, flags: Int): PendingIntent =
        PendingIntent.getService(context, 0, intent, flags)

    private fun activityPendingIntent(intent: Intent, flags: Int): PendingIntent =
        PendingIntent.getActivity(context, 0, intent, flags)

    private fun mutableFlags(): Int =
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) PendingIntent.FLAG_IMMUTABLE else 0
}

/**
 * Compatibility shim for the 3-argument startForeground (foreground service type), available
 * from API 29. On older APIs the type argument is ignored by the platform.
 */
object ForegroundStarter {
    fun startForeground(service: android.app.Service, id: Int, notification: Notification, type: String) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            service.startForeground(id, notification, android.content.pm.ServiceInfo.FOREGROUND_SERVICE_TYPE_DATA_SYNC)
        } else {
            service.startForeground(id, notification)
        }
    }

    fun stopForeground(service: android.app.Service) {
        service.stopForeground(true)
    }
}
