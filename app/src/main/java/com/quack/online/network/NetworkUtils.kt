package com.quack.online.network

import java.net.Inet6Address
import java.net.InetAddress

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
     * The colon check prevents getByName() from performing DNS lookups for hostnames.
     */
    fun isIpv6Address(input: String): Boolean {
        if (!input.contains(':')) return false
        return try {
            InetAddress.getByName(input) is Inet6Address
        } catch (_: Exception) {
            false
        }
    }

    private val hostnameRegex =
        Regex("^(?=.{1,253}\\$)(?:(?!-)[a-zA-Z0-9-]{1,63}(?<!-)\\.)+[a-zA-Z]{2,63}\\$")

    fun isValidHostname(input: String): Boolean {
        if (input.length > 253 || input.isEmpty()) return false
        return hostnameRegex.matches(input) || input.equals("localhost", ignoreCase = true)
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
