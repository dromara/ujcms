package com.ujcms.commons.sms;

/**
 * IP短信发送 Service
 *
 * @author PONY
 */
public class IpSmsCounterService {
    private final IpSmsCache cache;

    public IpSmsCounterService(IpSmsCache cache) {
        this.cache = cache;
    }

    public void updateCount(String ip) {
        cache.updateCount(ip, cache.getCount(ip) + 1);
    }

    public boolean isExcessive(String ip, int maxPerIp) {
        return maxPerIp > 0 && cache.getCount(ip) >= maxPerIp;
    }
}
