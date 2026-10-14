package com.ujcms.common.sms;

import java.time.Clock;
import java.time.LocalDate;

import com.ujcms.common.ip.IpAddresses;

/**
 * IP短信发送 Service。
 * <p>
 * 按自然日计数（服务器时区，每天零点重新计数），与后台设置“IP每日最大量”的含义一致。计数键由
 * {@link IpAddresses#throttleKey(String, int)} 加当天日期组成：IPv4 按地址，IPv6 按前缀（默认 /64）分组。
 * 不能只靠缓存过期时间实现“每日”：缓存在每次计数时重新计时，持续有发送的 IP（如单位出口 IP）计数永远不会清零。
 *
 * @author PONY
 */
public class IpSmsCounterService {
    private final IpSmsCache cache;
    private final int ipv6PrefixLength;
    private final Clock clock;

    public IpSmsCounterService(IpSmsCache cache) {
        this(cache, IpAddresses.DEFAULT_IPV6_THROTTLE_PREFIX_LENGTH);
    }

    /**
     * @param ipv6PrefixLength IPv6 分组前缀长度（位），128 表示按单个地址
     */
    public IpSmsCounterService(IpSmsCache cache, int ipv6PrefixLength) {
        this(cache, ipv6PrefixLength, Clock.systemDefaultZone());
    }

    /**
     * @param clock 决定“当天”的时钟。用于测试
     */
    public IpSmsCounterService(IpSmsCache cache, int ipv6PrefixLength, Clock clock) {
        this.cache = cache;
        this.ipv6PrefixLength = ipv6PrefixLength;
        this.clock = clock;
    }

    /**
     * 记录一次成功发送（发送失败不计数）。
     */
    public void updateCount(String ip) {
        String key = key(ip);
        cache.updateCount(key, cache.getCount(key) + 1);
    }

    /**
     * 当前 IP 当天的发送次数是否已达上限。
     *
     * @param maxPerIp 每个 IP 每天允许的最大发送次数，{@code <= 0} 表示不限制
     */
    public boolean isExcessive(String ip, int maxPerIp) {
        return maxPerIp > 0 && cache.getCount(key(ip)) >= maxPerIp;
    }

    private String key(String ip) {
        // 分隔符不用冒号，避免与 IPv6 键混淆
        return IpAddresses.throttleKey(ip, ipv6PrefixLength) + '|' + LocalDate.now(clock);
    }
}
