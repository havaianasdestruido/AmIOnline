package com.quack.online.network

import java.net.Inet6Address
import java.net.InetAddress
import java.net.UnknownHostException

object NetworkUtils {
    enum class AddressType { IPV4, IPV6, HOSTNAME, INVALID }

    fun getAddressType(input: String): AddressType {
        val trimmed = input.trim()
        if (trimmed.isEmpty()) return AddressType.INVALID
        if (isIpv4Address(trimmed)) return AddressType.IPV4
        if (isIpv6Address(trimmed)) return AddressType.IPV6
        if (isValidHostname(trimmed)) return AddressType.HOSTNAME
        return AddressType.INVALID
    }

    // Parse IPv4 without allocating an array via String.split().
    fun isIpv4Address(input: String): Boolean {
        var dots = 0
        var num = 0
        var len = 0
        for (i in input.indices) {
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
            } else return false
        }
        if (len == 0 || num > 255) return false
        if (len > 1 && input[input.length - len] == '0') return false
        return dots == 3
    }

    /**
     * Validate numeric IPv6 literals with the JDK parser rather than a large regex.
     *
     * Only strings that can possibly be numeric IPv6 literals are handed to
     * [InetAddress.getByName], so host names (e.g. "host:port") never trigger a blocking
     * name lookup (see `.jules/bolt.md`, 2026-10-15).
     */
    fun isIpv6Address(input: String): Boolean {
        if (!input.contains(':')) return false
        if (!looksLikeNumericIpv6(input)) return false
        return try {
            InetAddress.getByName(input) is Inet6Address
        } catch (_: Exception) {
            false
        }
    }

    /** True when [input] only contains characters allowed in an IPv6 literal or its zone id. */
    private fun looksLikeNumericIpv6(input: String): Boolean {
        val zoneIndex = input.indexOf('%')
        val addressPart = if (zoneIndex >= 0) input.substring(0, zoneIndex) else input
        if (addressPart.isEmpty()) return false
        for (c in addressPart) {
            val isHexDigit = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            if (!isHexDigit && c != ':' && c != '.') return false
        }
        // Zone ids (e.g. "fe80::1%eth0") may contain ASCII letters, digits, '_' and '-'.
        if (zoneIndex >= 0) {
            val zone = input.substring(zoneIndex + 1)
            if (zone.isEmpty()) return false
            val allowed = zone.all {
                it in '0'..'9' || it in 'a'..'z' || it in 'A'..'Z' || it == '_' || it == '-'
            }
            if (!allowed) return false
        }
        return true
    }

    private val hostnameRegex =
        Regex("^(?=.{1,253}\\$)(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+[a-zA-Z]{2,63}\\$")

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

    fun buildPingCommand(
        target: String,
        isIpv6: Boolean,
        count: Int = 4,
        timeoutSeconds: Int = 5
    ): List<String> {
        require(count > 0) { "Ping count must be greater than zero" }
        require(timeoutSeconds > 0) { "Ping timeout must be greater than zero" }
        return buildList {
            add(if (isIpv6) "ping6" else "ping")
            add("-c")
            add(count.toString())
            add("-W")
            add(timeoutSeconds.toString())
            add(target.trim())
        }
    }
}
