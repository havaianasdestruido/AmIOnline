package com.quack.online.network

import java.net.Inet6Address
import java.net.InetAddress

object NetworkUtils {

    enum class AddressType {
        IPV4,
        IPV6,
        HOSTNAME,
        INVALID
    }

    fun getAddressType(input: String): AddressType {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return AddressType.INVALID

        // Check if IPv4 format (x.x.x.x)
        if (isIpv4Address(trimmed)) {
            return AddressType.IPV4
        }

        // Check if IPv6 format (contains colon and valid hex groups)
        if (isIpv6Address(trimmed)) {
            return AddressType.IPV6
        }

        // Check basic hostname format
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

    // Compiled once per process instead of on every call. Avoids using android.util.Patterns
    // which breaks local unit tests. Regex matches standard IPv6 formats.
    private val ipv6Regex = Regex(
        "^((?:[0-9a-fA-F]{1,4}:){7}[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,7}:|(?:[0-9a-fA-F]{1,4}:){1,6}:[0-9a-fA-F]{1,4}|(?:[0-9a-fA-F]{1,4}:){1,5}(?::[0-9a-fA-F]{1,4}){1,2}|(?:[0-9a-fA-F]{1,4}:){1,4}(?::[0-9a-fA-F]{1,4}){1,3}|(?:[0-9a-fA-F]{1,4}:){1,3}(?::[0-9a-fA-F]{1,4}){1,4}|(?:[0-9a-fA-F]{1,4}:){1,2}(?::[0-9a-fA-F]{1,4}){1,5}|[0-9a-fA-F]{1,4}:(?:(?::[0-9a-fA-F]{1,4}){1,6})|:(?:(?::[0-9a-fA-F]{1,4}){1,7}|:)|fe80:(?::[0-9a-fA-F]{0,4}){0,4}%[0-9a-zA-Z]{1,}|::(?:ffff(?::0{1,4}){0,1}:){0,1}(?:(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9])|(?:[0-9a-fA-F]{1,4}:){1,4}:(?:(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9])\\.){3,3}(?:25[0-5]|(?:2[0-4]|1{0,1}[0-9]){0,1}[0-9]))$"
    )

    fun isIpv6Address(input: String): Boolean {
        if (!input.contains(":")) return false
        return ipv6Regex.matches(input)
    }

    // Compiled once per process instead of on every call: getAddressType() runs
    // for each ping, and Regex(...) would otherwise call Pattern.compile each time.
    private val hostnameRegex = Regex("^(?=.{1,253}\$)(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+[a-zA-Z]{2,63}\$")

    fun isValidHostname(input: String): Boolean {
        if (input.length > 253 || input.isEmpty()) return false
        return hostnameRegex.matches(input) || input.equals("localhost", ignoreCase = true)
    }

    fun buildPingCommand(target: String, isIpv6: Boolean, count: Int = 4, timeoutSeconds: Int = 5): List<String> {
        val cmd = mutableListOf<String>()
        if (isIpv6) {
            cmd.add("ping6")
        } else {
            cmd.add("ping")
        }

        cmd.add("-c")
        cmd.add(count.toString())
        cmd.add("-W")
        cmd.add(timeoutSeconds.toString())
        cmd.add(target.trim())

        return cmd
    }
}
