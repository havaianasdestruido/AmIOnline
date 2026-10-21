package com.quack.online.network

import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.io.BufferedReader
import java.io.InputStreamReader

sealed class PingResult {
    data class Output(val line: String) : PingResult()
    data class Finished(val exitCode: Int, val isSuccess: Boolean) : PingResult()
    data class Error(val message: String) : PingResult()
}

class PingRunner {
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
        val command = NetworkUtils.buildPingCommand(trimmedTarget, isIpv6, count, timeoutSeconds)
        var process: Process? = null

        try {
            process = try {
                ProcessBuilder(command).redirectErrorStream(true).start()
            } catch (startException: Exception) {
                // Some Android builds have no ping6 binary but support the -6 option.
                if (!isIpv6) throw startException
                val fallback = listOf(
                    "ping", "-6", "-c", count.toString(), "-W",
                    timeoutSeconds.toString(), trimmedTarget
                )
                ProcessBuilder(fallback).redirectErrorStream(true).start()
            }

            BufferedReader(InputStreamReader(process.inputStream)).use { reader ->
                while (true) {
                    val line = reader.readLine() ?: break
                    emit(PingResult.Output(line))
                }
            }
            val exitCode = process.waitFor()
            emit(PingResult.Finished(exitCode, exitCode == 0))
        } catch (cancelled: CancellationException) {
            // Cancellation must propagate instead of being reported as a ping failure.
            throw cancelled
        } catch (e: Exception) {
            emit(PingResult.Error("Failed to execute ping: " + (e.localizedMessage ?: e.message ?: "unknown error")))
        } finally {
            // Do not leave a native ping process running after collection is cancelled.
            process?.let {
                runCatching { it.inputStream.close() }
                if (it.isAlive) runCatching { it.destroy() }
                if (it.isAlive) runCatching { it.destroyForcibly() }
            }
        }
    }.flowOn(Dispatchers.IO)
}
