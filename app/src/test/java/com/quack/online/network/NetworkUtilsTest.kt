package com.quack.online.network

import org.junit.Assert.*
import org.junit.Test

class NetworkUtilsTest {
    @Test
    fun testAddressTypeClassification() {
        assertEquals(NetworkUtils.AddressType.IPV4, NetworkUtils.getAddressType("1.1.1.1"))
        assertEquals(NetworkUtils.AddressType.IPV4, NetworkUtils.getAddressType("8.8.8.8"))
        assertEquals(NetworkUtils.AddressType.IPV4, NetworkUtils.getAddressType("127.0.0.1"))
        assertEquals(NetworkUtils.AddressType.IPV6, NetworkUtils.getAddressType("2606:4700:4700::1111"))
        assertEquals(NetworkUtils.AddressType.IPV6, NetworkUtils.getAddressType("2001:4860:4860::8888"))
        assertEquals(NetworkUtils.AddressType.IPV6, NetworkUtils.getAddressType("::1"))
        assertEquals(NetworkUtils.AddressType.HOSTNAME, NetworkUtils.getAddressType("one.one.one.one"))
        assertEquals(NetworkUtils.AddressType.HOSTNAME, NetworkUtils.getAddressType("google.com"))
        assertEquals(NetworkUtils.AddressType.HOSTNAME, NetworkUtils.getAddressType("localhost"))
        assertEquals(NetworkUtils.AddressType.INVALID, NetworkUtils.getAddressType(""))
        assertEquals(NetworkUtils.AddressType.INVALID, NetworkUtils.getAddressType("256.256.256.256"))
        assertEquals(NetworkUtils.AddressType.INVALID, NetworkUtils.getAddressType("2001::db8::1"))
    }

    @Test
    fun testValidHostnameEndsAtInputBoundary() {
        assertTrue(NetworkUtils.isValidHostname("example.com"))
    }

    @Test
    fun testBuildPingCommand() {
        assertEquals(
            listOf("ping", "-c", "4", "-W", "3", "1.1.1.1"),
            NetworkUtils.buildPingCommand("1.1.1.1", false, 4, 3)
        )
        assertEquals(
            listOf("ping6", "-c", "5", "-W", "2", "2606:4700:4700::1111"),
            NetworkUtils.buildPingCommand("2606:4700:4700::1111", true, 5, 2)
        )
    }

    @Test
    fun testBuildPingCommandRejectsNonPositiveLimits() {
        assertThrows(IllegalArgumentException::class.java) {
            NetworkUtils.buildPingCommand("1.1.1.1", false, count = 0)
        }
        assertThrows(IllegalArgumentException::class.java) {
            NetworkUtils.buildPingCommand("1.1.1.1", false, timeoutSeconds = 0)
        }
    }

    @Test
    fun testIsIpv4AddressValid() {
        assertTrue(NetworkUtils.isIpv4Address("1.1.1.1"))
        assertTrue(NetworkUtils.isIpv4Address("8.8.8.8"))
        assertTrue(NetworkUtils.isIpv4Address("127.0.0.1"))
        assertTrue(NetworkUtils.isIpv4Address("192.168.1.1"))
        assertTrue(NetworkUtils.isIpv4Address("255.255.255.255"))
        assertTrue(NetworkUtils.isIpv4Address("0.0.0.0"))
        assertTrue(NetworkUtils.isIpv4Address("10.0.0.1"))
    }

    @Test
    fun testIsIpv4AddressInvalid() {
        assertFalse(NetworkUtils.isIpv4Address(""))
        assertFalse(NetworkUtils.isIpv4Address("1.1.1"))
        assertFalse(NetworkUtils.isIpv4Address("1.1.1.1.1"))
        assertFalse(NetworkUtils.isIpv4Address("256.256.256.256"))
        assertFalse(NetworkUtils.isIpv4Address("192.168.01.1"))
        assertFalse(NetworkUtils.isIpv4Address("a.b.c.d"))
        assertFalse(NetworkUtils.isIpv4Address("192.168.1.1a"))
        assertFalse(NetworkUtils.isIpv4Address("..."))
        assertFalse(NetworkUtils.isIpv4Address("1..1.1.1"))
        assertFalse(NetworkUtils.isIpv4Address("-1.1.1.1"))
        assertFalse(NetworkUtils.isIpv4Address("192.168.1.256"))
    }
}
