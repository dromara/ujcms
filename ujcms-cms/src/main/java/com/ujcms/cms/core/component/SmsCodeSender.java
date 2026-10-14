package com.ujcms.cms.core.component;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.security.PiiNormalizer;
import com.ujcms.cms.core.service.ShortMessageService;
import com.ujcms.common.security.Secures;
import com.ujcms.common.sms.IpSmsCounterService;
import com.ujcms.common.sms.SmsCooldownCache;
import com.ujcms.common.sms.SmsError;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import jakarta.servlet.http.HttpServletRequest;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;

import java.time.Duration;
import java.time.OffsetDateTime;

/**
 * 手机验证码发送组件。封装“限额 → 短信服务是否开启 → 冷却 → 发送 → 记录”的完整流程，供各发送入口共用。
 * <p>
 * 手机号码先经 {@link PiiNormalizer} 规范化，再用于冷却键、发送与入库：否则 {@code 138 0013 8000} 与
 * {@code 13800138000} 会被当作两个号码，绕过同号码冷却。
 *
 * @author PONY
 */
@Component
public class SmsCodeSender {
    private static final Logger logger = LoggerFactory.getLogger(SmsCodeSender.class);
    /**
     * 同一手机号码每 24 小时可发送的双因子登录验证码数量上限。双因子验证码不受 IP 每日限额约束（见
     * {@link #sendMfaCode}），以此上限防止持有口令者反复触发短信轰炸登记号码、消耗短信费用。
     * 正常用户一天登录不了这么多次，无需做成配置项。
     */
    public static final int MFA_DAILY_LIMIT = 20;

    private final ShortMessageService shortMessageService;
    private final IpSmsCounterService ipSmsCounterService;
    private final SmsCooldownCache smsCooldownCache;

    public SmsCodeSender(ShortMessageService shortMessageService, IpSmsCounterService ipSmsCounterService,
                         SmsCooldownCache smsCooldownCache) {
        this.shortMessageService = shortMessageService;
        this.ipSmsCounterService = ipSmsCounterService;
        this.smsCooldownCache = smsCooldownCache;
    }

    /**
     * 公开接口发送手机验证码。调用方无需登录，因此受 IP 每日限额与同号码冷却双重限制。
     *
     * @param receiver 手机号码
     * @param usage    用途
     * @param ip       请求 IP
     * @param sms      短信配置
     */
    public SendResult sendMobileCode(String receiver, short usage, String ip, Config.Sms sms) {
        String mobile = PiiNormalizer.normalizeMobile(receiver);
        if (mobile == null) {
            throw new Http400Exception("receiver is not a valid mobile number");
        }
        // IP发送短信是否超过限制次数
        if (ipSmsCounterService.isExcessive(ip, sms.getMaxPerIp())) {
            return SendResult.failure("error.ipSendSmsExcessive");
        }
        // 短信功能未开启
        if (sms.getProvider() == Config.Sms.PROVIDER_NONE) {
            return SendResult.failure("error.smsNotEnabled");
        }
        // 同一手机号码是否处于发送冷却中
        String cooldownKey = SmsCooldownCache.key(mobile);
        if (smsCooldownCache.isCoolingDown(cooldownKey)) {
            return SendResult.failure("error.codeSendCooldown", SmsCooldownCache.EXPIRES_SECONDS);
        }
        String code = Secures.randomNumeric(sms.getCodeLength());
        SmsError error = shortMessageService.sendMobileMessage(mobile, code, sms);
        if (error != null) {
            // 公开接口不输出服务商的原始错误信息（可能泄露余额、密钥、签名模板等内部情况），只记录日志
            logger.warn("Failed to send SMS. IP: {}, error: {}", ip, error);
            return SendResult.failure(failureMessageKey(error));
        }
        smsCooldownCache.start(cooldownKey);
        ipSmsCounterService.updateCount(ip);
        return SendResult.ok(shortMessageService.insertMobileMessage(mobile, code, ip, usage),
                SmsCooldownCache.EXPIRES_SECONDS);
    }

