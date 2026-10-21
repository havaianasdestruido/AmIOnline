package com.quack.online.network

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
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

        val isIpv6 = addressType == NetworkUtils.AddressType.IPV6
        val command = NetworkUtils.buildPingCommand(trimmedTarget, isIpv6, count, timeoutSeconds)
        var process: Process? = null

        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?
            var repliesReceived = 0

            while (reader.readLine().also { line = it } != null) {
                line?.let { emittedLine ->
                    emit(PingResult.Output(emittedLine))
                    // A reply line looks like "64 bytes from 1.1.1.1: icmp_seq=1 ttl=56 time=5 ms"
                    if (emittedLine.contains("bytes from", ignoreCase = true)) {
                        repliesReceived++
                    }
                }
            }

            val exitCode = process.waitFor()
            val isSuccess = repliesReceived > 0
            emit(PingResult.Finished(exitCode, repliesReceived, isSuccess))
        } catch (e: Exception) {
            // Fallback attempt: if ping6 binary is missing or blocked, try standard ping command
            if (command.contains("ping6")) {
                val fallbackCmd = listOf("ping", "-c", count.toString(), "-W", timeoutSeconds.toString(), trimmedTarget)
                try {
                    // ⚡ Destroy the original process before re-assigning to avoid leaking process and file descriptors
                    process?.destroy()

                    val pb = ProcessBuilder(fallbackCmd).redirectErrorStream(true)
                    process = pb.start()
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    var repliesReceived = 0
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { emittedLine ->
                            emit(PingResult.Output(emittedLine))
                            if (emittedLine.contains("bytes from", ignoreCase = true)) {
                                repliesReceived++
                            }
                        }
                    }
                    val exitCode = process.waitFor()
                    val isSuccess = repliesReceived > 0
                    emit(PingResult.Finished(exitCode, repliesReceived, isSuccess))
                    return@flow
                } catch (fallbackEx: Exception) {
                    emit(PingResult.Error("Failed to execute ping: ${e.localizedMessage ?: e.message}"))
                }
            } else {
                emit(PingResult.Error("Failed to execute ping: ${e.localizedMessage ?: e.message}"))
            }
        } finally {
            process?.destroy()
        }
    }.flowOn(Dispatchers.IO)
}
