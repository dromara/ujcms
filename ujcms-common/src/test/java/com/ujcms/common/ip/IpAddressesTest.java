package com.ujcms.common.ip;

import org.junit.jupiter.api.Test;

import java.net.Inet4Address;
import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertInstanceOf;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP 字面量解析、规范化、限流分组键与内网判定。全部使用字面量，不触发 DNS。
 */
class IpAddressesTest {

    @Test
    void canonicalizeAcceptsAllTextualForms() {
        Map<String, String> cases = Map.ofEntries(
                Map.entry("203.0.113.9", "203.0.113.9"),
                Map.entry(" 203.0.113.9 ", "203.0.113.9"),
                Map.entry("203.0.113.9:5678", "203.0.113.9"),
                Map.entry("2001:db8::1", "2001:db8::1"),
                Map.entry("2001:DB8:0:0:0:0:0:1", "2001:db8::1"),
                Map.entry("0:0:0:0:0:0:0:1", "::1"),
                Map.entry("2001:db8::", "2001:db8::"),
                Map.entry("fe80::", "fe80::"),
                Map.entry("[2001:db8::1]", "2001:db8::1"),
                Map.entry("[2001:db8::1]:443", "2001:db8::1"),
                Map.entry("fe80::1%eth0", "fe80::1"),
                Map.entry("fe80::1%3", "fe80::1"),
                Map.entry("[fe80::1%25eth0]:8080", "fe80::1"),
                Map.entry("::ffff:203.0.113.9", "203.0.113.9"),
                Map.entry("::ffff:cb00:7109", "203.0.113.9"),
                Map.entry("0:0:0:0:0:ffff:10.1.2.3", "10.1.2.3"),
                Map.entry("64:ff9b::203.0.113.9", "64:ff9b::cb00:7109"),
                // 不带方括号的 IPv6 不去端口：本身即合法地址
                Map.entry("2001:db8::1:443", "2001:db8::1:443"));
        cases.forEach((input, expected) -> assertEquals(expected, IpAddresses.canonicalize(input), input));
        assertInstanceOf(Inet4Address.class, IpAddresses.parseLiteral("::ffff:203.0.113.9"));
        assertInstanceOf(Inet6Address.class, IpAddresses.parseLiteral("::203.0.113.9"));
    }

    @Test
    void canonicalNeverCarriesBracketsPortOrZone() {
        for (String input : List.of("[2001:db8::1]:443", "fe80::1%eth0", "[fe80::1%25eth0]:8080", "203.0.113.9:80")) {
            String canonical = IpAddresses.canonicalize(input);
            assertTrue(canonical != null && !canonical.contains("[") && !canonical.contains("]")
                    && !canonical.contains("%"), input + " -> " + canonical);
            assertTrue(canonical.length() <= 39, input);
        }
    }

    @Test
    void canonicalStripsScopeFromScopedAddress() throws UnknownHostException {
        // 直接构造带网卡标识的地址（Tomcat 可能给出），规范化后不得带 %
        Inet6Address scoped = Inet6Address.getByAddress(null,
                InetAddress.getByName("fe80::1").getAddress(), 5);
        assertEquals("fe80::1", IpAddresses.canonical(scoped));
    }

    @Test
    void parseLiteralRejectsHostnamesAndGarbage() {
        List<String> rejected = List.of("gggg::1", "zz:yy:xx", "1.2.3.4.5", "999.999.999.999", "0177.0.0.1",
                "256.1.1.1", "1.2.3", "1.2.3.4:", "1.2.3.4:123456", "[2001:db8::1", "[2001:db8::1]x", "[1.2.3.4]",
                "example.com", "localhost", "unknown", "", "   ", "2130706433", "1234", "1.2.3.04");
        for (String input : rejected) {
            assertNull(IpAddresses.parseLiteral(input), input);
        }
        assertNull(IpAddresses.parseLiteral(null));
        assertNull(IpAddresses.canonicalize(null));
    }

