package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.captcha.CaptchaTokenService;
import com.ujcms.util.security.Secures;
import com.ujcms.util.sms.IpSmsCounterService;
import com.ujcms.util.sms.SmsTokenService;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Servlets;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.HashMap;
import java.util.Map;

/**
 * 短信验证码 Controller
 *
 * @author PONY
 */
@RestController
@RequestMapping(UrlConstants.API + "/sms")
public class SmsController {
    private ConfigService configService;
    private UserService userService;
    private ShortMessageService shortMessageService;
    private SmsTokenService smsTokenService;
    private IpSmsCounterService ipSmsCounterService;
    private CaptchaTokenService captchaTokenService;

    public SmsController(ConfigService configService, UserService userService,
                         ShortMessageService shortMessageService, SmsTokenService smsTokenService,
                         IpSmsCounterService ipSmsCounterService, CaptchaTokenService captchaTokenService) {
        this.configService = configService;
        this.userService = userService;
        this.shortMessageService = shortMessageService;
        this.smsTokenService = smsTokenService;
        this.ipSmsCounterService = ipSmsCounterService;
        this.captchaTokenService = captchaTokenService;
    }

    @GetMapping("/mfa-login")
    public ResponseEntity<Responses.Body> mfaLogin(String token, String captcha, String mobile,
                                                   HttpServletRequest request) {
        // 验证码是否正确
        if (!captchaTokenService.validateCaptcha(token, captcha)) {
            return Responses.failure(request, "error.captchaIncorrect");
        }
        // 手机号码是否存在
        if (userService.selectByMobile(mobile) == null) {
            return Responses.failure(request, "error.phoneNumberNotExist");
        }
        String ip = Servlets.getRemoteAddr(request);
        Config.Sms sms = configService.getUnique().getSms();
        // IP发送短信是否超过限制次数
        if (ipSmsCounterService.isExcessive(ip, sms.getMaxPerIp())) {
            return Responses.failure(request, "error.ipSendSmsExcessive");
        }
        // 短信功能未开启
        if (sms.getProvider() == Config.Sms.PROVIDER_NONE) {
            return Responses.failure(request, "error.smsNotEnabled");
        }
        String code = Secures.randomNumeric(sms.getCodeLength());
        String error = shortMessageService.sendMobileMessage(mobile, code, sms);
        if (error != null) {
            return Responses.failure(error);
        }
        ShortMessage shortMessage = shortMessageService.insertMobileMessage(mobile, code, ip,
                ShortMessage.USAGE_MFA_LOGIN);
        String shortMessageToken = smsTokenService.createToken(shortMessage.getId(), mobile, code);
        ipSmsCounterService.updateCount(ip);
        Map<String, Object> result = new HashMap<>(16);
        result.put("token", shortMessageToken);
        return Responses.ok(result);
    }
}
