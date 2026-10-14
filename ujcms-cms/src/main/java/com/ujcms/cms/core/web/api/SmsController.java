package com.ujcms.cms.core.web.api;

import com.google.common.collect.ImmutableMap;
import com.ujcms.cms.core.component.SmsCodeSender;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.security.Secures;
import com.ujcms.common.sms.IpSmsCounterService;
import com.ujcms.common.sms.SmsCooldownCache;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.Servlets;
import com.ujcms.common.web.exception.Http400Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;

import java.util.Set;

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
    /**
     * 公开接口允许发送的手机验证码用途。“测试”只能由后台发送；“登录”没有任何校验方；“双因子登录”只能在口令校验通过后
     * 由登录流程发往账号登记的号码（见 {@code JwtAuthController#mfaSend}），不接受匿名请求指定号码。
     * 验证码与用途绑定（见 {@link ShortMessageService#validateCode}），不在此列的用途发出去也无处可用，只会白白消耗短信。
     */
    private static final Set<Short> MOBILE_USAGES = Set.of(ShortMessage.USAGE_REGISTER,
            ShortMessage.USAGE_RESET_PASSWORD, ShortMessage.USAGE_MODIFY_MOBILE);
    /**
     * 公开接口允许发送的邮件验证码用途
     */
    private static final Set<Short> EMAIL_USAGES = Set.of(ShortMessage.USAGE_REGISTER,
            ShortMessage.USAGE_RESET_PASSWORD, ShortMessage.USAGE_MODIFY_EMAIL);

    private final ConfigService configService;
    private final ShortMessageService shortMessageService;
    private final SmsCodeSender smsCodeSender;
    private final IpSmsCounterService ipSmsCounterService;
    private final SmsCooldownCache smsCooldownCache;
    private final CaptchaTokenService captchaTokenService;
    private final Props props;

    public SmsController(ConfigService configService, ShortMessageService shortMessageService,
                         SmsCodeSender smsCodeSender, IpSmsCounterService ipSmsCounterService,
                         SmsCooldownCache smsCooldownCache, CaptchaTokenService captchaTokenService, Props props) {
        this.configService = configService;
        this.shortMessageService = shortMessageService;
        this.smsCodeSender = smsCodeSender;
        this.ipSmsCounterService = ipSmsCounterService;
        this.smsCooldownCache = smsCooldownCache;
        this.captchaTokenService = captchaTokenService;
        this.props = props;
    }

    @Operation(summary = "发送手机短信")
    @PostMapping("/mobile")
    public ResponseEntity<Body> mobileMessage(@RequestBody @Valid ShortMessageParams params,
                                              HttpServletRequest request) {
        if (!MOBILE_USAGES.contains(params.usage)) {
            throw new Http400Exception("usage not allowed: " + params.usage);
        }
        // 验证码是否正确
        if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            return Responses.failure(request, "error.captchaIncorrect");
        }
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        Config.Sms sms = configService.getUnique().getSms();
        SmsCodeSender.SendResult result = smsCodeSender.sendMobileCode(params.receiver, params.usage, ip, sms);
        if (!result.isSuccess()) {
            return result.toFailure(request);
        }
        return Responses.ok(ImmutableMap.of("shortMessageId", result.getMessage().getId()));
    }

    @Operation(summary = "发送邮件短信")
    @PostMapping("/email")
    public ResponseEntity<Body> emailMessage(@RequestBody @Valid ShortMessageParams params,
                                             HttpServletRequest request) {
        if (!EMAIL_USAGES.contains(params.usage)) {
            throw new Http400Exception("usage not allowed: " + params.usage);
        }
        // 验证码是否正确
        if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            return Responses.failure(request, "error.captchaIncorrect");
        }
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        Config.Email emailConfig = configService.getUnique().getEmail();
        // IP发送短信是否超过限制次数
        if (ipSmsCounterService.isExcessive(ip, emailConfig.getMaxPerIp())) {
            return Responses.failure(request, "error.ipSendSmsExcessive");
        }
        // 短信功能未开启
        if (StringUtils.isBlank(emailConfig.getHost())) {
            return Responses.failure(request, "error.emailNotEnabled");
        }
        // 同一邮箱地址是否处于发送冷却中
        String cooldownKey = SmsCooldownCache.key(params.receiver);
        if (smsCooldownCache.isCoolingDown(cooldownKey)) {
            return Responses.failure(request, "error.codeSendCooldown", SmsCooldownCache.EXPIRES_SECONDS);
        }
        String code = Secures.randomNumeric(emailConfig.getCodeLength());
        String text = Strings.CS.replace(emailConfig.getText(), "${code}", code);
        emailConfig.sendMail(new String[]{params.receiver}, emailConfig.getSubject(), text);
        smsCooldownCache.start(cooldownKey);
        ipSmsCounterService.updateCount(ip);
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
