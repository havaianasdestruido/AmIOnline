package com.quack.online.monitoring

import com.quack.online.network.PingResult
import com.quack.online.network.PingRunner
import kotlinx.coroutines.flow.toList
import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Real network-probe tests for the ping counting model used by monitoring.
 *
 * Monitoring invokes PingRunner with `count = 1`, so every completed run maps to exactly one
 * attempt. These tests execute the system `ping` binary available on the host running the test
 * (localhost is always reachable; 192.0.2.1 is a TEST-NET address that must not reply).
 */
class PingRunnerMonitoringTest {

    @Test
    fun single_probe_to_localhost_counts_one_successful_attempt() = runBlocking {
        val results = PingRunner().executePing("127.0.0.1", count = 1, timeoutSeconds = 2).toList()

        val finished = results.filterIsInstance<PingResult.Finished>().singleOrNull()
        assertNotNull("expected a Finished result", finished)
        assertTrue("localhost should reply", finished!!.isSuccess)
        assertTrue("at least one reply line should be parsed", finished.repliesReceived >= 1)
        assertEquals(0, results.filterIsInstance<PingResult.Error>().size)
    }

    @Test
    fun probe_to_unreachable_address_counts_failed_attempt_not_success() = runBlocking {
        // TEST-NET-1 address: routable but unused, must not reply to ICMP.
        val results = PingRunner().executePing("192.0.2.1", count = 1, timeoutSeconds = 3).toList()

        val finished = results.filterIsInstance<PingResult.Finished>().singleOrNull()
        assertNotNull("expected a Finished result", finished)
        assertFalse("no reply must not be success", finished!!.isSuccess)
        assertEquals(0, finished.repliesReceived)
    }

    @Test
    fun missing_target_emits_error_and_counts_nothing() = runBlocking {
        val results = PingRunner().executePing("", count = 1, timeoutSeconds = 2).toList()
        val error = results.filterIsInstance<PingResult.Error>().singleOrNull()
        assertNotNull("empty target must produce an error", error)
        assertTrue(results.filterIsInstance<PingResult.Finished>().isEmpty())
    }

    @Test
    fun unresolvable_hostname_emits_error() = runBlocking {
        val results = PingRunner().executePing("this-host-does-not-exist.invalid", count = 1, timeoutSeconds = 3).toList()
        val hasError = results.any { it is PingResult.Error }
        val finished = results.filterIsInstance<PingResult.Finished>()
        // The process either fails to start (error) or finishes without replies (failed).
        // Either way it must never be counted as successful.
        assertTrue(hasError || finished.all { !it.isSuccess && it.repliesReceived == 0 })
    }
}
