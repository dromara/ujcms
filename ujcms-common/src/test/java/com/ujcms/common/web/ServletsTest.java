package com.ujcms.common.web;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;
import org.mockito.MockitoAnnotations;

import jakarta.servlet.ServletRequest;
import jakarta.servlet.http.HttpServletRequest;
import java.util.Vector;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.mockito.Mockito.when;

/**
 * Tests for Servlets utility class, specifically focusing on IP address extraction security
 */
class ServletsTest {

    @Mock
    private HttpServletRequest httpRequest;

    @Mock
    private ServletRequest servletRequest;

    private AutoCloseable mocks;

    @BeforeEach
    void setUp() {
        mocks = MockitoAnnotations.openMocks(this);
    }

    @AfterEach
    void tearDown() throws Exception {
        mocks.close();
    }

    @Test
    void testGetRemoteAddr_DirectConnection() {
        // Test direct connection without proxy
        when(servletRequest.getRemoteAddr()).thenReturn("203.0.113.1");

        String result = Servlets.getRemoteAddr(servletRequest, 2);
        assertEquals("203.0.113.1", result);
    }

    @Test
    void testGetRemoteAddr_BehindProxyWithValidXForwardedFor() {
        // Test behind proxy with valid X-Forwarded-For header
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.1, 10.0.0.2");

        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("203.0.113.1", result);
    }

    @Test
    void testGetRemoteAddr_WithDepthProtection() {
        // Test depth protection against X-Forwarded-For forgery
        // Attack scenario: malicious client tries to forge IP
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For"))
                .thenReturn("1.1.1.1, 2.2.2.2, 203.0.113.1, 10.0.0.2");

        // With depth=2, should only trust the last 2 IPs
        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("203.0.113.1", result);

        // With depth=1, should only trust the last IP (which is internal)
        result = Servlets.getRemoteAddr(httpRequest, 1);
        assertEquals("10.0.0.2", result);
    }

    @Test
    void testGetRemoteAddr_FallbackToRealIpHeader() {
        // Test fallback to X-Real-IP header when X-Forwarded-For is not available
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn("203.0.113.1");

        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("203.0.113.1", result);
    }

