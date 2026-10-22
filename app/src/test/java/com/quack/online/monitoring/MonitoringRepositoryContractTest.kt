package com.quack.online.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringRepositoryContractTest {

    @Test
    fun config_and_stats_persist_round_trip() {
        val repo = FakeMonitoringRepository()

        repo.monitoringActive = true
        repo.saveConfig("2606:4700:4700::1111", 30_000L, "2606:4700:4700::1111", "2606:4700:4700::1111")
        repo.saveStats(MonitoringStats(totalAttempts = 5, successfulAttempts = 3, lastCheckMs = 42L))

        assertTrue(repo.monitoringActive)
        assertNotNull(repo.config)
        assertEquals("2606:4700:4700::1111", repo.config?.target)
        assertEquals(30_000L, repo.config?.intervalMs)
        assertEquals(5L, repo.stats.totalAttempts)
        assertEquals(3L, repo.stats.successfulAttempts)
        assertEquals(2L, repo.stats.failedAttempts)
    }

    @Test
    fun reset_stats_clears_counters_only() {
        val repo = FakeMonitoringRepository()
        repo.saveConfig("1.1.1.1", 60_000L, "1.1.1.1", "1.1.1.1")
        repo.saveStats(MonitoringStats(totalAttempts = 100, successfulAttempts = 90))

        repo.resetStats()

        assertTrue(repo.resetStatsCalled)
        assertEquals(0L, repo.stats.totalAttempts)
        assertEquals(0L, repo.stats.successfulAttempts)
        // Configuration is intentionally untouched by a statistics reset.
        assertNotNull(repo.config)
        assertEquals("1.1.1.1", repo.config?.target)
    }

    @Test
    fun listeners_are_notified_of_changes() {
        val repo = FakeMonitoringRepository()
        var notified = false
        repo.registerListener { notified = true }

        repo.saveStats(MonitoringStats(totalAttempts = 1))
        repo.notifyListeners()
        assertTrue(notified)

        repo.unregisterListener { } // no-op removal attempt is safe
        notified = false
        repo.saveStats(MonitoringStats(totalAttempts = 2))
        repo.notifyListeners()
        assertTrue(notified) // the remaining listener still fires
    }

    @Test
    fun empty_repository_has_no_config() {
        val repo = FakeMonitoringRepository()
        assertNull(repo.config)
        assertFalse(repo.monitoringActive)
        assertEquals(0L, repo.stats.totalAttempts)
    }
}
