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
        val addressLen = if (zoneIndex >= 0) zoneIndex else input.length
        if (addressLen == 0) return false

        // ⚡ Bolt: manual loop over chars avoids String.substring() and intermediate collection allocations
        for (i in 0 until addressLen) {
            val c = input[i]
            val isHexDigit = c in '0'..'9' || c in 'a'..'f' || c in 'A'..'F'
            if (!isHexDigit && c != ':' && c != '.') return false
        }
        // Zone ids (e.g. "fe80::1%eth0") may contain ASCII letters, digits, '_' and '-'.
        if (zoneIndex >= 0) {
            if (zoneIndex + 1 == input.length) return false
            // ⚡ Bolt: manual iteration avoids zone.all {} lambda allocations
            for (i in zoneIndex + 1 until input.length) {
                val c = input[i]
                val allowed = c in '0'..'9' || c in 'a'..'z' || c in 'A'..'Z' || c == '_' || c == '-'
                if (!allowed) return false
            }
        }
        return true
    }

    // Compiled once per process instead of on every call: getAddressType() runs for each ping,
    // and Regex(...) would otherwise call Pattern.compile every time.
    // - The length lookahead (?=.{1,253}$) is redundant: isValidHostname() checks it explicitly.
    // - \z anchors reject a trailing newline that $ would accept.
    private val hostnameRegex = Regex(
        """^(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\.)+[a-zA-Z]{2,63}\z"""
    )

    fun isValidHostname(input: String): Boolean {
        if (input.length > 253 || input.isEmpty()) return false
        // ⚡ Bolt: short-circuit the O(1) localhost check before the O(N) regex
        if (input.equals("localhost", ignoreCase = true)) return true
        return hostnameRegex.matches(input)
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
        // ⚡ Bolt: a fixed listOf avoids the mutableListOf growth/backing-array overhead
        return listOf(
            if (isIpv6) "ping6" else "ping",
            "-c",
            count.toString(),
            "-W",
            timeoutSeconds.toString(),
            target.trim()
        )
    }
}
