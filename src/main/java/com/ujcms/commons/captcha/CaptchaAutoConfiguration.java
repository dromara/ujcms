package com.ujcms.commons.captcha;

import com.nimbusds.jose.KeyLengthException;
import com.octo.captcha.component.image.backgroundgenerator.UniColorBackgroundGenerator;
import com.octo.captcha.component.image.color.RandomListColorGenerator;
import com.octo.captcha.component.image.fontgenerator.RandomFontGenerator;
import com.octo.captcha.component.word.wordgenerator.RandomWordGenerator;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.ResourceLoader;

import java.awt.*;
import java.io.IOException;
import java.io.InputStream;

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
    public CaptchaTokenService captchaTokenService(CaptchaProperties props, ResourceLoader resourceLoader)
            throws IOException, FontFormatException, KeyLengthException {
        return new CaptchaTokenService(gmailCaptchaEngine(props, resourceLoader), captchaCache(), props);
    }

    @Bean
    public GmailCaptchaEngine gmailCaptchaEngine(CaptchaProperties props, ResourceLoader resourceLoader)
            throws IOException, FontFormatException {
        RandomWordGenerator wordGenerator = new RandomWordGenerator("ABCDEGHJKLMNRSTUWXY235689235689");
        // 防止创建字体时出现错误: java.io.IOException: Problem reading font data.
        // JDK11 需要安装 FontConfig 组件， yum install -y fontconfig
        // docker环境，则还需要执行 fc-ache --force
        System.setProperty("java.awt.headless", "true");
        Font font;
        try (InputStream is = resourceLoader.getResource(props.getFontPath()).getInputStream()) {
            font = Font.createFont(Font.TRUETYPE_FONT, is);
        }
        RandomFontGenerator fontGenerator = new RandomFontGenerator(
                props.getFontSize(), props.getFontSize(), new Font[]{font}, false);
        RandomListColorGenerator colorGenerator = new RandomListColorGenerator(new Color[]{new Color(23, 67, 172)});
        UniColorBackgroundGenerator background = new UniColorBackgroundGenerator(
                props.getWidth(), props.getHeight(), Color.white);
        return new GmailCaptchaEngine(wordGenerator, fontGenerator, colorGenerator, background,
                props.getWordLength(), props.getWordLength(), props.getRadius());
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
    public IpLoginAttemptService ipLoginAttemptService() {
        return new IpLoginAttemptService(ipLoginCache());
    }
}
