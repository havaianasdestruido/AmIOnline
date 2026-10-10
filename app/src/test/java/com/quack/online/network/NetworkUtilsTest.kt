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

        assertEquals(NetworkUtils.AddressType.INVALID, NetworkUtils.getAddressType(""))
        assertEquals(NetworkUtils.AddressType.INVALID, NetworkUtils.getAddressType("256.256.256.256"))
    }

    @Test
    fun testValidHostnameEndsAtInputBoundary() {
        assertTrue(NetworkUtils.isValidHostname("example.com"))
    }

    @Test
    fun testBuildPingCommand() {
        val ipv4Cmd = NetworkUtils.buildPingCommand("1.1.1.1", 4, 3)
        assertEquals(listOf("ping", "-c", "4", "-W", "3", "1.1.1.1"), ipv4Cmd)

        val ipv6Cmd = NetworkUtils.buildPingCommand("2606:4700:4700::1111", 5, 2)
        assertEquals(listOf("ping6", "-c", "5", "-W", "2", "2606:4700:4700::1111"), ipv6Cmd)
    }
}
