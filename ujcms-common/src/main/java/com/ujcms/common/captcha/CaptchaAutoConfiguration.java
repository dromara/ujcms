package com.ujcms.common.captcha;

import com.nimbusds.jose.KeyLengthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.awt.Color;

/**
 * 验证码自动配置类
 *
 * @author PONY
 */
@Configuration
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CaptchaTokenService.class)
    public CaptchaTokenService captchaTokenService(CaptchaProperties props) throws KeyLengthException {
        return new CaptchaTokenService(captchaRenderer(props), captchaCache(), props);
    }

    @Bean
    public CaptchaRenderer captchaRenderer(CaptchaProperties props) {
        // 渲染只用几何绘制，不加载字体，无需 fontconfig；无图形环境的服务器上仍显式声明 headless，避免创建画布时探测显示设备
        System.setProperty("java.awt.headless", "true");
        return new CaptchaRenderer(props.getWidth(), props.getHeight(), props.getFontSize(),
                props.getWordLength(), props.getRadius(), props.getScale(), CaptchaRenderer.DEFAULT_ALPHABET,
                new Color(23, 67, 172), Color.WHITE);
    }

    @Bean
    public CaptchaCache captchaCache() {
        return new CaptchaCache();
    }

    @Bean
    public IpLoginCache ipLoginCache() {
        return new IpLoginCache();
    }

    /**
     * @param ipv6ThrottlePrefixLength IPv6 限流分组前缀长度（{@code ujcms.ipv6-throttle-prefix-length}，默认 64）
     */
    @Bean
    public IpLoginAttemptService ipLoginAttemptService(
            @Value("${ujcms.ipv6-throttle-prefix-length:64}") int ipv6ThrottlePrefixLength) {
        return new IpLoginAttemptService(ipLoginCache(), ipv6ThrottlePrefixLength);
    }
}
