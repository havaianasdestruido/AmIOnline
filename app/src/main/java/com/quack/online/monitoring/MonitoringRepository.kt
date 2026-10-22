package com.quack.online.monitoring

/**
 * Persistence contract for monitoring configuration and statistics.
 *
 * The implementation used in the app wraps Android SharedPreferences; tests use a fake
 * implementation so no Android framework is required.
 */
interface MonitoringRepository {

    /** Whether continuous monitoring is currently active. */
    var monitoringActive: Boolean

    /** Last saved configuration, or null if none has been saved yet. */
    val config: MonitoringConfig?

    /** Last saved statistics. */
    val stats: MonitoringStats

    /** Last status shown in the UI/notification (IDLE, CHECKING, ONLINE, OFFLINE, ERROR). */
    val lastStatus: String

    fun saveConfig(target: String, intervalMs: Long, resolvedIp: String?, displayName: String)

    fun saveStats(stats: MonitoringStats)

    fun resetStats()

    fun registerListener(listener: () -> Unit)

    fun unregisterListener(listener: () -> Unit)
}
