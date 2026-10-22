package com.quack.online.monitoring

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringNotificationContentTest {

    @Test
    fun title_reports_status_and_server_ip() {
        assertEquals("ONLINE — 1.1.1.1", MonitoringNotificationContent.title("ONLINE", "1.1.1.1"))
        assertEquals("OFFLINE — 8.8.8.8", MonitoringNotificationContent.title("OFFLINE", "8.8.8.8"))
        assertEquals("CHECKING — 1.1.1.1", MonitoringNotificationContent.title("CHECKING", "1.1.1.1"))
        assertEquals("ERROR — 2001:4860:4860::8888", MonitoringNotificationContent.title("ERROR", "2001:4860:4860::8888"))
    }

    @Test
    fun body_contains_attempts_success_and_server_ip() {
        assertEquals(
            "Total attempts: 120 | Successful: 118 | Server IP: 1.1.1.1",
            MonitoringNotificationContent.body(120, 118, "1.1.1.1"),
        )
        assertEquals(
            "Total attempts: 120 | Successful: 93 | Server IP: 8.8.8.8",
            MonitoringNotificationContent.body(120, 93, "8.8.8.8"),
        )
    }

    @Test
    fun expanded_includes_basic_body_plus_extra_details() {
        val stats = MonitoringStats(totalAttempts = 3, successfulAttempts = 2, lastCheckMs = 0)
        val expanded = MonitoringNotificationContent.expanded("ONLINE", "1.1.1.1", stats)
        assertTrue(expanded.contains("ONLINE — 1.1.1.1"))
        assertTrue(expanded.contains("Total attempts: 3 | Successful: 2 | Server IP: 1.1.1.1 | Failed: 1"))
        assertTrue(expanded.contains("Last check: never"))
        assertTrue(expanded.contains("Monitoring continues in the background."))
    }
}
