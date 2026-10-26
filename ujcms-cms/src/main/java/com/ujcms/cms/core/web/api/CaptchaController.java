package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.component.CaptchaGuard;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.captcha.CaptchaToken;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.captcha.IpLoginAttemptService;
import com.ujcms.common.web.Servlets;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.lang3.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;

/**
 * 验证码 Controller。契约见 docs/spec/captcha/plan.md。
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
    private final CaptchaGuard guard;
    private final Props props;

    public CaptchaController(ConfigService configService, IpLoginAttemptService ipLoginAttemptService,
                             CaptchaTokenService service, CaptchaGuard guard, Props props) {
        this.configService = configService;
        this.ipLoginAttemptService = ipLoginAttemptService;
        this.service = service;
        this.guard = guard;
        this.props = props;
    }

    @Operation(summary = "获取验证码题目。默认为全局设置的类型；可指定 slider / char（无障碍回退）/ pow（互动场景的静默验证）")
    @GetMapping()
    public CaptchaToken captchaToken(
            @Parameter(description = "类型。缺省为全局设置的类型") @RequestParam(required = false) String type,
            HttpServletRequest request) {
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        return service.getCaptchaToken(guard.resolveType(type), ip);
    }

    @Operation(summary = "当前生效的验证码类型。组件的弹层模式据此决定：厂商题即时渲染（厂商自带弹层），自托管题延迟到提交时弹出")
    @GetMapping("/type")
    public TypeResult type() {
        return new TypeResult(guard.configuredType());
    }

    @Operation(summary = "尝试验证码是否正确。不消费令牌；第三方厂商类型不支持，恒为 false")
    @GetMapping("/try")
    public boolean tryCaptcha(@Parameter(description = "验证码Token") @RequestParam(required = false) String token,
                              @Parameter(description = "验证码") @RequestParam(required = false) String captcha) {
        if (StringUtils.isBlank(token) || StringUtils.isBlank(captcha)) {
            return false;
        }
        return service.tryCaptcha(token, captcha);
    }

    @Operation(summary = "是否需要显示验证码。不带 scope 为登录：登录错误超过指定次数后需要输入验证码；"
            + "带 scope（comment、messageBoard、form、mailbox、disclosure、interview、example）为互动场景：同一 IP 的提交次数达到免验证额度后需要")
    @GetMapping("/is-display")
    public boolean isDisplayCaptcha(
            @Parameter(description = "互动场景。缺省为登录") @RequestParam(required = false) String scope,
            HttpServletRequest request) {
        if (StringUtils.isNotBlank(scope)) {
            return guard.isVisibleRequired(request, scope);
        }
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        Config.Security security = configService.getUnique().getSecurity();
        return ipLoginAttemptService.isExcessive(ip, security.getIpCaptchaAttempts());
    }

    /**
     * 当前生效的验证码类型：slider | char | 厂商代码（未注册的厂商已回落为 slider）
     */
    public record TypeResult(String type) {
    }
}
