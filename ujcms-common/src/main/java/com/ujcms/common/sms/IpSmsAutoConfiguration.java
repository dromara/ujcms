package com.ujcms.common.sms;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短信验证码自动配置类
 *
 * @author PONY
 */
@Configuration
public class IpSmsAutoConfiguration {
    @Bean
    public IpSmsCache ipSmsCache() {
        return new IpSmsCache();
    }

    @Bean
    public SmsCooldownCache smsCooldownCache() {
        return new SmsCooldownCache();
    }

    /**
     * @param ipv6ThrottlePrefixLength IPv6 限流分组前缀长度（{@code ujcms.ipv6-throttle-prefix-length}，默认 64）
     */
    @Bean
    public IpSmsCounterService ipSmsCounterService(
            @Value("${ujcms.ipv6-throttle-prefix-length:64}") int ipv6ThrottlePrefixLength) {
        return new IpSmsCounterService(ipSmsCache(), ipv6ThrottlePrefixLength);
    }
}
