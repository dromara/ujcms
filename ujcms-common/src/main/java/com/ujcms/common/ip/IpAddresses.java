package com.ujcms.common.ip;

import com.google.common.net.InetAddresses;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.net.Inet6Address;
import java.net.InetAddress;
import java.net.UnknownHostException;
import java.util.Arrays;
import java.util.Locale;

/**
 * IP 地址工具类：字面量解析、RFC 5952 规范化、限流分组键、内网/保留地址判定。
 * <p>
 * 除 {@link #isInternalHost(String)} 的域名分支外，所有方法都不做 DNS 解析。
 * 规范化文本不含方括号、端口与 {@code %网卡标识}，IPv6 为小写压缩形式（最长 39 字符），
 * IPv4 映射地址（{@code ::ffff:a.b.c.d}）按 IPv4 处理。
 * <p>
 * 内网判定与后续 HTTP 连接时的域名解析存在时间差（DNS rebinding 的残留风险），
 * 完全消除需在连接层校验，超出本工具范围。
 *
 * @author PONY
 */
public class IpAddresses {
    /**
     * IPv6 限流分组的默认前缀长度。/64 是分配给终端网络的最小前缀；隐私扩展地址（RFC 4941）只在其后 64 位轮换，
     * 按单个地址限流会被轻易绕过，且可用海量地址挤掉缓存中其他 IP 的计数
     */
    public static final int DEFAULT_IPV6_THROTTLE_PREFIX_LENGTH = 64;

    private static final int IPV4_BYTES = 4;
    private static final int IPV6_BYTES = 16;
    private static final int IPV6_BITS = 128;
    private static final int MAX_PORT_DIGITS = 5;
    private static final int BYTE_MASK = 0xFF;

    /**
     * 解析 IP 字面量，不做 DNS 解析。
     * <p>
     * 去除方括号（{@code [2001:db8::1]}）、{@code %网卡标识}（{@code fe80::1%eth0}），
     * 以及 IPv4 与带方括号 IPv6 后的端口（{@code 203.0.113.9:5678}、{@code [2001:db8::1]:443}）；
     * 不带方括号的 IPv6 不去端口（{@code 2001:db8::1:443} 本身即合法地址，无法区分）。
     *
     * @return 解析结果；主机名或非法字面量返回 {@code null}。IPv4 映射地址返回 {@link java.net.Inet4Address}
     */
    @Nullable
    public static InetAddress parseLiteral(@Nullable String token) {
        String value = StringUtils.trimToNull(token);
        if (value == null) {
            return null;
        }
        if (value.charAt(0) == '[') {
            int close = value.indexOf(']');
            if (close < 0) {
                return null;
            }
            String tail = value.substring(close + 1);
            if (!tail.isEmpty() && !isPortSuffix(tail)) {
                return null;
            }
            value = value.substring(1, close);
            // 方括号仅用于 IPv6
            if (value.indexOf(':') < 0) {
                return null;
            }
        } else {
            int colon = value.indexOf(':');
            // 只有一个冒号且冒号前有点号：视为 IPv4:端口
            if (colon > 0 && colon == value.lastIndexOf(':') && value.lastIndexOf('.', colon) >= 0
                    && isPortSuffix(value.substring(colon))) {
                value = value.substring(0, colon);
            }
        }
        // 网卡标识必须在交给 Guava 之前去除：Guava 会按名称查找本机网卡，不存在时抛异常
        int percent = value.indexOf('%');
        if (percent >= 0) {
            value = value.substring(0, percent);
        }
        if (value.isEmpty() || !InetAddresses.isInetAddress(value)) {
            return null;
        }
        try {
            return InetAddresses.forString(value);
        } catch (IllegalArgumentException e) {
            return null;
        }
    }

    private static boolean isPortSuffix(String tail) {
        return tail.length() >= 2 && tail.length() <= MAX_PORT_DIGITS + 1 && tail.charAt(0) == ':'
                && StringUtils.isNumeric(tail.substring(1));
    }

