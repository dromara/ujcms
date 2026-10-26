package com.ujcms.common.captcha;

import com.nimbusds.jose.KeyLengthException;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.awt.Color;
import java.util.List;

/**
 * 验证码自动配置类。
 * <p>
 * 内置三种提供者：字符图片（无障碍回退）、滑块拼图（默认可见挑战）、工作量证明（互动表单的静默验证）；
 * 第三方厂商的提供者由商业层按需注册为 Bean，{@link CaptchaTokenService} 收集全部 {@link CaptchaProvider}。
 * 契约见 docs/spec/captcha/plan.md。
 *
 * @author PONY
 */
@Configuration
@EnableConfigurationProperties(CaptchaProperties.class)
public class CaptchaAutoConfiguration {

    @Bean
    @ConditionalOnMissingBean(CaptchaTokenService.class)
    public CaptchaTokenService captchaTokenService(List<CaptchaProvider> providers, CaptchaCache captchaCache,
                                                   CaptchaProperties props) throws KeyLengthException {
        return new CaptchaTokenService(providers, captchaCache, props);
    }

    @Bean
    public CaptchaCrypto captchaCrypto(CaptchaProperties props) {
        return new CaptchaCrypto(props.getSecret());
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
    public SliderRenderer sliderRenderer(CaptchaProperties props) {
        System.setProperty("java.awt.headless", "true");
        return new SliderRenderer(props.getSliderWidth(), props.getSliderHeight(), props.getSliderPieceSize(),
                props.getScale());
    }

    @Bean
    public CharCaptchaProvider charCaptchaProvider(CaptchaRenderer captchaRenderer, CaptchaCrypto captchaCrypto,
                                                   CaptchaProperties props) {
        return new CharCaptchaProvider(captchaRenderer, captchaCrypto, props.getWordLength());
    }

    @Bean
    public SliderCaptchaProvider sliderCaptchaProvider(SliderRenderer sliderRenderer, CaptchaCrypto captchaCrypto,
                                                       CaptchaProperties props) {
        return new SliderCaptchaProvider(sliderRenderer, captchaCrypto, props.getSliderWidth(),
                props.getSliderHeight(), props.getSliderTolerance());
    }

    /**
     * 工作量证明。难度默认取属性，cms 层接入全局设置后以 {@link PowCaptchaProvider#setDifficulty} 替换来源
     */
    @Bean
    public PowCaptchaProvider powCaptchaProvider(CaptchaProperties props) {
        return new PowCaptchaProvider(props::getPowDifficulty);
    }

    @Bean
    public CaptchaCache captchaCache() {
        return new CaptchaCache();
    }

    @Bean
    public IpLoginCache ipLoginCache() {
        return new IpLoginCache();
    }

    @Bean
    public IpSubmitCache ipSubmitCache() {
        return new IpSubmitCache();
    }

    /**
     * @param ipv6ThrottlePrefixLength IPv6 限流分组前缀长度（{@code ujcms.ipv6-throttle-prefix-length}，默认 64）
     */
    @Bean
    public IpLoginAttemptService ipLoginAttemptService(
            @Value("${ujcms.ipv6-throttle-prefix-length:64}") int ipv6ThrottlePrefixLength) {
        return new IpLoginAttemptService(ipLoginCache(), ipv6ThrottlePrefixLength);
    }

    @Bean
    public IpSubmitCounterService ipSubmitCounterService(
            @Value("${ujcms.ipv6-throttle-prefix-length:64}") int ipv6ThrottlePrefixLength) {
        return new IpSubmitCounterService(ipSubmitCache(), ipv6ThrottlePrefixLength);
    }
}
