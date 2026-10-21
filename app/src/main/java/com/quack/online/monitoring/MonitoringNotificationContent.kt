package com.quack.online.monitoring

/**
 * Notification text content.
 *
 * Title examples:  "ONLINE — 1.1.1.1"   "OFFLINE — 8.8.8.8"   "CHECKING — 1.1.1.1"   "ERROR — 1.1.1.1"
 * Body example:    "Total attempts: 120 | Successful: 118 | Server IP: 1.1.1.1"
 *
 * The essential statistics are always in the compact body; the expanded layout only adds
 * convenience details (target name, last check time).
 */
object MonitoringNotificationContent {

    fun title(status: String, serverIp: String): String = "$status — $serverIp"

    fun body(totalAttempts: Long, successfulAttempts: Long, serverIp: String): String =
        "Total attempts: $totalAttempts | Successful: $successfulAttempts | Server IP: $serverIp"

    fun expanded(status: String, serverIp: String, stats: MonitoringStats): String {
        val failed = stats.totalAttempts - stats.successfulAttempts
        val lastCheck = if (stats.lastCheckMs > 0) {
            java.text.DateFormat.getTimeInstance().format(java.util.Date(stats.lastCheckMs))
        } else {
            "never"
        }
        return buildString {
            appendLine("${MonitoringNotificationContent.title(status, serverIp)}")
            appendLine("${MonitoringNotificationContent.body(stats.totalAttempts, stats.successfulAttempts, serverIp)} | Failed: $failed")
            appendLine("Last check: $lastCheck")
            appendLine("Monitoring continues in the background.")
        }
    }
}
