package com.ujcms.common.captcha;

import com.ujcms.common.ip.IpAddresses;

/**
 * IP 提交计数：按场景与 IP（IPv6 按前缀分组，同 {@link IpLoginAttemptService}）计数。
 *
 * @author PONY
 */
public class IpSubmitCounterService {
    private final IpSubmitCache cache;
    private final int ipv6PrefixLength;

    public IpSubmitCounterService(IpSubmitCache cache) {
        this(cache, IpAddresses.DEFAULT_IPV6_THROTTLE_PREFIX_LENGTH);
    }

    public IpSubmitCounterService(IpSubmitCache cache, int ipv6PrefixLength) {
        this.cache = cache;
        this.ipv6PrefixLength = ipv6PrefixLength;
    }

    /**
     * 记一次被接受的提交
     */
    public void increase(String scope, String ip) {
        String key = key(scope, ip);
        cache.updateCount(key, cache.getCount(key) + 1);
    }

    public int count(String scope, String ip) {
        return cache.getCount(key(scope, ip));
    }

    /**
     * 是否达到上限。{@code limit <= 0} 视为“没有免验证额度”，恒为 true
     */
    public boolean isExcessive(String scope, String ip, int limit) {
        return limit <= 0 || cache.getCount(key(scope, ip)) >= limit;
    }

    private String key(String scope, String ip) {
        return scope + '|' + IpAddresses.throttleKey(ip, ipv6PrefixLength);
    }
}
