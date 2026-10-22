package com.quack.online.monitoring

import java.util.ArrayDeque

/**
 * Scripted [ProbeExecutor] for deterministic engine tests. Results are returned in the order
 * they were enqueued.
 */
class FakeProbeExecutor : ProbeExecutor {
    val queue = ArrayDeque<ProbeResult>()
    var lastTarget: String? = null
    var lastTimeoutSeconds: Int = -1
    var invocationCount: Int = 0

    override suspend fun executeProbe(target: String, timeoutSeconds: Int): ProbeResult {
        lastTarget = target
        lastTimeoutSeconds = timeoutSeconds
        invocationCount++
        return if (queue.isEmpty()) {
            ProbeResult.ExecutorError("no more scripted results")
        } else {
            queue.removeFirst()
        }
    }
}
