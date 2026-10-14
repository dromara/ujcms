package com.ujcms.common.captcha;

import com.ujcms.common.ip.IpAddresses;

/**
 * IP登录尝试 Service。
 * <p>
 * 计数键由 {@link IpAddresses#throttleKey(String, int)} 派生：IPv4 按地址，IPv6 按前缀（默认 /64）分组，
 * 避免隐私扩展地址轮换绕过锁定。调用方传入的仍是完整 IP，登录日志等记录不受影响。
 *
 * @author PONY
 */
public class IpLoginAttemptService {
    private final IpLoginCache cache;
    private final int ipv6PrefixLength;

    public IpLoginAttemptService(IpLoginCache cache) {
        this(cache, IpAddresses.DEFAULT_IPV6_THROTTLE_PREFIX_LENGTH);
    }

    /**
     * @param ipv6PrefixLength IPv6 分组前缀长度（位），128 表示按单个地址
     */
    public IpLoginAttemptService(IpLoginCache cache, int ipv6PrefixLength) {
        this.cache = cache;
        this.ipv6PrefixLength = ipv6PrefixLength;
    }

    public void failure(String ip) {
        String key = key(ip);
        cache.updateAttempts(key, cache.getAttempts(key) + 1);
    }

    public void success(String ip) {
        cache.removeAttempts(key(ip));
    }

    public boolean isExcessive(String ip, int maxAttempts) {
        return maxAttempts > 0 && cache.getAttempts(key(ip)) >= maxAttempts;
    }

    private String key(String ip) {
        return IpAddresses.throttleKey(ip, ipv6PrefixLength);
    }
}
