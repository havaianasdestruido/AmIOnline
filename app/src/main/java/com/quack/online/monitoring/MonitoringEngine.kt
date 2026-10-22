package com.quack.online.monitoring

import kotlin.coroutines.CoroutineContext
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.cancel
import kotlinx.coroutines.currentCoroutineContext
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.launch

/**
 * Drives the continuous monitoring loop inside the foreground service.
 *
 * Design notes:
 * - One cycle = one `ping -c 1` probe, so one cycle == one attempt (never ambiguous).
 * - Cycles run sequentially; a new cycle never starts while the previous one is in flight,
 *   so monitoring cycles can never overlap.
 * - The interval and target are re-read from the repository on every cycle, so changing
 *   them updates the schedule without restarting the service or the application.
 * - Per-cycle execution failures (timeout, unreachable host) are reported as ERROR/OFFLINE
 *   status but never silently change the configured interval.
 */
class MonitoringEngine(
    private val repository: MonitoringRepository,
    private val notifier: MonitoringNotifier,
    private val probeExecutor: ProbeExecutor = RealProbeExecutor(),
    private val context: CoroutineContext = Dispatchers.Default + SupervisorJob(),
) {
    private val scope = CoroutineScope(context)
    private var job: kotlinx.coroutines.Job? = null

    val isRunning: Boolean
        get() = job?.isActive == true

    fun start() {
        if (isRunning) return
        job = scope.launch {
            runLoop()
        }
    }

    /** Cancel and restart the loop so a new interval/target takes effect immediately. */
    fun reschedule() {
        stop()
        start()
    }

    fun stop() {
        job?.cancel()
        job = null
    }

    private suspend fun runLoop() {
        // Follow this coroutine's own lifecycle: reading the `job` field races with start(),
        // which assigns it only after launch() returns, and could exit the loop immediately.
        while (currentCoroutineContext().isActive) {
            val config = repository.config ?: return
            val intervalMs = MonitoringConfig.coerceInterval(config.intervalMs)
            val ip = config.resolvedIp ?: config.target

            try {
                runCycle(config, ip)
            } catch (e: Exception) {
                // Never let one bad cycle kill the monitoring loop; report and continue.
                notifier.showError(ip, repository.stats, e.message ?: e.toString())
            }

            delay(intervalMs)
        }
    }

    private suspend fun runCycle(config: MonitoringConfig, ip: String) {
        notifier.showChecking(ip, repository.stats)

        val result = probeExecutor.executeProbe(ip, timeoutSeconds = 5)
        val now = System.currentTimeMillis()

        when (result) {
            is ProbeResult.Success -> {
                val stats = repository.stats.withCompletedProbe(successful = true, now)
                repository.saveStats(stats)
                notifier.showOnline(ip, stats)
            }
            is ProbeResult.Failed -> {
                val stats = repository.stats.withCompletedProbe(successful = false, now)
                repository.saveStats(stats)
                notifier.showOffline(ip, stats, result.reason)
            }
            is ProbeResult.ExecutorError -> {
                // No probe was transmitted: counters are intentionally left untouched.
                notifier.showError(ip, repository.stats, result.reason)
            }
        }
    }
}