    @Test
    void throttleKeyGroupsIpv6ByPrefixAndKeepsIpv4() {
        assertEquals("203.0.113.9", IpAddresses.throttleKey("203.0.113.9"));
        assertEquals("203.0.113.9", IpAddresses.throttleKey("203.0.113.9:5678"));
        assertEquals("203.0.113.9", IpAddresses.throttleKey("::ffff:203.0.113.9"));
        assertEquals("2001:db8:1:2::/64", IpAddresses.throttleKey("2001:db8:1:2:3:4:5:6"));
        assertEquals("2001:db8:1:2::/64", IpAddresses.throttleKey("[2001:db8:1:2::abcd]:443"));
        assertEquals("2001:db8:1:2::/64", IpAddresses.throttleKey("2001:DB8:1:2::1%eth0"));
        assertEquals("::/64", IpAddresses.throttleKey("::1"));
        // 同一 /64 内不同地址同键，相邻 /64 不同键
        assertEquals(IpAddresses.throttleKey("2001:db8:1:2::1"), IpAddresses.throttleKey("2001:db8:1:2:ffff::1"));
        assertNotEquals(IpAddresses.throttleKey("2001:db8:1:2::1"), IpAddresses.throttleKey("2001:db8:1:3::1"));
        // 可配置前缀
        assertEquals("2001:db8:1::/48", IpAddresses.throttleKey("2001:db8:1:2::1", 48));
        assertEquals("2001:db8:1:2::1", IpAddresses.throttleKey("2001:db8:1:2::1", 128));
        assertEquals("2001:db8:1:2::1", IpAddresses.throttleKey("2001:db8:1:2::1", 200));
        // 无法解析：原串
        assertEquals("gggg::1", IpAddresses.throttleKey("gggg::1"));
        assertEquals("", IpAddresses.throttleKey(null));
        assertEquals("", IpAddresses.throttleKey("  "));
    }

    private static boolean internal(String literal) {
        InetAddress address = IpAddresses.parseLiteral(literal);
        assertTrue(address != null, "not a literal: " + literal);
        return IpAddresses.isInternal(address);
    }

    @Test
    void isInternalCoversPrivateReservedAndEmbeddedRanges() {
        List<String> internal = List.of(
                "127.0.0.1", "127.1.2.3", "0.0.0.0", "0.1.2.3", "10.0.0.1", "172.16.0.1", "172.31.255.255",
                "192.168.1.1", "169.254.169.254", "224.0.0.1", "239.255.255.255",
                "100.100.100.200", "100.64.0.1", "100.127.255.255", "192.0.0.1", "198.18.0.1", "198.19.255.255",
                "240.0.0.1", "255.255.255.255",
                "::1", "::", "fe80::1", "fec0::1", "ff02::1", "fc00::1", "fd12:3456::1",
                "64:ff9b::7f00:1", "64:ff9b::a00:1", "64:ff9b:1::1", "100::1",
                "2002:7f00:1::1", "2002:c0a8:101::1", "::10.0.0.1", "::ffff:10.0.0.5");
        for (String literal : internal) {
            assertTrue(internal(literal), literal);
        }
        List<String> external = List.of(
                "8.8.8.8", "1.1.1.1", "203.0.113.9", "192.0.2.1", "198.51.100.1",
                "100.63.255.255", "100.128.0.1", "192.0.1.1", "198.17.255.255",
                "2001:4860:4860::8888", "2001:db8::1", "64:ff9b::808:808", "2002:808:808::1", "::8.8.8.8");
        for (String literal : external) {
            assertFalse(internal(literal), literal);
        }
    }

    @Test
    void isInternalHostHandlesLiteralsAndLocalNamesWithoutDns() throws UnknownHostException {
        for (String host : List.of("[::1]", "::1", "127.0.0.1", "[fd00::1]", "localhost", "LOCALHOST",
                "foo.localhost", "nas.local", "", "   ", "0177.0.0.1", "1.2.3.4.5", "gggg::1", "2130706433",
                "100.100.100.200", "[64:ff9b::7f00:1]")) {
            assertTrue(IpAddresses.isInternalHost(host), host);
        }
        for (String host : List.of("8.8.8.8", "[2001:4860:4860::8888]", "2001:db8::1")) {
            assertFalse(IpAddresses.isInternalHost(host), host);
        }
    }
}
