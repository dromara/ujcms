package com.ujcms.commons.sms;

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
    public IpSmsCounterService ipSmsCounterService() {
        return new IpSmsCounterService(ipSmsCache());
    }
}
