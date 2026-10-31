package com.quack.online.monitoring

import com.quack.online.network.PingResult
import com.quack.online.network.PingRunner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import kotlinx.coroutines.withTimeoutOrNull

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
            var error: PingResult.Error? = null
            var finished: PingResult.Finished? = null

            val completed = withTimeoutOrNull((timeoutSeconds.toLong() * 1000 + 5_000)) {
                PingRunner().executePing(target, count = 1, timeoutSeconds = timeoutSeconds).collect { result ->
                    when (result) {
                        is PingResult.Error -> if (error == null) error = result
                        is PingResult.Finished -> finished = result
                        is PingResult.Output -> { /* unused in monitoring */ }
                    }
                }
                true
            }

            if (completed == null) {
                return@withContext ProbeResult.ExecutorError("Ping timed out after ${timeoutSeconds}s")
            }

            val finalError = error
            if (finalError != null) {
                return@withContext ProbeResult.ExecutorError(finalError.message ?: "Ping failed to start")
            }

            val finalFinished = finished
            when {
                finalFinished == null -> ProbeResult.ExecutorError("Ping process produced no result")
                finalFinished.repliesReceived > 0 -> ProbeResult.Success
                else -> ProbeResult.Failed(
                    "No reply received (exit code ${finalFinished.exitCode})"
                )
            }
        }
}
