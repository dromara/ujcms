package com.ujcms.common.ip;

import org.junit.jupiter.api.Test;
import org.springframework.core.io.ClassPathResource;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * IP 归属地查找：IPv4 地址库来自 ip-region-data 依赖包（测试类路径），IPv6 地址库缺省。
 * 任何输入都不得抛出异常。
 */
class IpSeekerTest {

    private static InputStream v4() throws Exception {
        return new ClassPathResource("ip2region.xdb").getInputStream();
    }

    private static void assertAll(Region region, String expected) {
        assertEquals(expected, region.getCountry());
        assertEquals(expected, region.getProvince());
        assertEquals(expected, region.getCity());
        assertEquals(expected, region.getProvider());
    }

    @Test
    void internalAddressesAreLanRegardlessOfFamily() throws Exception {
        try (InputStream in = v4(); IpSeeker seeker = new IpSeeker(in, null)) {
            assertFalse(seeker.isIpv6Supported());
            for (String ip : List.of("::1", "0:0:0:0:0:0:0:1", "127.0.0.1", "10.0.0.1", "fd00::1", "fe80::1",
                    "100.100.100.200", "localhost", "[::1]")) {
                assertAll(seeker.find(ip), Region.LAN);
            }
        }
    }

    @Test
    void ipv6WithoutDatabaseIsUnknownAndNeverThrows() throws Exception {
        try (InputStream in = v4(); IpSeeker seeker = new IpSeeker(in, null)) {
            assertAll(seeker.find("2001:4860:4860::8888"), Region.UNKNOWN);
            for (String ip : List.of("", "   ", "gggg::1", "example.com", "1.2.3.4.5")) {
                assertAll(seeker.find(ip), Region.UNKNOWN);
            }
            assertAll(seeker.find(null), Region.UNKNOWN);
        }
    }

    @Test
    void ipv4AndMappedIpv4ResolveThroughIpv4Database() throws Exception {
        try (InputStream in = v4(); IpSeeker seeker = new IpSeeker(in, null)) {
            Region direct = seeker.find("114.114.114.114");
            assertNotEquals(Region.UNKNOWN, direct.getCountry());
            assertNotEquals(Region.LAN, direct.getCountry());
            Region mapped = seeker.find("::ffff:114.114.114.114");
            assertEquals(direct.getCountry(), mapped.getCountry());
            assertEquals(direct.getProvince(), mapped.getProvince());
            assertNotNull(seeker.find("8.8.8.8").getCountry());
        }
    }

    @Test
    void wrongFamilyIpv6DatabaseIsIgnored() throws Exception {
        // 把 IPv4 地址库当作 IPv6 地址库提供：不抛出，退化为仅 IPv4
        try (InputStream in = v4(); InputStream bogusV6 = v4(); IpSeeker seeker = new IpSeeker(in, bogusV6)) {
            assertFalse(seeker.isIpv6Supported());
            assertAll(seeker.find("2001:4860:4860::8888"), Region.UNKNOWN);
            assertNotEquals(Region.UNKNOWN, seeker.find("114.114.114.114").getCountry());
        }
    }

    @Test
    void concurrentLookupsAreConsistent() throws Exception {
        try (InputStream in = v4(); IpSeeker seeker = new IpSeeker(in, null)) {
            String expected = seeker.find("114.114.114.114").getProvince();
            ExecutorService pool = Executors.newFixedThreadPool(8);
            try {
                List<Future<Boolean>> results = new ArrayList<>();
                for (int t = 0; t < 8; t++) {
                    results.add(pool.submit(() -> {
                        for (int i = 0; i < 200; i++) {
                            if (!expected.equals(seeker.find("114.114.114.114").getProvince())
                                    || !Region.LAN.equals(seeker.find("10.0.0.1").getCountry())) {
                                return false;
                            }
                        }
                        return true;
                    }));
                }
                for (Future<Boolean> result : results) {
                    assertTrue(result.get());
                }
            } finally {
                pool.shutdownNow();
            }
        }
    }
}
