package com.quack.online.network

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class NetworkUtilsRegexTest {
    @Test
    fun acceptsCommonIpv6Forms() {
        assertTrue(NetworkUtils.isIpv6Address("2606:4700:4700::1111"))
        assertTrue(NetworkUtils.isIpv6Address("2001:4860:4860::8888"))
        assertTrue(NetworkUtils.isIpv6Address("::1"))
        assertTrue(NetworkUtils.isIpv6Address("::"))
        assertTrue(NetworkUtils.isIpv6Address("2001:db8:0:0:0:0:0:1"))
        assertTrue(NetworkUtils.isIpv6Address("2001:db8::1"))
    }

    @Test
    fun rejectsInvalidIpv6AndNonIpv6Input() {
        assertFalse(NetworkUtils.isIpv6Address(""))
        assertFalse(NetworkUtils.isIpv6Address("google.com"))
        assertFalse(NetworkUtils.isIpv6Address("1.1.1.1"))
        assertFalse(NetworkUtils.isIpv6Address("aaaa:bbbb"))
        assertFalse(NetworkUtils.isIpv6Address("2001::db8::1"))
        assertFalse(NetworkUtils.isIpv6Address("2001:db8::gggg"))
        assertFalse(NetworkUtils.isIpv6Address("1:2:3:4:5:6:7:8:9"))
    }
}
