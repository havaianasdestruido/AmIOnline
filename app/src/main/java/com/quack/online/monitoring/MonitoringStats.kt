package com.quack.online.monitoring

/**
 * Persistent monitoring counters.
 *
 * Counting rules (documented here so behaviour is unambiguous):
 *
 * - totalAttempts: number of individual ping probes that were actually executed
 *   (the ping process was started and ran to a result). One monitoring cycle with
 *   `ping -c 1` equals exactly one attempt, so cycles and attempts never diverge.
 * - successfulAttempts: number of probes that received at least one valid reply.
 * - failedAttempts: totalAttempts - successfulAttempts.
 * - A probe that timed out or was rejected by the host counts as attempted but not successful.
 * - DNS resolution failures and failures to start the ping process are reported as ERROR
 *   status but do NOT touch the counters: no probe was transmitted.
 * - If the service is interrupted mid-cycle, that probe's outcome is unknown and is not
 *   counted; counters are only written after a cycle completes.
 * - Counters survive process/application restarts. They are reset only when the user
 *   explicitly taps "Reset statistics".
 */
data class MonitoringStats(
    val totalAttempts: Long = 0,
    val successfulAttempts: Long = 0,
    val lastCheckMs: Long = 0,
    val lastSuccessMs: Long = 0,
    val lastStatus: String = MonitoringStatus.IDLE,
) {
    val failedAttempts: Long get() = totalAttempts - successfulAttempts

    fun withCompletedProbe(successful: Boolean, checkMs: Long): MonitoringStats =
        copy(
            totalAttempts = totalAttempts + 1,
            successfulAttempts = successfulAttempts + if (successful) 1 else 0,
            lastCheckMs = checkMs,
            lastStatus = if (successful) MonitoringStatus.ONLINE else MonitoringStatus.OFFLINE,
            lastSuccessMs = if (successful) checkMs else lastSuccessMs,
        )
}
