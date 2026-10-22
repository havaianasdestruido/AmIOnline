package com.quack.online.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

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

    // Zero-allocation IPv4 parsing optimized to avoid String.split and mapping overheads
    // which cause significant GC pauses during heavy network polling
    fun isIpv4Address(input: String): Boolean {
        var dots = 0
        var num = 0
        var len = 0
        for (i in 0 until input.length) {
            val c = input[i]
            if (c == '.') {
                if (len == 0 || num > 255) return false
                if (len > 1 && input[i - len] == '0') return false
                dots++
                if (dots > 3) return false
                num = 0
                len = 0
            } else if (c in '0'..'9') {
                num = num * 10 + (c - '0')
                len++
                if (len > 3) return false
            } else {
                return false
            }
        }
        if (len == 0 || num > 255) return false
        if (len > 1 && input[input.length - len] == '0') return false
        return dots == 3
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

    /**
     * Resolves [target] to its first IP address for display purposes.
     * Literal IP addresses are returned as-is. Throws [UnknownHostException] when the
     * address cannot be resolved; callers handle this gracefully (error UI, no false counters).
     */
    fun resolveIpAddress(target: String): String {
        val address = InetAddress.getByName(target.trim())
        // Strip IPv6 zone id (e.g. "fe80::1%eth0") for display and for the ping command
        return address.hostAddress.substringBefore('%')
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
