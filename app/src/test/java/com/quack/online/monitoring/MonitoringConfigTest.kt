package com.quack.online.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringConfigTest {

    @Test
    fun default_interval_is_one_minute() {
        assertEquals(60_000L, MonitoringConfig.DEFAULT_INTERVAL_MS)
        assertEquals(60_000L, MonitoringConfig("1.1.1.1").intervalMs)
    }

    @Test
    fun sensible_minimum_interval_is_enforced() {
        assertEquals(10_000L, MonitoringConfig.MIN_INTERVAL_MS)
    }

    @Test
    fun interval_validation() {
        assertTrue(MonitoringConfig.isValidInterval(MonitoringConfig.MIN_INTERVAL_MS))
        assertTrue(MonitoringConfig.isValidInterval(60_000L))
        assertTrue(MonitoringConfig.isValidInterval(3_600_000L))
        assertFalse(MonitoringConfig.isValidInterval(9_999L))
        assertFalse(MonitoringConfig.isValidInterval(0L))
        assertFalse(MonitoringConfig.isValidInterval(-1_000L))
    }

    @Test
    fun interval_coercion_never_silently_goes_below_minimum() {
        assertEquals(MonitoringConfig.MIN_INTERVAL_MS, MonitoringConfig.coerceInterval(1_000L))
        assertEquals(MonitoringConfig.MIN_INTERVAL_MS, MonitoringConfig.coerceInterval(MonitoringConfig.MIN_INTERVAL_MS))
        assertEquals(300_000L, MonitoringConfig.coerceInterval(300_000L))
    }
}
