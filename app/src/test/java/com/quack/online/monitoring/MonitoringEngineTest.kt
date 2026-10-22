package com.quack.online.monitoring

import kotlinx.coroutines.runBlocking
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertTrue
import org.junit.Test

class MonitoringEngineTest {

    private fun newEngine(repo: FakeMonitoringRepository = FakeMonitoringRepository.active(),
                          notifier: FakeNotifier = FakeNotifier(),
                          executor: FakeProbeExecutor = FakeProbeExecutor()): MonitoringEngine {
        repo.config = MonitoringConfig("1.1.1.1", 60_000L, "1.1.1.1", "1.1.1.1")
        return MonitoringEngine(repo, notifier, executor)
    }

    /**
     * Waits for [condition] instead of sleeping a fixed amount of time.
     *
     * The engine runs its loop on Dispatchers.Default, so a fixed sleep can be too short on a
     * loaded machine and make these tests flaky.
     */
    private fun awaitUntil(timeoutMs: Long = 5_000, condition: () -> Boolean) {
        val deadline = System.currentTimeMillis() + timeoutMs
        while (!condition() && System.currentTimeMillis() < deadline) {
            Thread.sleep(10)
        }
        assertTrue("condition was not met within ${timeoutMs}ms", condition())
    }

    @Test
    fun successful_probe_counts_one_attempt_and_shows_online() = runBlocking {
        val repo = FakeMonitoringRepository.active()
        val notifier = FakeNotifier()
        val executor = FakeProbeExecutor().apply { queue.add(ProbeResult.Success) }
        val engine = MonitoringEngine(repo, notifier, executor)

        engine.start()
        // One cycle runs immediately (interval delay is long).
        awaitUntil { repo.stats.totalAttempts == 1L }
        engine.stop()

        assertEquals(1L, repo.stats.totalAttempts)
        assertEquals(1L, repo.stats.successfulAttempts)
        assertEquals(0L, repo.stats.failedAttempts)
        assertEquals(MonitoringStatus.ONLINE, notifier.lastStatus)
        assertEquals("1.1.1.1", notifier.lastServerIp)
        assertEquals(1, executor.invocationCount)
    }

    @Test
    fun failed_probe_counts_attempt_but_not_success() = runBlocking {
        val repo = FakeMonitoringRepository.active()
        val notifier = FakeNotifier()
        val executor = FakeProbeExecutor().apply { queue.add(ProbeResult.Failed("No reply received (exit code 1)")) }
        val engine = MonitoringEngine(repo, notifier, executor)

        engine.start()
        awaitUntil { repo.stats.totalAttempts == 1L }
        engine.stop()

        assertEquals(1L, repo.stats.totalAttempts)
        assertEquals(0L, repo.stats.successfulAttempts)
        assertEquals(1L, repo.stats.failedAttempts)
        assertEquals(MonitoringStatus.OFFLINE, notifier.lastStatus)
    }

    @Test
    fun executor_error_does_not_touch_counters() = runBlocking {
        val repo = FakeMonitoringRepository.active()
        val notifier = FakeNotifier()
        val executor = FakeProbeExecutor().apply { queue.add(ProbeResult.ExecutorError("Failed to execute ping: binary missing")) }
        val engine = MonitoringEngine(repo, notifier, executor)

        engine.start()
        awaitUntil { notifier.lastStatus == MonitoringStatus.ERROR }
        engine.stop()

        assertEquals(0L, repo.stats.totalAttempts)
        assertEquals(0L, repo.stats.successfulAttempts)
        assertEquals(MonitoringStatus.ERROR, notifier.lastStatus)
    }

    @Test
    fun repeated_cycles_accumulate_counters_without_overlap() = runBlocking {
        val repo = FakeMonitoringRepository.active()
        val notifier = FakeNotifier()
        val executor = FakeProbeExecutor().apply {
            queue.add(ProbeResult.Success)
            queue.add(ProbeResult.Failed("timeout"))
            queue.add(ProbeResult.Success)
        }
        // A 60s interval means only the first cycle runs before we stop; to exercise accumulation
        // we run three cycles back to back by restarting the engine.
        val engine = MonitoringEngine(repo, notifier, executor)
        repeat(3) { index ->
            engine.start()
            awaitUntil { repo.stats.totalAttempts == (index + 1).toLong() }
            engine.stop()
        }

        assertEquals(3L, repo.stats.totalAttempts)
        assertEquals(2L, repo.stats.successfulAttempts)
        assertEquals(1L, repo.stats.failedAttempts)
        assertEquals(3, executor.invocationCount)
    }

    @Test
    fun starting_an_already_running_engine_does_not_spawn_a_duplicate_loop() = runBlocking {
        val executor = FakeProbeExecutor().apply { queue.add(ProbeResult.Success) }
        val engine = newEngine(executor = executor)

        engine.start()
        engine.start() // second start must be a no-op
        awaitUntil { executor.invocationCount == 1 }
        // Give a (wrongly) duplicated loop the chance to run a second cycle.
        Thread.sleep(150)
        engine.stop()

        assertEquals(1, executor.invocationCount)
        assertTrue(engine.isRunning == false)
    }

    @Test
    fun reschedule_restarts_the_loop() = runBlocking {
        val executor = FakeProbeExecutor().apply { queue.add(ProbeResult.Success) }
        val engine = newEngine(executor = executor)

        engine.start()
        awaitUntil { executor.invocationCount == 1 }
        engine.reschedule() // cancel + restart; one more cycle begins immediately
        awaitUntil { executor.invocationCount == 2 }
        engine.stop()

        assertEquals(2, executor.invocationCount)
    }

    @Test
    fun failed_dns_style_error_preserves_existing_counters() = runBlocking {
        val repo = FakeMonitoringRepository.active().apply {
            stats // touch
        }
        repo.setStats(MonitoringStats(totalAttempts = 10, successfulAttempts = 8, lastCheckMs = 1_000L))
        val notifier = FakeNotifier()
        val executor = FakeProbeExecutor().apply { queue.add(ProbeResult.ExecutorError("unknown host")) }
        val engine = MonitoringEngine(repo, notifier, executor)

        engine.start()
        awaitUntil { notifier.lastStatus == MonitoringStatus.ERROR }
        engine.stop()

        // Counters unchanged: DNS/resolution errors are not counted as transmitted probes.
        assertEquals(10L, repo.stats.totalAttempts)
        assertEquals(8L, repo.stats.successfulAttempts)
        assertEquals(MonitoringStatus.ERROR, notifier.lastStatus)
    }

    @Test
    fun probe_target_is_the_resolved_ip() = runBlocking {
        val repo = FakeMonitoringRepository.active().apply {
            config = MonitoringConfig("google.com", 60_000L, "2001:4860:4860::8888", "google.com")
        }
        val notifier = FakeNotifier()
        val executor = FakeProbeExecutor().apply { queue.add(ProbeResult.Success) }
        val engine = MonitoringEngine(repo, notifier, executor)

        engine.start()
        awaitUntil { executor.lastTarget != null }
        engine.stop()

        assertNotNull(executor.lastTarget)
        assertEquals("2001:4860:4860::8888", executor.lastTarget)
    }
}