    /**
     * 规范化文本：IPv4 为点分十进制；IPv6 为 RFC 5952 小写压缩形式，不含网卡标识。
     */
    public static String canonical(InetAddress address) {
        InetAddress plain = address;
        if (address instanceof Inet6Address inet6 && (inet6.getScopeId() != 0 || inet6.getScopedInterface() != null)) {
            plain = fromBytes(address.getAddress());
        }
        return InetAddresses.toAddrString(plain);
    }

    /**
     * 解析并规范化 IP 字面量。
     *
     * @return 规范化文本；无法解析返回 {@code null}
     */
    @Nullable
    public static String canonicalize(@Nullable String ip) {
        InetAddress address = parseLiteral(ip);
        return address == null ? null : canonical(address);
    }

    /**
     * 限流分组键（默认 IPv6 前缀 /64）。
     *
     * @see #throttleKey(String, int)
     */
    public static String throttleKey(@Nullable String ip) {
        return throttleKey(ip, DEFAULT_IPV6_THROTTLE_PREFIX_LENGTH);
    }

    /**
     * 限流分组键：IPv4 为规范化地址本身；IPv6 取前缀，如 {@code 2001:db8:1:2::/64}；
     * 前缀长度大于等于 128 时按单个地址；无法解析时返回去除首尾空白后的原串。
     * IPv4 键不含冒号、IPv6 键以 {@code /前缀} 结尾，两者不会冲突。
     *
     * @param ipv6PrefixLength IPv6 分组前缀长度（位）
     */
    public static String throttleKey(@Nullable String ip, int ipv6PrefixLength) {
        InetAddress address = parseLiteral(ip);
        if (address == null) {
            return StringUtils.trimToEmpty(ip);
        }
        byte[] bytes = address.getAddress();
        if (bytes.length != IPV6_BYTES || ipv6PrefixLength >= IPV6_BITS) {
            return canonical(address);
        }
        int prefix = Math.max(0, ipv6PrefixLength);
        for (int i = 0; i < IPV6_BYTES; i++) {
            int keptBits = Math.min(Byte.SIZE, Math.max(0, prefix - i * Byte.SIZE));
            bytes[i] = (byte) (bytes[i] & (BYTE_MASK << (Byte.SIZE - keptBits)));
        }
        return canonical(fromBytes(bytes)) + "/" + prefix;
    }

    /**
     * 是否为内网/保留地址：环回、任意本地、链路本地（169.254/16、fe80::/10）、站点本地（10/8、172.16/12、192.168/16、fec0::/10）、
     * 组播，IPv4 保留段（0/8、100.64/10 运营商级 NAT、192.0.0/24、198.18/15、240/4），
     * IPv6 唯一本地地址 fc00::/7、黑洞地址 100::/64、本地 NAT64 前缀 64:ff9b:1::/48，
     * 以及内嵌 IPv4 的 IPv6 形式（NAT64 64:ff9b::/96、6to4 2002::/16、IPv4 映射 ::ffff:0:0/96、IPv4 兼容 ::/96）按内嵌的 IPv4 判定。
     * 文档地址段 2001:db8::/32 与 TEST-NET 视为公网地址。
     */
    public static boolean isInternal(InetAddress address) {
        if (address.isLoopbackAddress() || address.isAnyLocalAddress() || address.isLinkLocalAddress()
                || address.isSiteLocalAddress() || address.isMulticastAddress()) {
            return true;
        }
        byte[] bytes = address.getAddress();
        if (bytes.length == IPV4_BYTES) {
            return isReservedIpv4(bytes);
        }
        if (bytes.length != IPV6_BYTES) {
            return false;
        }
        int b0 = bytes[0] & BYTE_MASK;
        int b1 = bytes[1] & BYTE_MASK;
        // 唯一本地地址 fc00::/7
        if ((b0 & 0xFE) == 0xFC) {
            return true;
        }
        // 黑洞地址 100::/64（RFC 6666）
        if (b0 == 0x01 && b1 == 0x00 && isZero(bytes, 2, 8)) {
            return true;
        }
        // NAT64：64:ff9b:1::/48 为本地前缀，整体视为内网；64:ff9b::/96 为公共前缀，按内嵌 IPv4 判定
        if (b0 == 0x00 && b1 == 0x64 && (bytes[2] & BYTE_MASK) == 0xFF && (bytes[3] & BYTE_MASK) == 0x9B) {
            if ((bytes[4] & BYTE_MASK) == 0x00 && (bytes[5] & BYTE_MASK) == 0x01) {
                return true;
            }
            if (isZero(bytes, 4, 12)) {
                return isInternalEmbedded(bytes, 12);
            }
        }
        // 6to4 2002::/16：内嵌 IPv4 位于第 2～5 字节
        if (b0 == 0x20 && b1 == 0x02) {
            return isInternalEmbedded(bytes, 2);
        }
        // IPv4 映射 ::ffff:0:0/96 与 IPv4 兼容 ::/96：内嵌 IPv4 位于末 4 字节
        if (isZero(bytes, 0, 10)) {
            boolean mapped = (bytes[10] & BYTE_MASK) == 0xFF && (bytes[11] & BYTE_MASK) == 0xFF;
            boolean compatible = bytes[10] == 0 && bytes[11] == 0;
            if (mapped || compatible) {
                return isInternalEmbedded(bytes, 12);
            }
        }
        return false;
    }

