package com.quack.online.monitoring

import android.content.Context
import com.quack.online.R
import java.text.DateFormat
import java.util.Date

/** Formats compact and expanded monitoring notification content. */
object MonitoringNotificationContent {

    // Kept as locale-independent helpers for plain JVM unit tests and existing callers.
    fun title(status: String, serverIp: String): String = "$status — $serverIp"

    fun body(totalAttempts: Long, successfulAttempts: Long, serverIp: String): String =
        "Total attempts: $totalAttempts | Successful: $successfulAttempts | Server IP: $serverIp"

    fun expanded(status: String, serverIp: String, stats: MonitoringStats): String {
        val failed = stats.totalAttempts - stats.successfulAttempts
        val lastCheck = if (stats.lastCheckMs > 0) {
            DateFormat.getTimeInstance().format(Date(stats.lastCheckMs))
        } else {
            "never"
        }
        return buildString {
            appendLine(title(status, serverIp))
            appendLine("${body(stats.totalAttempts, stats.successfulAttempts, serverIp)} | Failed: $failed")
            appendLine("Last check: $lastCheck")
            appendLine("Monitoring continues in the background.")
        }
    }

    fun title(context: Context, status: String, serverIp: String): String =
        context.getString(R.string.notification_title, localizedStatus(context, status), serverIp)

    fun body(context: Context, totalAttempts: Long, successfulAttempts: Long, serverIp: String): String =
        context.getString(R.string.notification_body, totalAttempts, successfulAttempts, serverIp)

    fun expanded(context: Context, status: String, serverIp: String, stats: MonitoringStats): String {
        val failed = stats.totalAttempts - stats.successfulAttempts
        val locale = context.resources.configuration.locales[0]
        val lastCheck = if (stats.lastCheckMs > 0) {
            DateFormat.getTimeInstance(DateFormat.SHORT, locale).format(Date(stats.lastCheckMs))
        } else {
            context.getString(R.string.notification_never)
        }
        val compactBody = body(context, stats.totalAttempts, stats.successfulAttempts, serverIp)
        return buildString {
            appendLine(title(context, status, serverIp))
            appendLine(context.getString(R.string.notification_expanded_stats, compactBody, failed))
            appendLine(context.getString(R.string.notification_last_check, lastCheck))
            append(context.getString(R.string.notification_background))
        }
    }

    private fun localizedStatus(context: Context, status: String): String = context.getString(when (status) {
        MonitoringStatus.CHECKING -> R.string.notification_status_checking
        MonitoringStatus.ONLINE -> R.string.notification_status_online
        MonitoringStatus.OFFLINE -> R.string.notification_status_offline
        MonitoringStatus.ERROR -> R.string.notification_status_error
        else -> R.string.notification_status_idle
    })
}
