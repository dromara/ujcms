package com.ujcms.cms.core.security.spi;

import org.jspecify.annotations.Nullable;

/**
 * 第二因子校验 SPI（双因素认证）。
 * <p>
 * 默认实现 {@link SmsSecondFactorProvider} 即当前的短信验证码校验，行为与原内联逻辑一致。
 * 后续若某测评机构不接受短信作为第二因子，可新增 SM2 软证书 / USBKey 等实现替换，
 * 无需改动登录调用点。属于 Phase 2 引入的 SPI 接缝（默认实现 = 现有行为）。
 *
 * @author PONY
 */
public interface SecondFactorProvider {
    /**
     * 校验第二因子是否通过。
     *
     * @param mobile      用户手机号（短信因子的接收方）
     * @param messageId   短信记录 ID
     * @param code        用户提交的验证码
     * @param codeExpires 验证码有效期（分钟）
     * @return 校验通过返回 {@code true}
     */
    boolean validate(@Nullable String mobile, @Nullable Long messageId, @Nullable String code, int codeExpires);
}
