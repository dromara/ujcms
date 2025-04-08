package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Org;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.captcha.CaptchaTokenService;
import com.ujcms.commons.security.Secures;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.NotBlank;

import java.util.Collections;
import java.util.regex.Pattern;

import static com.ujcms.cms.core.domain.Config.Register.*;
import static com.ujcms.cms.core.domain.User.STATUS_ENABLED;
import static com.ujcms.cms.core.domain.User.STATUS_UNACTIVATED;
import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 注册 Controller
 *
 * @author PONY
 */
@Tag(name = "注册接口")
@RestController
@RequestMapping({API + "/register", FRONTEND_API + "/register"})
public class RegisterController {
    private final CaptchaTokenService captchaTokenService;
    private final ShortMessageService shortMessageService;
    private final UserService userService;
    private final ConfigService configService;
    private final Props props;

    public RegisterController(CaptchaTokenService captchaTokenService, ShortMessageService shortMessageService,
                              UserService userService, ConfigService configService, Props props) {
        this.captchaTokenService = captchaTokenService;
        this.shortMessageService = shortMessageService;
        this.userService = userService;
        this.configService = configService;
        this.props = props;
    }

    @Operation(summary = "用户注册")
    @PostMapping
    public ResponseEntity<Body> register(@RequestBody @Valid RegisterParams params, HttpServletRequest request) {
        Config config = configService.getUnique();
        Config.Register register = config.getRegister();
        if (!register.isEnabled()) {
            throw new Http404Exception("error.registerNotEnabled");
        }
        validateRegister(params, register);
        int verifyMode = register.getVerifyMode();
        User user = new User();
        Entities.copyIncludes(params, user, "username", "nickname", "realName", "gender", "birthday",
                "location", "bio");
        switch (verifyMode) {
            case VERIFY_MODE_NONE:
            case VERIFY_MODE_MANUAL:
                // 验证码是否正确
                if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
                    throw new Http400Exception("error.captchaIncorrect");
                }
                break;
            case VERIFY_MODE_EMAIL:
                if (!shortMessageService.validateCode(params.emailMessageId, params.getEmail(),
                        params.emailMessageValue, config.getEmail().getCodeExpires())) {
                    throw new Http400Exception("error.emailMessageIncorrect");
                }
                user.setEmail(params.getEmail());
                break;
            case VERIFY_MODE_MOBILE:
                if (!shortMessageService.validateCode(params.mobileMessageId, params.getMobile(),
                        params.mobileMessageValue, config.getSms().getCodeExpires())) {
                    throw new Http400Exception("error.mobileMessageIncorrect");
                }
                user.setMobile(params.getMobile());
                break;
            default:
                throw new IllegalStateException("Registration verify mode is illegal: " + verifyMode);
        }
        user.setStatus(verifyMode == VERIFY_MODE_MANUAL ? STATUS_UNACTIVATED : STATUS_ENABLED);
        user.setGroupId(Group.MEMBER_GROUP_ID);
        user.setOrgId(Org.MEMBER_ORG_ID);
        user.setType(User.TYPE_MEMBER);
        userService.insert(user, user.getExt(), Collections.emptyList());
        String password = Secures.sm2Decrypt(params.getPassword(), props.getClientSm2PrivateKey());
        userService.updatePassword(user, user.getExt(), password);
        if (verifyMode == VERIFY_MODE_MANUAL) {
            return Responses.status(100, request, "register.waitForReview");
        }
        return Responses.ok();
    }

    private void validateRegister(RegisterParams params, Config.Register register) {
        if (!Pattern.matches(register.getUsernameRegex(), params.getUsername())) {
            throw new Http400Exception(String.format("username '%s' not matches pattern: %s",
                    params.getUsername(), register.getUsernameRegex()));
        }
        if (userService.selectByUsername(params.getUsername()) != null) {
            throw new Http400Exception("username already exists");
        }
        String mobile = params.getMobile();
        if (StringUtils.isNotBlank(mobile) && userService.selectByMobile(mobile) != null) {
            throw new Http400Exception("mobile already exists");
        }
        String email = params.getEmail();
        if (StringUtils.isNotBlank(email) && userService.selectByEmail(email) != null) {
            throw new Http400Exception("mobile already exists");
        }
    }

    @Schema(name = "RegisterController.RegisterParams", description = "注册参数")
    public static class RegisterParams extends User {
        private static final long serialVersionUID = 1L;
        @Schema(description = "验证码Token")
        public String captchaToken;
        @Schema(description = "验证码")
        public String captcha;
        @Schema(description = "邮件短信ID")
        public Long emailMessageId;
        @Schema(description = "邮件验证码")
        public String emailMessageValue;
        @Schema(description = "手机短信ID")
        public Long mobileMessageId;
        @Schema(description = "短信验证码")
        public String mobileMessageValue;

        @Schema(description = "密码")
        @Length(max = 1000)
        @NotBlank
        private String password;

        @NonNull
        @Override
        public String getPassword() {
            return this.password;
        }

        @Override
        public void setPassword(String password) {
            this.password = password;
        }
    }
}
