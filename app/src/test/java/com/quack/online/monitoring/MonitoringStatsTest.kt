package com.quack.online.monitoring

import org.junit.Assert.assertEquals
import org.junit.Test

class MonitoringStatsTest {

    @Test
    fun fresh_stats_are_zero() {
        val stats = MonitoringStats()
        assertEquals(0L, stats.totalAttempts)
        assertEquals(0L, stats.successfulAttempts)
        assertEquals(0L, stats.failedAttempts)
        assertEquals(MonitoringStatus.IDLE, stats.lastStatus)
    }

    @Test
    fun successful_probe_increments_total_and_success() {
        val stats = MonitoringStats()
            .withCompletedProbe(successful = true, checkMs = 1_000L)
            .withCompletedProbe(successful = true, checkMs = 2_000L)

        assertEquals(2L, stats.totalAttempts)
        assertEquals(2L, stats.successfulAttempts)
        assertEquals(0L, stats.failedAttempts)
        assertEquals(MonitoringStatus.ONLINE, stats.lastStatus)
        assertEquals(2_000L, stats.lastSuccessMs)
    }

    @Test
    fun failed_probe_increments_total_but_not_success() {
        val stats = MonitoringStats()
            .withCompletedProbe(successful = true, checkMs = 1_000L)
            .withCompletedProbe(successful = false, checkMs = 2_000L)
            .withCompletedProbe(successful = false, checkMs = 3_000L)

        assertEquals(3L, stats.totalAttempts)
        assertEquals(1L, stats.successfulAttempts)
        assertEquals(2L, stats.failedAttempts)
        assertEquals(MonitoringStatus.OFFLINE, stats.lastStatus)
        assertEquals(1_000L, stats.lastSuccessMs)
    }

    @Test
    fun all_failed_probes() {
        val stats = MonitoringStats()
            .withCompletedProbe(successful = false, checkMs = 500L)
        assertEquals(1L, stats.totalAttempts)
        assertEquals(0L, stats.successfulAttempts)
        assertEquals(1L, stats.failedAttempts)
    }
}
