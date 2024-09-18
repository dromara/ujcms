package com.ujcms.cms.core.security;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.LoginLog;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.LoginLogService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.captcha.IpLoginAttemptService;
import com.ujcms.commons.security.CaptchaIncorrectException;
import com.ujcms.commons.security.IpExcessiveAttemptsException;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.web.Servlets;
import org.springframework.lang.Nullable;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.web.authentication.UsernamePasswordAuthenticationFilter;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

/**
 * @author PONY
 */
public class EncryptedPasswordAuthenticationFilter extends UsernamePasswordAuthenticationFilter {
    private final Props props;
    private final ConfigService configService;
    private final LoginLogService loginLogService;
    private final CaptchaTokenService captchaTokenService;
    private final IpLoginAttemptService ipLoginAttemptService;

    public EncryptedPasswordAuthenticationFilter(
            Props props, ConfigService configService,
            LoginLogService loginLogService, CaptchaTokenService captchaTokenService,
            IpLoginAttemptService ipLoginAttemptService) {
        this.props = props;
        this.configService = configService;
        this.loginLogService = loginLogService;
        this.captchaTokenService = captchaTokenService;
        this.ipLoginAttemptService = ipLoginAttemptService;
    }

    @Override
    public Authentication attemptAuthentication(HttpServletRequest request, HttpServletResponse response)
            throws AuthenticationException {
        String username = getUsernameParameter();
        String captcha = request.getParameter(FORM_CAPTCHA_KEY);
        String captchaToken = request.getParameter(FORM_CAPTCHA_TOKEN_KEY);

        Config config = configService.getUnique();
        Config.Security security = config.getSecurity();

        String ip = Servlets.getRemoteAddr(request);
        // IP登录失败超过限制次数
        if (ipLoginAttemptService.isExcessive(ip, security.getIpMaxAttempts())) {
            loginLogService.loginFailure(username, ip, LoginLog.STATUS_IP_EXCESSIVE_ATTEMPTS);
            throw new IpExcessiveAttemptsException("error.ipExcessiveAttempts");
        }
        // 是否需要验证码
        if (!security.isTwoFactor() && ipLoginAttemptService.isExcessive(ip, security.getIpCaptchaAttempts()) &&
                !captchaTokenService.validateCaptcha(captchaToken, captcha)) {
            loginLogService.loginFailure(username, ip, LoginLog.STATUS_CAPTCHA_WRONG);
            throw new CaptchaIncorrectException("error.captchaIncorrect");
        }
        return super.attemptAuthentication(request, response);
    }

    @Nullable
    @Override
    protected String obtainPassword(HttpServletRequest request) {
        String encryptedPassword = super.obtainPassword(request);
        if (encryptedPassword == null) {
            return null;
        }
        // 前台密码已通过SM2加密，此处进行解密
        return Secures.sm2Decrypt(encryptedPassword, props.getClientSm2PrivateKey());
    }

    public static final String FORM_CAPTCHA_KEY = "captcha";
    public static final String FORM_CAPTCHA_TOKEN_KEY = "captchaToken";
}
