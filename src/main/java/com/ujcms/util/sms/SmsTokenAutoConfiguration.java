package com.ujcms.util.sms;

import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 短信验证码自动配置类
 *
 * @author PONY
 */
@Configuration
@EnableConfigurationProperties(SmsTokenProperties.class)
public class SmsTokenAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(SmsTokenService.class)
    public SmsTokenService cmsTokenService(SmsTokenProperties props) {
        return new SmsTokenService(smsTokenCache(), props);
    }

    @Bean
    public SmsTokenCache smsTokenCache() {
        return new SmsTokenCache();
    }

    @Bean
    public IpSmsCache ipSmsCache() {
        return new IpSmsCache();
    }

    @Bean
    public IpSmsCounterService ipSmsCounterService() {
        return new IpSmsCounterService(ipSmsCache());
    }
}
