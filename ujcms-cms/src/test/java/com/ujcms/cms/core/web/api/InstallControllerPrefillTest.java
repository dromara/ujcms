package com.ujcms.cms.core.web.api;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

/**
 * 安装向导网络参数预填：X-Forwarded-Host 的 IPv6 字面量、端口与多值处理。
 */
class InstallControllerPrefillTest {

    private static InstallController.Prefill prefill(String forwardedHost, String forwardedPort, String proto) {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/install/state");
        if (forwardedHost != null) {
            request.addHeader("X-Forwarded-Host", forwardedHost);
        }
        if (forwardedPort != null) {
            request.addHeader("X-Forwarded-Port", forwardedPort);
        }
        if (proto != null) {
            request.addHeader("X-Forwarded-Proto", proto);
        }
        return InstallController.prefill(request);
    }

    @Test
    void bracketedIpv6WithPort() {
        InstallController.Prefill p = prefill("[2001:db8::1]:8080", null, null);
        assertEquals("2001:db8::1", p.domain());
        assertEquals(8080, p.port());
    }

    @Test
    void bracketedIpv6WithoutPortUsesForwardedPort() {
        InstallController.Prefill p = prefill("[2001:db8::1]", "8443", "https");
        assertEquals("2001:db8::1", p.domain());
        assertEquals(8443, p.port());
        assertEquals("https", p.protocol());
    }

    @Test
    void bareIpv6IsWholeHost() {
        InstallController.Prefill p = prefill("2001:db8::1", "9000", null);
        assertEquals("2001:db8::1", p.domain());
        assertEquals(9000, p.port());
    }

    @Test
    void hostnameWithPortUnchanged() {
        InstallController.Prefill p = prefill("example.com:8080", null, null);
        assertEquals("example.com", p.domain());
        assertEquals(8080, p.port());
    }

    @Test
    void firstValueOfCommaList() {
        InstallController.Prefill p = prefill("example.com, proxy.internal", null, null);
        assertEquals("example.com", p.domain());
        assertNull(p.port());
    }

    @Test
    void defaultPortCollapses() {
        InstallController.Prefill p = prefill("example.com", "443", "https");
        assertEquals("example.com", p.domain());
        assertNull(p.port());
    }

    @Test
    void fallsBackToRequestWhenNoForwardedHost() {
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/install/state");
        request.setServerName("cms.example.com");
        request.setServerPort(8080);
        InstallController.Prefill p = InstallController.prefill(request);
        assertEquals("cms.example.com", p.domain());
        assertEquals(8080, p.port());
    }
}
