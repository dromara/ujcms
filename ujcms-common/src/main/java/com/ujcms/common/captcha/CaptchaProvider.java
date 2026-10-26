package com.ujcms.common.captcha;

import java.util.List;
import java.util.Map;

/**
 * 验证码提供者：一种人机验证方式（字符图片、滑块拼图、工作量证明、第三方厂商）。
 * <p>
 * 提供者只负责两件事：出题（{@link #challenge()}，把需要留在服务端校验的内容写进令牌声明，把需要发给浏览器的内容写进
 * 载荷）与判题（{@link #verify(Map, String)}）。令牌签发、过期、一次性消费与尝试次数由 {@link CaptchaTokenService}
 * 统一处理，提供者不必关心。写进声明的内容会随令牌发给浏览器，因此答案必须哈希（字符）或加密（滑块），
 * 见 {@link CaptchaCrypto}。
 *
 * @author PONY
 */
public interface CaptchaProvider {
    /**
     * 类型代码。即 {@code GET /api/captcha?type=} 的取值与令牌的 {@code typ} 声明
     */
    String type();

    /**
     * 是否为可见挑战（需要用户操作）。工作量证明等静默验证为 {@code false}：
     * {@link CaptchaTokenService#validateCaptcha(String, String)} 不接受静默令牌，只有显式允许静默的调用方才接受
     */
    default boolean visible() {
        return true;
    }

    /**
     * 是否支持提交前预检（{@code GET /api/captcha/try}）。第三方厂商的凭证只能校验一次，不支持预检
     */
    default boolean supportsTry() {
        return true;
    }

    /**
     * 浏览器端需要放行的内容安全策略来源：CSP 指令名（{@code script-src}、{@code connect-src}、{@code frame-src}、
     * {@code img-src}、{@code style-src}）到来源列表。控制台页面带严格 CSP，第三方厂商的脚本、接口与 iframe 域名
     * 须在此声明；自托管类型为空
     */
    default Map<String, List<String>> cspSources() {
        return Map.of();
    }

    /**
     * 出题
     *
     * @return 令牌声明与浏览器载荷
     */
    CaptchaChallenge challenge();

    /**
     * 判题
     *
     * @param claims 令牌声明（出题时写入的内容，外加 {@link CaptchaTokenService#CLAIM_TYPE}、{@link CaptchaTokenService#CLAIM_IP}）
     * @param answer 用户答案（已去除首尾空白，非空）
     * @return 是否通过
     */
    boolean verify(Map<String, Object> claims, String answer);
}
