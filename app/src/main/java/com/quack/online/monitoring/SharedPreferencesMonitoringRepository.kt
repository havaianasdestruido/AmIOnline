package com.quack.online.monitoring

import android.content.Context
import android.content.SharedPreferences
import java.util.concurrent.CopyOnWriteArraySet

/**
 * [MonitoringRepository] backed by SharedPreferences.
 * Configuration and statistics survive application restarts and live in the same
 * preferences file regardless of which component (activity or service) writes them.
 */
class SharedPreferencesMonitoringRepository(private val context: Context) : MonitoringRepository {

    companion object {
        private const val PREFS_NAME = "com.quack.online.monitoring"

        private const val KEY_ACTIVE = "monitoring_active"
        private const val KEY_TARGET = "monitoring_target"
        private const val KEY_INTERVAL_MS = "monitoring_interval_ms"
        private const val KEY_RESOLVED_IP = "monitoring_resolved_ip"
        private const val KEY_DISPLAY_NAME = "monitoring_display_name"

        private const val KEY_STATS_TOTAL = "monitoring_stats_total"
        private const val KEY_STATS_SUCCESS = "monitoring_stats_success"
        private const val KEY_STATS_LAST_CHECK_MS = "monitoring_stats_last_check_ms"
        private const val KEY_STATS_LAST_SUCCESS_MS = "monitoring_stats_last_success_ms"
        private const val KEY_STATS_LAST_STATUS = "monitoring_stats_last_status"
    }

    private val prefs: SharedPreferences =
        context.getSharedPreferences(PREFS_NAME, Context.MODE_PRIVATE)

    private val listeners = CopyOnWriteArraySet<() -> Unit>()

    private val sharedListener = SharedPreferences.OnSharedPreferenceChangeListener { _, _ ->
        listeners.forEach { it() }
    }

    init {
        prefs.registerOnSharedPreferenceChangeListener(sharedListener)
    }

    override var monitoringActive: Boolean
        get() = prefs.getBoolean(KEY_ACTIVE, false)
        set(value) {
            prefs.edit().putBoolean(KEY_ACTIVE, value).apply()
        }

    override val config: MonitoringConfig?
        get() {
            val target = prefs.getString(KEY_TARGET, "") ?: return null
            if (target.isEmpty()) return null
            return MonitoringConfig(
                target = target,
                intervalMs = prefs.getLong(KEY_INTERVAL_MS, MonitoringConfig.DEFAULT_INTERVAL_MS),
                resolvedIp = prefs.getString(KEY_RESOLVED_IP, null),
                displayName = prefs.getString(KEY_DISPLAY_NAME, target) ?: target,
            )
        }

    override val stats: MonitoringStats
        get() = MonitoringStats(
            totalAttempts = prefs.getLong(KEY_STATS_TOTAL, 0),
            successfulAttempts = prefs.getLong(KEY_STATS_SUCCESS, 0),
            lastCheckMs = prefs.getLong(KEY_STATS_LAST_CHECK_MS, 0),
            lastSuccessMs = prefs.getLong(KEY_STATS_LAST_SUCCESS_MS, 0),
            lastStatus = prefs.getString(KEY_STATS_LAST_STATUS, MonitoringStatus.IDLE) ?: MonitoringStatus.IDLE,
        )

    override val lastStatus: String
        get() = stats.lastStatus

    override fun saveConfig(
        target: String,
        intervalMs: Long,
        resolvedIp: String?,
        displayName: String
    ) {
        prefs.edit()
            .putString(KEY_TARGET, target)
            .putLong(KEY_INTERVAL_MS, MonitoringConfig.coerceInterval(intervalMs))
            .putString(KEY_RESOLVED_IP, resolvedIp.orEmpty())
            .putString(KEY_DISPLAY_NAME, displayName)
            .apply()
    }

    override fun saveStats(stats: MonitoringStats) {
        prefs.edit()
            .putLong(KEY_STATS_TOTAL, stats.totalAttempts)
            .putLong(KEY_STATS_SUCCESS, stats.successfulAttempts)
            .putLong(KEY_STATS_LAST_CHECK_MS, stats.lastCheckMs)
            .putLong(KEY_STATS_LAST_SUCCESS_MS, stats.lastSuccessMs)
            .putString(KEY_STATS_LAST_STATUS, stats.lastStatus)
            .apply()
    }

    override fun resetStats() {
        prefs.edit()
            .putLong(KEY_STATS_TOTAL, 0)
            .putLong(KEY_STATS_SUCCESS, 0)
            .putLong(KEY_STATS_LAST_CHECK_MS, 0)
            .putLong(KEY_STATS_LAST_SUCCESS_MS, 0)
            .putString(KEY_STATS_LAST_STATUS, MonitoringStatus.IDLE)
            .apply()
    }

    override fun registerListener(listener: () -> Unit) {
        listeners.add(listener)
    }

    override fun unregisterListener(listener: () -> Unit) {
        listeners.remove(listener)
    }
}
