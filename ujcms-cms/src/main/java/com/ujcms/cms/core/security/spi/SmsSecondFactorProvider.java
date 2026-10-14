package com.ujcms.cms.core.security.spi;

import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.service.ShortMessageService;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Component;

/**
 * 默认第二因子实现：短信验证码。行为与原 {@code JwtAuthController} 内联的短信校验完全一致。
 *
 * @author PONY
 */
@Component
public class SmsSecondFactorProvider implements SecondFactorProvider {
    private final ShortMessageService shortMessageService;

    public SmsSecondFactorProvider(ShortMessageService shortMessageService) {
        this.shortMessageService = shortMessageService;
    }

    @Override
    public boolean validate(@Nullable String mobile, @Nullable Long messageId, @Nullable String code, int codeExpires) {
        // 只接受为“双因子登录”发送的验证码：注册、找回密码等用途的验证码不能充当登录第二因子
        return shortMessageService.validateCode(messageId, mobile, code, codeExpires, ShortMessage.USAGE_MFA_LOGIN);
    }
}
