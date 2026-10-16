package com.quack.online.network

import org.junit.Assert.*
import org.junit.Test

class NetworkUtilsRegexTest {
    @Test
    fun testIpv6() {
        assertTrue(NetworkUtils.isIpv6Address("2606:4700:4700::1111"))
        assertFalse(NetworkUtils.isIpv6Address("aaaa:bbbb"))
    }
}
