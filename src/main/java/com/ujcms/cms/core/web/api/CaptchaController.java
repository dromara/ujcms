package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.captcha.CaptchaToken;
import com.ujcms.util.captcha.CaptchaTokenService;
import com.ujcms.util.captcha.IpLoginAttemptService;
import com.ujcms.util.web.Servlets;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 图像验证码 Controller
 *
 * @author PONY
 */
@RestController
@RequestMapping(UrlConstants.API + "/captcha")
public class CaptchaController {
    private ConfigService configService;
    private IpLoginAttemptService ipLoginAttemptService;
    private CaptchaTokenService service;

    public CaptchaController(ConfigService configService, IpLoginAttemptService ipLoginAttemptService,
                             CaptchaTokenService service) {
        this.configService = configService;
        this.ipLoginAttemptService = ipLoginAttemptService;
        this.service = service;
    }

    @GetMapping()
    public CaptchaToken captchaToken() {
        return service.getCaptchaToken();
    }

    @GetMapping("/try")
    public boolean tryCaptcha(String token, String captcha) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(captcha)) {
            return false;
        }
        return service.tryCaptcha(token, captcha);
    }

    @GetMapping("/is-display")
    public boolean isDisplayCaptcha(HttpServletRequest request) {
        String ip = Servlets.getRemoteAddr(request);
        Config.Security security = configService.getUnique().getSecurity();
        return !security.isTwoFactor() && ipLoginAttemptService.isExcessive(ip, security.getIpCaptchaAttempts());
    }
}
