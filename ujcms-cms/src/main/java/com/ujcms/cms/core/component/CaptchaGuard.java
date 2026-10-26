package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.captcha.CaptchaProvider;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.captcha.CharCaptchaProvider;
import com.ujcms.common.captcha.IpSubmitCounterService;
import com.ujcms.common.captcha.PowCaptchaProvider;
import com.ujcms.common.captcha.SliderCaptchaProvider;
import com.ujcms.common.web.Servlets;
import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Component;

import jakarta.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 人机验证策略（docs/spec/captcha/plan.md）：互动场景的“静默直至升级”与类型策略。
 * <p>
 * 互动场景（评论、留言、表单、信箱、依申请公开、访谈提问、示例）在同一 IP 一小时内被接受的提交未达
 * {@link Config.Captcha#getSilentSubmits()} 时只要求静默的工作量证明令牌，达到后要求可见挑战；
 * 登录、注册、短信等不在此列的消费者仍直接调用 {@link CaptchaTokenService#validateCaptcha(String, String)}，始终可见。
 * 蜜罐字段非空即拒绝。
 * <p>
 * 类型策略：{@code GET /api/captcha} 默认给全局设置的类型；浏览器可指定 {@code slider}、{@code char}（无障碍回退）
 * 或 {@code pow}（静默场景）；设置的厂商类型在当前发行版未注册（社区版库里残留厂商设置）时回落到滑块。
 * 工作量证明的难度由此处接入全局设置。
 *
 * @author PONY
 */
@Component
public class CaptchaGuard {
    private static final Logger logger = LoggerFactory.getLogger(CaptchaGuard.class);

    public static final String SCOPE_COMMENT = "comment";
    public static final String SCOPE_MESSAGE_BOARD = "messageBoard";
    public static final String SCOPE_FORM = "form";
    public static final String SCOPE_MAILBOX = "mailbox";
    public static final String SCOPE_DISCLOSURE = "disclosure";
    public static final String SCOPE_INTERVIEW = "interview";
    public static final String SCOPE_EXAMPLE = "example";
    /**
     * 受保护场景。{@code is-display?scope=} 只认这些
     */
    public static final Set<String> SCOPES = Set.of(SCOPE_COMMENT, SCOPE_MESSAGE_BOARD, SCOPE_FORM, SCOPE_MAILBOX,
            SCOPE_DISCLOSURE, SCOPE_INTERVIEW, SCOPE_EXAMPLE);
    /**
     * 任何场景都可请求的自托管类型
     */
    private static final Set<String> SELF_HOSTED = Set.of(SliderCaptchaProvider.TYPE, CharCaptchaProvider.TYPE,
            PowCaptchaProvider.TYPE);

    public static final String ERROR_INCORRECT = "error.captchaIncorrect";
    public static final String ERROR_REQUIRED = "error.captchaRequired";

    private final CaptchaTokenService tokenService;
    private final IpSubmitCounterService counterService;
    private final ConfigService configService;
    private final Props props;

    public CaptchaGuard(CaptchaTokenService tokenService, IpSubmitCounterService counterService,
                        ConfigService configService, Props props, PowCaptchaProvider powCaptchaProvider) {
        this.tokenService = tokenService;
        this.counterService = counterService;
        this.configService = configService;
        this.props = props;
        powCaptchaProvider.setDifficulty(() -> configService.getUnique().getCaptcha().getPowDifficulty());
    }

    /**
     * 当前生效的类型：全局设置的类型；未注册时回落到滑块
     */
    public String configuredType() {
        String type = configService.getUnique().getCaptcha().getType();
        if (tokenService.supports(type)) {
            return type;
        }
        if (logger.isWarnEnabled()) {
            logger.warn("captcha type '{}' is not available in this edition, falling back to '{}'", type,
                    SliderCaptchaProvider.TYPE);
        }
        return SliderCaptchaProvider.TYPE;
    }

    /**
     * 解析浏览器请求的类型
     *
     * @param requested {@code ?type=}，可为 null
     * @return 出题类型
     * @throws Http400Exception 请求的类型既不是生效类型也不是自托管类型
     */
    public String resolveType(@Nullable String requested) {
        String configured = configuredType();
        if (StringUtils.isBlank(requested) || requested.equals(configured)) {
            return configured;
        }
        if (SELF_HOSTED.contains(requested) && tokenService.supports(requested)) {
            return requested;
        }
        throw new Http400Exception("Captcha type not allowed: " + requested);
    }

    /**
     * 当前生效类型对应的 CSP 来源（第三方厂商脚本、接口、iframe 的域名）；自托管类型为空
     */
    public Map<String, List<String>> cspSources() {
        CaptchaProvider provider = tokenService.provider(configuredType());
        return provider == null ? Map.of() : provider.cspSources();
    }

    /**
     * 该场景当前是否要求可见挑战（{@code GET /api/captcha/is-display?scope=}）。未知场景一律要求可见
     */
    public boolean isVisibleRequired(HttpServletRequest request, String scope) {
        if (!SCOPES.contains(scope)) {
            return true;
        }
        return !silentAllowed(scope, ip(request));
    }

    /**
     * 校验互动场景的提交
     *
     * @param request  请求（取 IP）
     * @param scope    场景
     * @param token    captchaToken
     * @param captcha  答案
     * @param honeypot 蜜罐字段的值。人不会填，非空即机器人
     * @return 通过返回 {@code null}，否则返回错误文案键（{@value #ERROR_INCORRECT} / {@value #ERROR_REQUIRED}）
     */
    @Nullable
    public String verify(HttpServletRequest request, String scope, @Nullable String token, @Nullable String captcha,
                         @Nullable String honeypot) {
        if (StringUtils.isNotBlank(honeypot)) {
            return ERROR_INCORRECT;
        }
        String ip = ip(request);
        boolean allowSilent = silentAllowed(scope, ip);
        CaptchaTokenService.Verdict verdict = tokenService.validate(token, captcha, allowSilent);
        return switch (verdict) {
            case OK -> {
                counterService.increase(scope, ip);
                yield null;
            }
            case VISIBLE_REQUIRED -> ERROR_REQUIRED;
            case INCORRECT -> ERROR_INCORRECT;
        };
    }

    private boolean silentAllowed(String scope, String ip) {
        int limit = configService.getUnique().getCaptcha().getSilentSubmits();
        return !counterService.isExcessive(scope, ip, limit);
    }

    private String ip(HttpServletRequest request) {
        return Servlets.getRemoteAddr(request, props.getIpProxyDepth());
    }
}
