package com.ujcms.commons.ip;

import io.minio.org.apache.commons.validator.routines.InetAddressValidator;
import org.apache.commons.lang3.StringUtils;
import org.lionsoul.ip2region.xdb.Searcher;

import java.io.IOException;

import static com.ujcms.commons.ip.Region.LAN;
import static com.ujcms.commons.ip.Region.UNKNOWN;

/**
 * IP 地址查找类
 *
 * @author PONY
 */
public class IpSeeker implements AutoCloseable {
    private final Searcher searcher;
    private static final InetAddressValidator VALIDATOR = InetAddressValidator.getInstance();

    public IpSeeker(byte[] bytes) throws IOException {
        searcher = Searcher.newWithBuffer(bytes);
    }

    public Region find(String ip) {
        // 本机地址直接设置为LAN
        if (StringUtils.isBlank(ip) ||
                StringUtils.equalsAnyIgnoreCase(ip, "0:0:0:0:0:0:0:1", "127.0.0.1", "localhost")) {
            return new Region(LAN, LAN, LAN, LAN);
        }
        // 目前只能判断 IPv4 地址
        if (!VALIDATOR.isValidInet4Address(ip)) {
            return new Region(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);
        }
        try {
            // 数据格式为：国家|区域|省份|城市|ISP
            String region = searcher.search(ip);
            String[] arr = region.split("\\|");
            // 如果是`内网IP`，则将所有值设置为`LAN`
            String lan = "内网IP";
            // ip2region只会在arr[3]或arr[4]给出`内网IP`
            int index3 = 3;
            int index4 = 4;
            if (StringUtils.equalsAnyIgnoreCase(lan, arr[index3], arr[index4])) {
                return new Region(LAN, LAN, LAN, LAN);
            }
            for (int i = 0, len = arr.length; i < len; i += 1) {
                // 无值的部分为`0`，设置为`UNKNOWN`
                if ("0".equals(arr[i])) {
                    arr[i] = UNKNOWN;
                }
            }
            return new Region(arr[0], arr[2], arr[3], arr[4]);
        } catch (Exception e) {
            throw new IllegalStateException(e);
        }
    }

    @Override
    public void close() throws Exception {
        searcher.close();
    }
}