    /**
     * 发送双因子登录验证码。调用方已通过口令校验，号码取自账号登记的手机号码，而非用户输入。
     * <p>
     * 与公开接口的差别：
     * <ul>
     *   <li>不受 IP 每日限额约束：整个单位常共用一个出口 IP，每次登录一条短信很快就会耗尽限额，把所有人挡在门外；
     *       改为按号码设每日上限（{@link #MFA_DAILY_LIMIT}）。</li>
     *   <li>幂等：冷却期内已发送且尚未使用的验证码直接复用，不重复发送。用户关闭对话框后马上重新登录，
     *       不会撞上“60 秒内只能发送一次”，也不会因丢失短信 ID 而无法使用刚收到的验证码。
     *       该判断以数据库为准，集群各节点结论一致。</li>
     * </ul>
     *
     * @param userMobile 账号登记的手机号码
     * @param ip         请求 IP
     * @param sms        短信配置
     */
    public SendResult sendMfaCode(String userMobile, String ip, Config.Sms sms) {
        String mobile = PiiNormalizer.normalizeMobile(userMobile);
        if (mobile == null) {
            throw new IllegalArgumentException("user mobile is blank");
        }
        // 短信功能未开启
        if (sms.getProvider() == Config.Sms.PROVIDER_NONE) {
            return SendResult.failure("error.smsNotEnabled");
        }
        ShortMessage latest = shortMessageService.findLatestUnused(mobile, ShortMessage.USAGE_MFA_LOGIN);
        if (latest != null) {
            long remaining = SmsCooldownCache.EXPIRES_SECONDS
                    - Duration.between(latest.getSendDate(), OffsetDateTime.now()).getSeconds();
            if (remaining > 0) {
                return SendResult.ok(latest, (int) remaining);
            }
        }
        // 冷却期内发送的验证码已被使用或作废（如刚登录成功又重新登录）：仍需等待冷却结束
        String cooldownKey = SmsCooldownCache.key(mobile);
        if (smsCooldownCache.isCoolingDown(cooldownKey)) {
            return SendResult.failure("error.codeSendCooldown", SmsCooldownCache.EXPIRES_SECONDS);
        }
        OffsetDateTime dayAgo = OffsetDateTime.now().minusDays(1);
        if (shortMessageService.countSince(mobile, ShortMessage.USAGE_MFA_LOGIN, dayAgo) >= MFA_DAILY_LIMIT) {
            return SendResult.failure("error.mfaSendExcessive");
        }
        String code = Secures.randomNumeric(sms.getCodeLength());
        SmsError error = shortMessageService.sendMobileMessage(mobile, code, sms);
        if (error != null) {
            logger.warn("Failed to send MFA SMS. IP: {}, error: {}", ip, error);
            return SendResult.failure(failureMessageKey(error));
        }
        smsCooldownCache.start(cooldownKey);
        return SendResult.ok(shortMessageService.insertMobileMessage(mobile, code, ip, ShortMessage.USAGE_MFA_LOGIN),
                SmsCooldownCache.EXPIRES_SECONDS);
    }

    /**
     * 短信平台错误对应的友好提示。提示信息注明来源为“短信平台”，与本系统自身的限制（“系统限制”）区分
     */
    private static String failureMessageKey(SmsError error) {
        return switch (error.kind()) {
            case RATE_LIMITED -> "error.smsPlatformRateLimited";
            case INVALID_MOBILE -> "error.smsPlatformMobileIncorrect";
            case OTHER -> "error.smsPlatformSendFailed";
        };
    }

    /**
     * 发送结果。成功时携带短信记录及距下次可发送的剩余秒数；失败时携带提示信息的消息键及参数。
     */
    public static final class SendResult {
        @Nullable
        private final ShortMessage message;
        @Nullable
        private final String errorKey;
        private final Object[] errorArgs;
        private final int cooldownSeconds;

        private SendResult(@Nullable ShortMessage message, int cooldownSeconds, @Nullable String errorKey,
                           Object[] errorArgs) {
            this.message = message;
            this.cooldownSeconds = cooldownSeconds;
            this.errorKey = errorKey;
            this.errorArgs = errorArgs;
        }

        static SendResult ok(ShortMessage message, int cooldownSeconds) {
            return new SendResult(message, cooldownSeconds, null, new Object[0]);
        }

        static SendResult failure(String errorKey, Object... errorArgs) {
            return new SendResult(null, 0, errorKey, errorArgs);
        }

        public boolean isSuccess() {
            return message != null;
        }

        /**
         * 发送成功的短信记录。发送失败时调用将抛出异常
         */
        public ShortMessage getMessage() {
            if (message == null) {
                throw new IllegalStateException("SMS code was not sent: " + errorKey);
            }
            return message;
        }

        /**
         * 距下次可发送的剩余秒数。复用冷却期内的验证码时，为该验证码剩余的冷却时间
         */
        public int getCooldownSeconds() {
            return cooldownSeconds;
        }

        /**
         * 发送失败的响应
         */
        public ResponseEntity<Body> toFailure(HttpServletRequest request) {
            if (errorKey == null) {
                throw new IllegalStateException("SMS code was sent successfully");
            }
            return Responses.failure(request, errorKey, errorArgs);
        }
    }
}
