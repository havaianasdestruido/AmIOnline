package com.quack.online.monitoring

/**
 * Thin notification interface used by [MonitoringEngine].
 * [MonitoringNotificationManager] implements it for production; tests can fake it without
 * any Android framework classes.
 */
interface MonitoringNotifier {
    fun showChecking(serverIp: String, stats: MonitoringStats)
    fun showOnline(serverIp: String, stats: MonitoringStats)
    fun showOffline(serverIp: String, stats: MonitoringStats, reason: String)
    fun showError(serverIp: String, stats: MonitoringStats, reason: String)
}
