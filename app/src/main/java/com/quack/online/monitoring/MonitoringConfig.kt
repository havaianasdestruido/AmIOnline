package com.quack.online.monitoring

/**
 * User-configured continuous monitoring settings.
 *
 * - Default interval: 1 minute (60_000 ms).
 * - Sensible minimum: 10 seconds. Android cannot guarantee exact cadence below that,
 *   and battery optimizations (Doze, App Standby) may delay checks further.
 *   The UI explains this instead of silently promising exact timing.
 * - All fields are persisted across restarts via [MonitoringRepository].
 */
data class MonitoringConfig(
    /** Target host name, IPv4 or IPv6 address as entered by the user. */
    val target: String,
    /** Ping interval in milliseconds. Defaults to 1 minute. */
    val intervalMs: Long = DEFAULT_INTERVAL_MS,
    /** Resolved IP address of [target], if resolution succeeded. Null for unresolved host names. */
    val resolvedIp: String? = null,
    /** Human-friendly label shown alongside the IP (host name, or the literal IP). */
    val displayName: String = ""
) {
    companion object {
        const val DEFAULT_INTERVAL_MS: Long = 60_000L
        const val MIN_INTERVAL_MS: Long = 10_000L

        fun isValidInterval(intervalMs: Long): Boolean = intervalMs >= MIN_INTERVAL_MS

        fun coerceInterval(intervalMs: Long): Long = intervalMs.coerceIn(MIN_INTERVAL_MS, Long.MAX_VALUE)
    }
}

/**
 * Status values shared between the engine, the notification and the UI.
 * Notification titles use exactly these words (e.g. "ONLINE — 1.1.1.1").
 */
object MonitoringStatus {
    const val IDLE = "IDLE"
    const val CHECKING = "CHECKING"
    const val ONLINE = "ONLINE"
    const val OFFLINE = "OFFLINE"
    const val ERROR = "ERROR"
}
