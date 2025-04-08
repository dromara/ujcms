package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.commons.captcha.CaptchaToken;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.captcha.IpLoginAttemptService;
import com.ujcms.commons.web.Servlets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 图像验证码 Controller
 *
 * @author PONY
 */
@Tag(name = "验证码接口")
@RestController
@RequestMapping({API + "/captcha", FRONTEND_API + "/captcha"})
public class CaptchaController {
    private final ConfigService configService;
    private final IpLoginAttemptService ipLoginAttemptService;
    private final CaptchaTokenService service;

    public CaptchaController(ConfigService configService, IpLoginAttemptService ipLoginAttemptService,
                             CaptchaTokenService service) {
        this.configService = configService;
        this.ipLoginAttemptService = ipLoginAttemptService;
        this.service = service;
    }

    @Operation(summary = "获取验证码Token")
    @GetMapping()
    public CaptchaToken captchaToken() {
        return service.getCaptchaToken();
    }

    @Operation(summary = "尝试验证码是否正确")
    @GetMapping("/try")
    public boolean tryCaptcha(@Parameter(description = "验证码Token") String token,
                              @Parameter(description = "验证码") String captcha) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(captcha)) {
            return false;
        }
        return service.tryCaptcha(token, captcha);
    }

    @Operation(summary = "是否显示验证码。当登录错误超过指定次数后，需要输入验证码")
    @GetMapping("/is-display")
    public boolean isDisplayCaptcha(HttpServletRequest request) {
        String ip = Servlets.getRemoteAddr(request);
        Config.Security security = configService.getUnique().getSecurity();
        return !security.isTwoFactor() && ipLoginAttemptService.isExcessive(ip, security.getIpCaptchaAttempts());
    }
}
