package com.quack.online.monitoring

class FakeNotifier : MonitoringNotifier {
    var lastStatus: String? = null
    var lastServerIp: String? = null
    var lastStats: MonitoringStats? = null
    var lastReason: String? = null
    var callCount: Int = 0

    override fun showChecking(serverIp: String, stats: MonitoringStats) = record(null, serverIp, stats)
    override fun showOnline(serverIp: String, stats: MonitoringStats) = record(MonitoringStatus.ONLINE, serverIp, stats)
    override fun showOffline(serverIp: String, stats: MonitoringStats, reason: String) = record(MonitoringStatus.OFFLINE, serverIp, stats, reason)
    override fun showError(serverIp: String, stats: MonitoringStats, reason: String) = record(MonitoringStatus.ERROR, serverIp, stats, reason)

    private fun record(status: String?, serverIp: String, stats: MonitoringStats, reason: String? = null) {
        lastStatus = status
        lastServerIp = serverIp
        lastStats = stats
        lastReason = reason
        callCount++
    }
}
