package com.quack.online.monitoring

/**
 * Outcome of a single monitoring probe.
 *
 * Success/Failure mean the ping process ran and produced a definitive result.
 * ExecutorError means the probe could not be executed at all (missing `ping` binary,
 * process start failure, timeout) — no attempt is counted in that case.
 */
sealed class ProbeResult {
    object Success : ProbeResult()
    data class Failed(val reason: String) : ProbeResult()
    data class ExecutorError(val reason: String) : ProbeResult()
}
