package com.ujcms.commons.captcha;

/**
 * IP登录尝试 Service
 *
 * @author PONY
 */
public class IpLoginAttemptService {
    private final IpLoginCache cache;

    public IpLoginAttemptService(IpLoginCache cache) {
        this.cache = cache;
    }

    public void failure(String ip) {
        cache.updateAttempts(ip, cache.getAttempts(ip) + 1);
    }

    public void success(String ip) {
        cache.removeAttempts(ip);
    }

    public boolean isExcessive(String ip, int maxAttempts) {
        return maxAttempts > 0 && cache.getAttempts(ip) >= maxAttempts;
    }
}
