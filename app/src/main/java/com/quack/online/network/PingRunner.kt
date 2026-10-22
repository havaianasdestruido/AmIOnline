package com.quack.online.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.FlowCollector
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class PingResult {
    data class Output(val line: String) : PingResult()
    data class Finished(
        val exitCode: Int,
        /** Number of probes that produced a valid reply line (e.g. "64 bytes from ..."). */
        val repliesReceived: Int,
        /**
         * True when the probe received at least one valid reply.
         * This is derived from actual ping output lines rather than the process exit code alone,
         * because exit codes can be ambiguous across `ping` implementations.
         */
        val isSuccess: Boolean,
    ) : PingResult()
    data class Error(val message: String) : PingResult()
}

class PingRunner {

    /**
     * Pings [target] once per call when [count] is 1, making every [PingResult.Finished]
     * correspond to exactly one individual probe — which keeps monitoring attempt counting
     * unambiguous (one cycle = one attempt).
     *
     * @param target host name or IP address
     * @param count how many probes `ping` sends in this process (default 4, manual ping style)
     * @param timeoutSeconds seconds to wait for each reply (`-W`)
     */
    fun executePing(target: String, count: Int = 4, timeoutSeconds: Int = 5): Flow<PingResult> = flow {
        val trimmedTarget = target.trim()
        if (trimmedTarget.isEmpty()) {
            emit(PingResult.Error("Target host/IP cannot be empty"))
            return@flow
        }

        val addressType = NetworkUtils.getAddressType(trimmedTarget)
        if (addressType == NetworkUtils.AddressType.INVALID) {
            emit(PingResult.Error("Invalid target IP address or hostname: '$trimmedTarget'"))
            return@flow
        }
        if (count <= 0 || timeoutSeconds <= 0) {
            emit(PingResult.Error("Ping count and timeout must be greater than zero"))
            return@flow
        }

        val isIpv6 = addressType == NetworkUtils.AddressType.IPV6
        // Reuse the classification above instead of parsing the target a second time.
        val command = NetworkUtils.buildPingCommand(trimmedTarget, isIpv6, count, timeoutSeconds)
        var process: Process? = null

        try {
            val startedProcess = startPingProcess(command, isIpv6, trimmedTarget, count, timeoutSeconds)
            process = startedProcess
            val repliesReceived = readOutput(startedProcess)
            val exitCode = startedProcess.waitFor()
            emit(PingResult.Finished(exitCode, repliesReceived, repliesReceived > 0))
        } catch (cancelled: CancellationException) {
            // Cancellation must propagate instead of being reported as a ping failure.
            throw cancelled
        } catch (e: Exception) {
            emit(
                PingResult.Error(
                    "Failed to execute ping: " + (e.localizedMessage ?: e.message ?: "unknown error")
                )
            )
        } finally {
            // Do not leave a native ping process running after collection is cancelled.
            // destroy() is a no-op once the process has already exited and is available on
            // every supported API level (minSdk 24).
            process?.let {
                runCatching { it.inputStream.close() }
                runCatching { it.destroy() }
            }
        }
    }.flowOn(Dispatchers.IO)

    /**
     * Starts the ping process, falling back to `ping -6` when the device has no separate
     * `ping6` binary (some Android builds only ship the unified `ping`).
     */
    private fun startPingProcess(
        command: List<String>,
        isIpv6: Boolean,
        target: String,
        count: Int,
        timeoutSeconds: Int
    ): Process {
        return try {
            ProcessBuilder(command).redirectErrorStream(true).start()
        } catch (startException: Exception) {
            if (!isIpv6) throw startException
            val fallback = listOf(
                "ping", "-6", "-c", count.toString(), "-W", timeoutSeconds.toString(), target
            )
            ProcessBuilder(fallback).redirectErrorStream(true).start()
        }
    }

    /** Emits every output line and returns how many of them were real ICMP replies. */
    private suspend fun FlowCollector<PingResult>.readOutput(process: Process): Int {
        var repliesReceived = 0
        BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                emit(PingResult.Output(line))
                // A reply line looks like "64 bytes from 1.1.1.1: icmp_seq=1 ttl=56 time=5 ms"
                if (line.contains("bytes from", ignoreCase = true)) {
                    repliesReceived++
                }
            }
        }
        return repliesReceived
    }
}
