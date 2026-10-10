package com.quack.online.network

import java.net.Inet6Address
import java.net.InetAddress

object NetworkUtils {

    // Compile once rather than creating a new regular expression for each target validation.
    private val hostnameRegex = Regex(
        "^(?=.{1,253}\\$)(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+[a-zA-Z]{2,63}\\$"
    )

    enum class AddressType {
        IPV4,
        IPV6,
        HOSTNAME,
        INVALID
    }

    fun getAddressType(input: String): AddressType {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return AddressType.INVALID

        if (isIpv4Address(trimmed)) {
            return AddressType.IPV4
        }

        if (isIpv6Address(trimmed)) {
            return AddressType.IPV6
        }

        if (isValidHostname(trimmed)) {
            return AddressType.HOSTNAME
        }

        return AddressType.INVALID
    }

    fun isIpv4Address(input: String): Boolean {
        val parts = input.split(".")
        if (parts.size != 4) return false
        return parts.all { part ->
            part.toIntOrNull()?.let { it in 0..255 && (part == "0" || !part.startsWith("0")) } ?: false
        }
    }

    fun isIpv6Address(input: String): Boolean {
        if (!input.contains(":")) return false
        return try {
            val addr = InetAddress.getByName(input)
            addr is Inet6Address
        } catch (e: Exception) {
            false
        }
    }

    fun isValidHostname(input: String): Boolean {
        if (input.length > 253 || input.isEmpty()) return false
        return hostnameRegex.matches(input) || input.equals("localhost", ignoreCase = true)
    }

    fun buildPingCommand(target: String, count: Int = 4, timeoutSeconds: Int = 5): List<String> =
        buildPingCommand(target, count, timeoutSeconds, getAddressType(target))

    /** Reuses a previously computed type when validation and command construction happen together. */
    internal fun buildPingCommand(
        target: String,
        count: Int,
        timeoutSeconds: Int,
        addressType: AddressType
    ): List<String> {
        val command = mutableListOf(if (addressType == AddressType.IPV6) "ping6" else "ping")
        command.add("-c")
        command.add(count.toString())
        command.add("-W")
        command.add(timeoutSeconds.toString())
        command.add(target.trim())
        return command
    }
}
