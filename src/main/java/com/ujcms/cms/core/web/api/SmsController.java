package com.ujcms.cms.core.web.api;

import com.google.common.collect.ImmutableMap;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.sms.IpSmsCounterService;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.Servlets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotBlank;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 短信验证码 Controller
 *
 * @author PONY
 */
@Tag(name = "短信接口")
@RestController
@RequestMapping({API + "/sms", FRONTEND_API + "/sms"})
public class SmsController {
    private final ConfigService configService;
    private final ShortMessageService shortMessageService;
    private final IpSmsCounterService ipSmsCounterService;
    private final CaptchaTokenService captchaTokenService;

    public SmsController(ConfigService configService, ShortMessageService shortMessageService,
                         IpSmsCounterService ipSmsCounterService, CaptchaTokenService captchaTokenService) {
        this.configService = configService;
        this.shortMessageService = shortMessageService;
        this.ipSmsCounterService = ipSmsCounterService;
        this.captchaTokenService = captchaTokenService;
    }

    @Operation(summary = "发送手机短信")
    @PostMapping("/mobile")
    public ResponseEntity<Body> mobileMessage(@RequestBody ShortMessageParams params, HttpServletRequest request) {
        // 验证码是否正确
        if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            return Responses.failure(request, "error.captchaIncorrect");
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
        String error = shortMessageService.sendMobileMessage(params.receiver, code, sms);
        if (error != null) {
            return Responses.failure(error);
        }
        ShortMessage shortMessage = shortMessageService.insertMobileMessage(params.receiver, code, ip, params.usage);
        return Responses.ok(ImmutableMap.of("shortMessageId", shortMessage.getId()));
    }

    @Operation(summary = "发送邮件短信")
    @PostMapping("/email")
    public ResponseEntity<Body> emailMessage(@RequestBody ShortMessageParams params, HttpServletRequest request) {
        // 验证码是否正确
        if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            return Responses.failure(request, "error.captchaIncorrect");
        }
        String ip = Servlets.getRemoteAddr(request);
        Config.Email emailConfig = configService.getUnique().getEmail();
        // IP发送短信是否超过限制次数
        if (ipSmsCounterService.isExcessive(ip, emailConfig.getMaxPerIp())) {
            return Responses.failure(request, "error.ipSendSmsExcessive");
        }
        // 短信功能未开启
        if (StringUtils.isBlank(emailConfig.getHost())) {
            return Responses.failure(request, "error.emailNotEnabled");
        }
        String code = Secures.randomNumeric(emailConfig.getCodeLength());
        String text = StringUtils.replace(emailConfig.getText(), "${code}", code);
        emailConfig.sendMail(new String[]{params.receiver}, emailConfig.getSubject(), text);
        ShortMessage shortMessage = shortMessageService.insertEmailMessage(params.receiver, code, ip, params.usage);
        return Responses.ok(ImmutableMap.of("shortMessageId", shortMessage.getId()));
    }

    @Operation(summary = "验证手机短信是否正确")
    @GetMapping("/mobile/try")
    public boolean tryMobile(@Parameter(description = "手机短信ID") Long mobileMessageId,
                             @Parameter(description = "手机号码") String mobile,
                             @Parameter(description = "手机验证码") String mobileMessageValue) {
        if (mobileMessageId == null || StringUtils.isBlank(mobileMessageValue)) {
            return false;
        }
        Config.Sms sms = configService.getUnique().getSms();
        return shortMessageService.tryCode(mobileMessageId, mobile, mobileMessageValue,
                sms.getCodeExpires(), sms.getCodeLength());
    }

    @Operation(summary = "验证邮件短信是否正确")
    @GetMapping("/email/try")
    public boolean tryEmail(@Parameter(description = "邮件短信ID") Long emailMessageId,
                            @Parameter(description = "邮件号码") String email,
                            @Parameter(description = "邮件验证码") String emailMessageValue) {
        if (emailMessageId == null || StringUtils.isBlank(emailMessageValue)) {
            return false;
        }
        Config.Email emailConfig = configService.getUnique().getEmail();
        return shortMessageService.tryCode(emailMessageId, email, emailMessageValue,
                emailConfig.getCodeExpires(), emailConfig.getCodeLength());
    }

    @Schema(name = "SmsController.ShortMessageParams", description = "短信验证码参数")
    public static class ShortMessageParams {
        @Schema(description = "验证码Token")
        public String captchaToken;
        @Schema(description = "验证码")
        public String captcha;
        @Schema(description = "接收人。发送手机短信则为手机号码；发送邮件短信则为邮箱地址")
        @NotBlank
        public String receiver;
        @Schema(description = "用途。0:测试,1:注册,2:登录,3:双因子登录,4:找回密码,5:修改手机号码,6:修改邮箱地址")
        @Min(0)
        @Max(6)
        public short usage;
    }
}
