package com.quack.online.monitoring

import java.util.concurrent.atomic.AtomicBoolean

/**
 * In-memory [MonitoringRepository] for deterministic unit tests. No Android framework classes
 * are required.
 */
class FakeMonitoringRepository : MonitoringRepository {

    override var monitoringActive: Boolean = false

    override var config: MonitoringConfig? = null
        private set

    override val stats: MonitoringStats
        get() = currentStats

    override val lastStatus: String
        get() = currentStats.lastStatus

    private var currentStats = MonitoringStats()
    val listeners = mutableListOf<() -> Unit>()
    val saveConfigCalls = mutableListOf<MonitoringConfig>()
    val saveStatsCalls = mutableListOf<MonitoringStats>()
    var resetStatsCalled: Boolean = false

    override fun saveConfig(target: String, intervalMs: Long, resolvedIp: String?, displayName: String) {
        config = MonitoringConfig(target, intervalMs, resolvedIp, displayName)
        saveConfigCalls.add(config!!)
    }

    override fun saveStats(stats: MonitoringStats) {
        currentStats = stats
        saveStatsCalls.add(stats)
    }

    override fun resetStats() {
        resetStatsCalled = true
        currentStats = MonitoringStats()
    }

    override fun registerListener(listener: () -> Unit) = listeners.add(listener)

    override fun unregisterListener(listener: () -> Unit) = listeners.remove(listener)

    fun notifyListeners() = listeners.forEach { it() }

    fun setStats(stats: MonitoringStats) {
        currentStats = stats
    }

    companion object {
        @JvmStatic
        fun active(target: String = "1.1.1.1", intervalMs: Long = 60_000L): FakeMonitoringRepository {
            return FakeMonitoringRepository().apply {
                monitoringActive = true
                config = MonitoringConfig(target, intervalMs, target, target)
            }
        }
    }
}
