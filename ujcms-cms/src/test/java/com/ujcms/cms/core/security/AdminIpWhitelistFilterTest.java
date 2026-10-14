package com.ujcms.cms.core.security;

import jakarta.servlet.http.HttpServletResponse;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 管理终端 IP 白名单过滤器：空名单放行、命中放行、未命中 403、非后台路径放行、X-Forwarded-For 按代理层数生效、
 * 非法条目启动期 fail-loud。
 */
class AdminIpWhitelistFilterTest {

    /** @return 经过过滤器后的响应状态码（403 = 拦截，200 = 放行） */
    private int run(AdminIpWhitelistFilter filter, String uri, String remoteAddr, String xff) throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", uri);
        req.setRemoteAddr(remoteAddr);
        if (xff != null) {
            req.addHeader("X-Forwarded-For", xff);
        }
        MockHttpServletResponse resp = new MockHttpServletResponse();
        filter.doFilter(req, resp, new MockFilterChain());
        return resp.getStatus();
    }

    @Test
    void emptyWhitelist_passesEverything() throws Exception {
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter("", 0);
        assertFalse(filter.isEnabled());
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/cp/index", "203.0.113.9", null));
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/api/backend/core/user", "203.0.113.9", null));
    }

    @Test
    void inWhitelist_passes() throws Exception {
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter("10.0.0.0/8, 192.168.1.5", 0);
        assertTrue(filter.isEnabled());
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/cp/index", "10.1.2.3", null));
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/api/backend/core/user", "192.168.1.5", null));
    }

    @Test
    void outOfWhitelist_forbidden() throws Exception {
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter("10.0.0.0/8", 0);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(filter, "/cp/index", "192.168.1.1", null));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(filter, "/api/backend/core/user", "203.0.113.1", null));
    }

    @Test
    void nonAdminPath_alwaysPasses() throws Exception {
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter("10.0.0.0/8", 0);
        // 前台 / 登录接口不受限，即便 IP 不在白名单
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/article/1", "192.168.1.1", null));
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/api/login", "192.168.1.1", null));
    }

    @Test
    void forwardedFor_honoredAtConfiguredDepth() throws Exception {
        // depth=1：信任一层代理的 X-Forwarded-For
        AdminIpWhitelistFilter depth1 = new AdminIpWhitelistFilter("203.0.113.5", 1);
        assertEquals(HttpServletResponse.SC_OK, run(depth1, "/cp/index", "10.0.0.1", "203.0.113.5"));
        // depth=0：忽略 X-Forwarded-For，按直连 IP（10.0.0.1，不在白名单）→ 403
        AdminIpWhitelistFilter depth0 = new AdminIpWhitelistFilter("203.0.113.5", 0);
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(depth0, "/cp/index", "10.0.0.1", "203.0.113.5"));
    }

    @Test
    void illegalEntry_failsAtConstruction() {
        assertThrows(IllegalArgumentException.class, () -> new AdminIpWhitelistFilter("not-an-ip", 0));
        // IPv4 映射形式的网段：Spring 按 4 字节解析，/104 掩码越界，启动期报错——文档要求 IPv4 网段写点分形式
        assertThrows(IllegalArgumentException.class, () -> new AdminIpWhitelistFilter("::ffff:10.0.0.0/104", 0));
    }

    @Test
    void ipv6_matchesCanonicalizedForms() throws Exception {
        AdminIpWhitelistFilter loopback = new AdminIpWhitelistFilter("::1", 0);
        // Tomcat 给出未压缩形式，规范化后命中
        assertEquals(HttpServletResponse.SC_OK, run(loopback, "/cp/index", "0:0:0:0:0:0:0:1", null));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(loopback, "/cp/index", "2001:db8::1", null));

        AdminIpWhitelistFilter cidr = new AdminIpWhitelistFilter("2001:db8::/32", 0);
        assertEquals(HttpServletResponse.SC_OK, run(cidr, "/cp/index", "2001:db8:1::5", null));
        assertEquals(HttpServletResponse.SC_OK, run(cidr, "/cp/index", "2001:DB8:0:0:0:0:0:1", null));
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(cidr, "/cp/index", "2001:db9::1", null));
    }

    @Test
    void ipv4MappedForwardedFor_matchesIpv4Range() throws Exception {
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter("10.0.0.0/8", 1);
        assertEquals(HttpServletResponse.SC_OK, run(filter, "/cp/index", "192.0.2.1", "::ffff:10.1.2.3"));
        // IPv4 网段不会命中 IPv6 客户端：两类地址需分别配置
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(filter, "/cp/index", "fd00::1", null));
    }

    @Test
    void unparseableAddress_forbiddenNotServerError() throws Exception {
        AdminIpWhitelistFilter filter = new AdminIpWhitelistFilter("203.0.113.5", 1);
        // 非法 X-Forwarded-For 条目被跳过，回落到直连地址（不在白名单）→ 403
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(filter, "/cp/index", "192.0.2.1", "gggg::1"));
        // 直连地址本身不是 IP（容器给出的非 IP 字符串）→ 403 而不是 500
        assertEquals(HttpServletResponse.SC_FORBIDDEN, run(filter, "/cp/index", "unix:/var/run/x.sock", null));
        // 合法条目回落：直连地址在白名单内 → 放行
        AdminIpWhitelistFilter direct = new AdminIpWhitelistFilter("10.0.0.0/8", 1);
        assertEquals(HttpServletResponse.SC_OK, run(direct, "/cp/index", "10.1.2.3", "not-an-ip"));
    }

    @Test
    void isProtected_matchesOnlyBackendPaths() {
        assertTrue(AdminPaths.isProtected("/cp/index"));
        assertTrue(AdminPaths.isProtected("/api/backend/core/user"));
        assertFalse(AdminPaths.isProtected("/api/login"));
        assertFalse(AdminPaths.isProtected("/article/1"));
        assertFalse(AdminPaths.isProtected("/"));
    }
}