    @Test
    void testGetRemoteAddr_FallbackToClientIpHeader() {
        // Test fallback to X-Client-IP header
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn(null);
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getHeader("X-Client-IP")).thenReturn("203.0.113.1");

        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("203.0.113.1", result);
    }

    @Test
    void testGetRemoteAddr_InvalidIpAddresses() {
        // Test with invalid IP addresses in headers
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For"))
                .thenReturn("invalid-ip, 999.999.999.999, 203.0.113.1");

        String result = Servlets.getRemoteAddr(httpRequest, 3);
        assertEquals("203.0.113.1", result);
    }

    @Test
    void testGetRemoteAddr_OnlyInternalIps() {
        // Test when all proxy IPs are internal
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For"))
                .thenReturn("10.0.0.2, 192.168.1.1, 172.16.0.1");

        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("192.168.1.1", result); // Should return the first valid internal IP
    }

    @Test
    void testGetRemoteAddr_EmptyHeaders() {
        // Test with empty or null headers
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("");
        when(httpRequest.getHeader("X-Real-IP")).thenReturn(null);
        when(httpRequest.getHeader("X-Client-IP")).thenReturn(null);

        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("10.0.0.1", result); // Should fallback to direct connection IP
    }

    @Test
    void testGetRemoteAddr_IPv6Support() {
        // Test IPv6 address support
        when(httpRequest.getRemoteAddr()).thenReturn("::1");
        when(httpRequest.getHeader("X-Forwarded-For"))
                .thenReturn("2001:db8::1, 10.0.0.1");

        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("2001:db8::1", result);
    }

    @Test
    void testGetRemoteAddr_MultipleHeaders() {
        // Test with multiple X-Forwarded-For headers (some proxies add multiple headers)
        Vector<String> headers = new Vector<>();
        headers.add("203.0.113.1");
        headers.add("10.0.0.2");

        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeaders("X-Forwarded-For")).thenReturn(headers.elements());

        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("203.0.113.1", result);
    }

    @Test
    void testGetRemoteAddr_SecurityAttackPrevention() {
        // Test prevention of common X-Forwarded-For forgery attacks

        // Attack 1: Attacker tries to inject fake IP at the beginning
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For"))
                .thenReturn("8.8.8.8, 1.1.1.1, 203.0.113.1");

        String result = Servlets.getRemoteAddr(httpRequest, 1); // Only trust 1 level
        assertEquals("203.0.113.1", result); // Should get the real IP, not the forged one

        // Attack 2: Multiple fake IPs with real IP in middle
        when(httpRequest.getHeader("X-Forwarded-For"))
                .thenReturn("fake1.com, 192.168.1.1, 203.0.113.1, 10.0.0.2");

        result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("203.0.113.1", result); // Should get the real public IP
    }

    @Test
    void testGetRemoteAddr_CanonicalizesDirectIpv6() {
        // Tomcat 给出未压缩形式，直连（depth=0）也要规范化
        when(servletRequest.getRemoteAddr()).thenReturn("0:0:0:0:0:0:0:1");
        assertEquals("::1", Servlets.getRemoteAddr(servletRequest, 0));
        when(httpRequest.getRemoteAddr()).thenReturn("2001:DB8:0:0:0:0:0:1");
        assertEquals("2001:db8::1", Servlets.getRemoteAddr(httpRequest, 0));
    }

    @Test
    void testGetRemoteAddr_UnparseableDirectAddressReturnedVerbatimAndTruncated() {
        when(servletRequest.getRemoteAddr()).thenReturn("unix:/var/run/x.sock");
        assertEquals("unix:/var/run/x.sock", Servlets.getRemoteAddr(servletRequest, 2));
        String longValue = "x".repeat(60);
        when(servletRequest.getRemoteAddr()).thenReturn(longValue);
        assertEquals(longValue.substring(0, 45), Servlets.getRemoteAddr(servletRequest, 2));
        when(servletRequest.getRemoteAddr()).thenReturn(null);
        assertNull(Servlets.getRemoteAddr(servletRequest, 2));
    }

    @Test
    void testGetRemoteAddr_Ipv6TextualForms() {
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        // 方括号 + 端口
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("[2001:db8::1]:443, 10.0.0.2");
        assertEquals("2001:db8::1", Servlets.getRemoteAddr(httpRequest, 2));
        // 大写、未压缩
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("2001:DB8:0:0:0:0:0:1");
        assertEquals("2001:db8::1", Servlets.getRemoteAddr(httpRequest, 1));
        // IPv4 映射地址按 IPv4
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("::ffff:203.0.113.9");
        assertEquals("203.0.113.9", Servlets.getRemoteAddr(httpRequest, 1));
        // IPv4 带端口（IIS ARR 等代理的写法）
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("203.0.113.9:5678");
        assertEquals("203.0.113.9", Servlets.getRemoteAddr(httpRequest, 1));
        // 以 :: 结尾
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("2001:db8::");
        assertEquals("2001:db8::", Servlets.getRemoteAddr(httpRequest, 1));
        // 网卡标识去除，且不抛异常
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("fe80::1%eth0");
        assertEquals("fe80::1", Servlets.getRemoteAddr(httpRequest, 1));
    }

    @Test
    void testGetRemoteAddr_RejectsIpv6LookingGarbage() {
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        // 仅按段数校验时会被误认为合法
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("gggg::1");
        assertEquals("10.0.0.1", Servlets.getRemoteAddr(httpRequest, 1));
        // 跳过非法条目继续扫描
        when(httpRequest.getHeader("X-Forwarded-For")).thenReturn("gggg::1, 2001:db8::1");
        assertEquals("2001:db8::1", Servlets.getRemoteAddr(httpRequest, 2));
    }

    @Test
    void testGetRemoteAddr_WhitespaceHandling() {
        // Test proper handling of whitespace in IP addresses
        when(httpRequest.getRemoteAddr()).thenReturn("10.0.0.1");
        when(httpRequest.getHeader("X-Forwarded-For"))
                .thenReturn(" 203.0.113.1 , 10.0.0.2 ");

        String result = Servlets.getRemoteAddr(httpRequest, 2);
        assertEquals("203.0.113.1", result);
    }
}
