package com.quack.online.network

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

        val isIpv6 = addressType == NetworkUtils.AddressType.IPV6
        val command = NetworkUtils.buildPingCommand(trimmedTarget, isIpv6, count, timeoutSeconds)
        var process: Process? = null

        try {
            val processBuilder = ProcessBuilder(command)
            processBuilder.redirectErrorStream(true)
            process = processBuilder.start()

            val reader = BufferedReader(InputStreamReader(process.inputStream))
            var line: String?

            while (reader.readLine().also { line = it } != null) {
                line?.let {
                    emit(PingResult.Output(it))
                }
            }

            val exitCode = process.waitFor()
            val isSuccess = (exitCode == 0)
            emit(PingResult.Finished(exitCode, isSuccess))
        } catch (e: Exception) {
            // Fallback attempt: if ping6 binary is missing or blocked, try standard ping command
            if (command.contains("ping6")) {
                val fallbackCmd = listOf("ping", "-c", count.toString(), "-W", timeoutSeconds.toString(), trimmedTarget)
                try {
                    val pb = ProcessBuilder(fallbackCmd).redirectErrorStream(true)
                    process = pb.start()
                    val reader = BufferedReader(InputStreamReader(process.inputStream))
                    var line: String?
                    while (reader.readLine().also { line = it } != null) {
                        line?.let { emit(PingResult.Output(it)) }
                    }
                    val exitCode = process.waitFor()
                    emit(PingResult.Finished(exitCode, exitCode == 0))
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
