package com.ujcms.cms.core.web.backendapi;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 远程图片抓取地址校验（协议、端口、域名合法性、SSRF 白名单）。不做 DNS 解析；
 * 内网地址判定由 IpAddresses.isInternalHost 负责，另行测试。
 */
class UploadControllerFetchGuardTest {

    private static String check(String url, List<String> whiteList) throws MalformedURLException {
        return UploadController.checkFetchUrl(new URL(url), whiteList);
    }

    @Test
    void rejectsSchemePortLiteralAndUnlistedHosts() throws MalformedURLException {
        assertTrue(check("ftp://example.com/a.jpg", List.of("*")).startsWith("Only supports http"));
        assertTrue(check("http://example.com:8080/a.jpg", List.of("*")).startsWith("Only default port"));
        // IP 字面量（IPv4 与 IPv6）不是合法域名，即便白名单放开也拒绝
        assertTrue(check("http://8.8.8.8/a.jpg", List.of("*")).startsWith("Host is not valid"));
        assertTrue(check("http://[2001:db8::1]/a.jpg", List.of("*")).startsWith("Host is not valid"));
        assertTrue(check("http://example.com/a.jpg", List.of("other.com")).startsWith("Host not in whitelist"));
        assertTrue(check("http://example.com/a.jpg", List.of()).startsWith("Host not in whitelist"));
    }

    @Test
    void acceptsListedHostsAndWildcard() throws MalformedURLException {
        assertNull(check("http://example.com/a.jpg", List.of("example.com")));
        assertNull(check("https://sub.example.com/a.jpg", List.of("example.com")));
        assertNull(check("https://example.com/a.jpg", List.of("*")));
    }
}
