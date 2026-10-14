package com.ujcms.common.ip;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.lionsoul.ip2region.xdb.LongByteArray;
import org.lionsoul.ip2region.xdb.Searcher;
import org.lionsoul.ip2region.xdb.Version;
import org.lionsoul.ip2region.xdb.XdbException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.IOException;
import java.io.InputStream;
import java.net.Inet4Address;
import java.net.InetAddress;

import static com.ujcms.common.ip.Region.LAN;
import static com.ujcms.common.ip.Region.UNKNOWN;

/**
 * IP 地址归属地查找类（ip2region xdb，IPv4 与 IPv6 双栈）。
 * <p>
 * 查询对象基于全内存缓冲（buffer 模式），可安全并发使用；不要改用 {@code newWithVectorIndex} / {@code newWithFileOnly}，
 * 那两种模式共用 {@code RandomAccessFile}，不是线程安全的。IPv4 地址库必需；IPv6 地址库可选，缺失或不可用时
 * IPv6 地址的归属地为 {@link Region#UNKNOWN}。内网/保留地址（含 IPv6）直接判定为 {@link Region#LAN}。
 *
 * @author PONY
 */
public class IpSeeker implements AutoCloseable {
    private static final Logger logger = LoggerFactory.getLogger(IpSeeker.class);
    /**
     * ip2region 对内网地址给出的归属地标记
     */
    private static final String LAN_REGION = "内网IP";
    private static final int INDEX_COUNTRY = 0;
    private static final int INDEX_PROVINCE = 2;
    private static final int INDEX_CITY = 3;
    private static final int INDEX_PROVIDER = 4;

    private final Searcher v4Searcher;
    @Nullable
    private final Searcher v6Searcher;

    /**
     * @param v4Input IPv4 地址库（ip2region.xdb），必需；文件非法或 IP 版本不符时抛出
     * @param v6Input IPv6 地址库（ip2region_v6.xdb），可为 {@code null}；文件非法或 IP 版本不符时记录警告并退化为仅 IPv4
     * @throws IOException 读取 IPv4 地址库失败
     */
    public IpSeeker(InputStream v4Input, @Nullable InputStream v6Input) throws IOException {
        this.v4Searcher = newSearcher(v4Input, Version.IPv4);
        Searcher v6 = null;
        if (v6Input != null) {
            try {
                v6 = newSearcher(v6Input, Version.IPv6);
            } catch (IOException | IllegalStateException e) {
                logger.warn("IPv6 ip2region database unusable, IPv6 lookups return UNKNOWN: {}", e.getMessage());
            }
        }
        this.v6Searcher = v6;
    }

    private static Searcher newSearcher(InputStream input, Version expected) throws IOException {
        LongByteArray buffer = Searcher.loadContentFromInputStream(input);
        Version version;
        try {
            version = Version.fromHeader(Searcher.loadHeaderFromBuffer(buffer));
        } catch (XdbException e) {
            throw new IllegalStateException("Invalid ip2region xdb file: " + e.getMessage(), e);
        }
        if (version.id != expected.id) {
            throw new IllegalStateException("ip2region xdb IP version mismatch: expected " + expected.name
                    + ", actual " + version.name);
        }
        return Searcher.newWithBuffer(version, buffer);
    }

    /**
     * 是否已加载 IPv6 地址库
     */
    public boolean isIpv6Supported() {
        return v6Searcher != null;
    }

    /**
     * 查找 IP 归属地。任何输入都返回结果，不抛出异常。
     *
     * @param ip IP 字面量（任意文本形式，见 {@link IpAddresses#parseLiteral}）
     * @return 内网/保留地址为 {@link Region#LAN}；无法解析、无对应地址库或未命中为 {@link Region#UNKNOWN}
     */
    public Region find(@Nullable String ip) {
        InetAddress address = IpAddresses.parseLiteral(ip);
        if (address == null) {
            // 主机名 localhost 视为内网（兼容旧行为）
            return Strings.CI.equals(StringUtils.trimToEmpty(ip), "localhost") ? lan() : unknown();
        }
        if (IpAddresses.isInternal(address)) {
            return lan();
        }
        Searcher searcher = address instanceof Inet4Address ? v4Searcher : v6Searcher;
        if (searcher == null) {
            return unknown();
        }
        try {
            // search(String) 内部会经 InetAddress.getByName 解析，可能触发 DNS；此处已是地址，直接按字节查询
            return parseRegion(searcher.search(address.getAddress()));
        } catch (Exception e) {
            logger.warn("IP region lookup failed for {}: {}", IpAddresses.canonical(address), e.getMessage());
            return unknown();
        }
    }

    /**
     * 数据格式为：国家|区域|省份|城市|ISP。未命中时返回空串；字段可能少于 5 个；无值的字段为 {@code 0}
     */
    private static Region parseRegion(@Nullable String region) {
        if (StringUtils.isBlank(region)) {
            return unknown();
        }
        String[] arr = region.split("\\|", -1);
        for (String field : arr) {
            if (LAN_REGION.equals(field.trim())) {
                return lan();
            }
        }
        return new Region(field(arr, INDEX_COUNTRY), field(arr, INDEX_PROVINCE), field(arr, INDEX_CITY),
                field(arr, INDEX_PROVIDER));
    }

    private static String field(String[] arr, int index) {
        if (index >= arr.length) {
            return UNKNOWN;
        }
        String value = arr[index].trim();
        return value.isEmpty() || "0".equals(value) ? UNKNOWN : value;
    }

    private static Region lan() {
        return new Region(LAN, LAN, LAN, LAN);
    }

    private static Region unknown() {
        return new Region(UNKNOWN, UNKNOWN, UNKNOWN, UNKNOWN);
    }

    @Override
    public void close() throws IOException {
        try {
            v4Searcher.close();
        } finally {
            if (v6Searcher != null) {
                v6Searcher.close();
            }
        }
    }
}
