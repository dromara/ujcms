package com.ujcms.common.file;

import org.junit.jupiter.api.Test;

import java.net.MalformedURLException;
import java.net.URL;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 远程文件抓取地址校验：协议、端口与内网/保留地址（IPv4 与 IPv6）。全部使用字面量，不触发 DNS。
 */
class FilesExUrlGuardTest {

    private static boolean allowed(String url) throws MalformedURLException {
        return FilesEx.isAllowedUrl(new URL(url));
    }

    @Test
    void refusesInternalAddressesAndOddSchemesOrPorts() throws MalformedURLException {
        for (String url : List.of("http://127.0.0.1/a", "http://[::1]/a", "http://[fd00::1]/a", "http://[fe80::1]/a",
                "http://169.254.169.254/latest/meta-data/", "http://100.100.100.200/latest/", "http://10.0.0.5/a",
                "http://[64:ff9b::7f00:1]/a", "http://[2002:c0a8:101::1]/a", "http://localhost/a",
                "http://nas.local/a", "http://0177.0.0.1/a", "ftp://example.com/a", "http://8.8.8.8:8080/a",
                "http://0.0.0.0/a")) {
            assertFalse(allowed(url), url);
        }
    }

    @Test
    void allowsPublicAddressesOnDefaultPorts() throws MalformedURLException {
        for (String url : List.of("http://8.8.8.8/a", "https://8.8.8.8/a", "http://8.8.8.8:80/a",
                "https://8.8.8.8:443/a", "https://[2001:4860:4860::8888]/a")) {
            assertTrue(allowed(url), url);
        }
    }
}
