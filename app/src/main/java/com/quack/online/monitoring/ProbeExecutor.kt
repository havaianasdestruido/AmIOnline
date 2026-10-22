package com.quack.online.monitoring

import com.quack.online.network.PingResult
import com.quack.online.network.PingRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull
import kotlinx.coroutines.flow.toList

/**
 * Executes a single monitoring probe. The real implementation delegates to [PingRunner]
 * with `count = 1`, so every completed run maps to exactly one attempt — keeping the
 * attempt/success counters unambiguous.
 */
interface ProbeExecutor {
    suspend fun executeProbe(target: String, timeoutSeconds: Int): ProbeResult
}

class RealProbeExecutor : ProbeExecutor {
    override suspend fun executeProbe(target: String, timeoutSeconds: Int): ProbeResult =
        withContext(Dispatchers.IO) {
            val lines: List<PingResult> = withTimeoutOrNull((timeoutSeconds.toLong() * 1000 + 5_000)) {
                PingRunner().executePing(target, count = 1, timeoutSeconds = timeoutSeconds).toList()
            } ?: return@withContext ProbeResult.ExecutorError(
                "Ping timed out after ${timeoutSeconds}s"
            )

            val error = lines.filterIsInstance<PingResult.Error>().firstOrNull()
            if (error != null) {
                return@withContext ProbeResult.ExecutorError(error.message ?: "Ping failed to start")
            }

            val finished = lines.filterIsInstance<PingResult.Finished>().lastOrNull()
            when {
                finished == null -> ProbeResult.ExecutorError("Ping process produced no result")
                finished.repliesReceived > 0 -> ProbeResult.Success
                else -> ProbeResult.Failed(
                    "No reply received (exit code ${finished.exitCode})"
                )
            }
        }
}
