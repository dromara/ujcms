package com.ujcms.cms.core.security;

import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.captcha.IpLoginAttemptService;
import com.ujcms.commons.security.AbstractLoginConfigurer;
import org.springframework.security.config.annotation.web.HttpSecurityBuilder;

/**
 * @author PONY
 */
public class EncryptedPasswordLoginConfigurer<H extends HttpSecurityBuilder<H>>
        extends AbstractLoginConfigurer<H, EncryptedPasswordLoginConfigurer<H>, EncryptedPasswordAuthenticationFilter> {
    public EncryptedPasswordLoginConfigurer(
            Props props, ConfigService configService,
            LoginLogService loginLogService, CaptchaTokenService captchaTokenService,
            IpLoginAttemptService ipLoginAttemptService) {
        super(new EncryptedPasswordAuthenticationFilter(props, configService, loginLogService, captchaTokenService,
                ipLoginAttemptService), null);
    }

    @Override
    public EncryptedPasswordLoginConfigurer<H> loginPage(String loginPage) {
        return super.loginPage(loginPage);
    }
}