    private static boolean isReservedIpv4(byte[] bytes) {
        int b0 = bytes[0] & BYTE_MASK;
        int b1 = bytes[1] & BYTE_MASK;
        int b2 = bytes[2] & BYTE_MASK;
        // 0.0.0.0/8 本网络
        if (b0 == 0) {
            return true;
        }
        // 100.64.0.0/10 运营商级 NAT（阿里云元数据地址 100.100.100.200 亦在其中）
        if (b0 == 100 && (b1 & 0xC0) == 0x40) {
            return true;
        }
        // 192.0.0.0/24 IETF 协议保留
        if (b0 == 192 && b1 == 0 && b2 == 0) {
            return true;
        }
        // 198.18.0.0/15 基准测试
        if (b0 == 198 && (b1 & 0xFE) == 18) {
            return true;
        }
        // 240.0.0.0/4 保留（含广播地址 255.255.255.255）
        return (b0 & 0xF0) == 0xF0;
    }

    private static boolean isInternalEmbedded(byte[] bytes, int offset) {
        return isInternal(fromBytes(Arrays.copyOfRange(bytes, offset, offset + IPV4_BYTES)));
    }

    private static boolean isZero(byte[] bytes, int from, int to) {
        for (int i = from; i < to; i++) {
            if (bytes[i] != 0) {
                return false;
            }
        }
        return true;
    }

    private static InetAddress fromBytes(byte[] bytes) {
        try {
            return InetAddress.getByAddress(bytes);
        } catch (UnknownHostException e) {
            // 字节长度固定为 4 或 16，不会发生
            throw new IllegalArgumentException(e);
        }
    }

    /**
     * 主机是否指向内网/保留地址（防止服务端请求伪造）。
     * <p>
     * IP 字面量直接判定；{@code localhost}、{@code *.localhost}、{@code *.local} 视为内网；
     * 形似 IP 但无法解析的主机（如 {@code 0177.0.0.1}、{@code 2130706433}）视为内网，不交给 DNS；
     * 其余域名解析全部地址，任一命中即为内网。
     *
     * @throws UnknownHostException 域名无法解析，由调用方决定放行或拒绝
     */
    public static boolean isInternalHost(@Nullable String host) throws UnknownHostException {
        String value = StringUtils.trimToEmpty(host);
        if (value.startsWith("[") && value.endsWith("]")) {
            value = value.substring(1, value.length() - 1);
        }
        if (value.isEmpty()) {
            return true;
        }
        InetAddress literal = parseLiteral(value);
        if (literal != null) {
            return isInternal(literal);
        }
        String lower = value.toLowerCase(Locale.ROOT);
        if ("localhost".equals(lower) || lower.endsWith(".localhost") || lower.endsWith(".local")) {
            return true;
        }
        if (lower.indexOf(':') >= 0 || StringUtils.containsOnly(lower, "0123456789.")) {
            return true;
        }
        for (InetAddress address : InetAddress.getAllByName(value)) {
            if (isInternal(address)) {
                return true;
            }
        }
        return false;
    }

    /**
     * 工具类不能实例化
     */
    private IpAddresses() {
        throw new IllegalStateException("Utility class");
    }
}
