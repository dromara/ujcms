package com.ujcms.cms.core.service;

import static com.ujcms.cms.core.domain.Config.Sms.PROVIDER_ALIYUN;
import static com.ujcms.cms.core.domain.Config.Sms.PROVIDER_TENCENTCLOUD;

import java.time.OffsetDateTime;
import java.util.List;
import java.util.Objects;

import com.ujcms.cms.core.domain.generated.GeneratedShortMessage;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.component.AliyunSmsService;
import com.ujcms.cms.core.component.TencentSmsService;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.mapper.ShortMessageMapper;
import com.ujcms.cms.core.service.args.ShortMessageArgs;
import com.ujcms.common.db.identifier.SnowflakeSequence;
import com.ujcms.common.query.QueryInfo;
import com.ujcms.common.query.QueryParser;
import com.ujcms.common.sms.SmsError;

/**
 * 短信 Service
 *
 * @author PONY
 */
@Service
public class ShortMessageService {
    private final ShortMessageMapper mapper;
    private final SnowflakeSequence snowflakeSequence;
    private final TencentSmsService tencentSmsService;
    private final AliyunSmsService aliyunSmsService;

    public ShortMessageService(ShortMessageMapper mapper, SnowflakeSequence snowflakeSequence, 
            TencentSmsService tencentSmsService, AliyunSmsService aliyunSmsService) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
        this.tencentSmsService = tencentSmsService;
        this.aliyunSmsService = aliyunSmsService;
    }

    /**
     * 验证短消息是否正确。验证成功后该短消息被消费，不可再次验证。
     * <p>
     * 验证码与用途绑定：为“注册”发送的验证码不能用于“双因子登录”，反之亦然。否则诱导用户交出任一用途的验证码，
     * 即可通过其它用途的校验。
     * <p>
     * 每次校验先由数据库原子占用一次尝试机会，再比较验证码。因此同一验证码的总校验次数（含并发提交）不会超过
     * {@link ShortMessage#MAX_ATTEMPTS}；输错不会立即作废，用户可在次数内更正。
     * <p>
     * 接收者不一致时直接返回失败、不占用尝试机会：此时并未比较验证码，不泄露任何信息；而短消息 ID 可被猜测，
     * 若计入尝试次数，任何人都能凭 ID 把别人的验证码耗尽作废。
     *
     * @param id       短消息ID
     * @param receiver 接收者（手机号码或邮箱地址）
     * @param code     验证码
     * @param expires  过期时间
     * @param usage    用途。必须与发送时的用途一致
     * @return 是否验证成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean validateCode(@Nullable Long id, @Nullable String receiver, @Nullable String code, int expires,
                                short usage) {
        if (id == null) {
            return false;
        }
        ShortMessage bean = select(id);
        if (bean == null || bean.isUsed() || bean.getUsage() != usage || !bean.isSameReceiver(receiver)) {
            return false;
        }
        if (bean.isExpired(expires)) {
            mapper.updateStatusIfUnused(id, ShortMessage.STATUS_EXPIRED);
            return false;
        }
        // 占用尝试机会失败：已被其它请求使用，或尝试次数已达上限
        if (mapper.increaseAttempts(id, ShortMessage.MAX_ATTEMPTS) <= 0) {
            mapper.exceedIfAttemptsReached(id, ShortMessage.MAX_ATTEMPTS, ShortMessage.STATUS_EXCEEDED);
            return false;
        }
        if (bean.isWrong(receiver, code)) {
            mapper.exceedIfAttemptsReached(id, ShortMessage.MAX_ATTEMPTS, ShortMessage.STATUS_EXCEEDED);
            return false;
        }
        // 条件更新抢占消费：并发提交同一正确验证码时，只有一个请求成功
        return mapper.updateStatusIfUnused(id, ShortMessage.STATUS_CORRECT) > 0;
    }

    /**
     * 尝试短消息是否正确。用于表单提交前的预校验，不消费验证码；只有输错才计入尝试次数。
     * <p>
     * 双因子登录的验证码不可经此预校验：它只能在登录流程中校验，输错计入登录失败次数。否则本接口就成了绕开登录锁定的试探入口。
     *
     * @param id       短消息ID
     * @param receiver 接收者（手机号码或邮箱地址）
     * @param code     验证码
     * @param expires  过期时间
     * @param length   验证码长度。验证码长度不符，不计入错误次数。
     * @return 尝试是否成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean tryCode(Long id, String receiver, String code, int expires, int length) {
        if (code.length() != length) {
            return false;
        }
        ShortMessage bean = select(id);
        if (bean == null || bean.isUsed() || bean.getUsage() == ShortMessage.USAGE_MFA_LOGIN
                || !bean.isSameReceiver(receiver)) {
            return false;
        }
        if (bean.isExpired(expires)) {
            mapper.updateStatusIfUnused(id, ShortMessage.STATUS_EXPIRED);
            return false;
        }
        if (bean.isWrong(receiver, code)) {
            mapper.increaseAttempts(id, ShortMessage.MAX_ATTEMPTS);
            mapper.exceedIfAttemptsReached(id, ShortMessage.MAX_ATTEMPTS, ShortMessage.STATUS_EXCEEDED);
            return false;
        }
        return true;
    }

    /**
     * 发送验证码短信。
     *
     * @return 错误信息。{@code null} 代表发送成功
     */
    @Nullable
    public SmsError sendMobileMessage(String mobile, String code, Config.Sms sms) {
        if (sms.getProvider() == PROVIDER_ALIYUN) {
            return aliyunSmsService.sendSms(sms.getAccessKeyId(), sms.getAccessKeySecret(),
                    sms.getSignName(), sms.getTemplateCode(), sms.getCodeName(), code, mobile);
        } else if (sms.getProvider() == PROVIDER_TENCENTCLOUD) {
            TencentSmsService.SmsConfig config = new TencentSmsService.SmsConfig(sms.getSecretId(), sms.getSecretKey(), 
                    sms.getSdkAppId(), sms.getRegion(), sms.getSignName(), sms.getTemplateId());
            return tencentSmsService.sendSms(config, mobile, code);
        } else {
            throw new IllegalArgumentException("Short message provider not support: " + sms.getProvider());
        }
    }

    /**
     * 发送通知类短信。与验证码短信共用服务商凭证和签名，但使用调用方指定的通知模板和模板参数。
     *
     * @param mobile       手机号码
     * @param templateCode 通知短信模板（阿里云为模板Code，腾讯云为模板ID）
     * @param paramName    模板变量名称（仅阿里云需要，腾讯云为位置参数）
     * @param paramValue   模板变量值
     * @param sms          短信配置（服务商凭证、签名）
     * @return 错误信息。{@code null} 代表发送成功
     */
    @Nullable
    public SmsError sendNotificationMessage(String mobile, String templateCode, String paramName, String paramValue,
                                          Config.Sms sms) {
        if (sms.getProvider() == PROVIDER_ALIYUN) {
            return aliyunSmsService.sendSms(sms.getAccessKeyId(), sms.getAccessKeySecret(),
                    sms.getSignName(), templateCode, paramName, paramValue, mobile);
        } else if (sms.getProvider() == PROVIDER_TENCENTCLOUD) {
            TencentSmsService.SmsConfig config = new TencentSmsService.SmsConfig(sms.getSecretId(), sms.getSecretKey(),
                    sms.getSdkAppId(), sms.getRegion(), sms.getSignName(), templateCode);
            return tencentSmsService.sendSms(config, mobile, paramValue);
        } else {
            throw new IllegalArgumentException("Short message provider not support: " + sms.getProvider());
        }
    }

    @Transactional(rollbackFor = Exception.class)
    public ShortMessage insertMobileMessage(String mobile, String code, String ip, short usage) {
        ShortMessage shortMessage = new ShortMessage(ShortMessage.TYPE_MOBILE, mobile, code, ip, usage);
        insert(shortMessage);
        return shortMessage;
    }

    public void sendEmailMessage(String to, String code, Config.Email email) {
        String text = Strings.CS.replace(email.getText(), "${code}", code);
        email.sendMail(new String[]{to}, email.getSubject(), text);
    }

    @Transactional(rollbackFor = Exception.class)
    public ShortMessage insertEmailMessage(String email, String code, String ip, short usage) {
        ShortMessage shortMessage = new ShortMessage(ShortMessage.TYPE_EMAIL, email, code, ip, usage);
        insert(shortMessage);
        return shortMessage;
    }

    @Transactional(rollbackFor = Exception.class)
    public void insert(ShortMessage bean) {
        bean.setId(snowflakeSequence.nextId());
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ShortMessage bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(Long id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Long> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public ShortMessage select(Long id) {
        return mapper.select(id);
    }

    /**
     * 获取指定接收者、指定用途最近一条未使用的短消息
     */
    @Nullable
    public ShortMessage findLatestUnused(String receiver, short usage) {
        ShortMessageArgs args = ShortMessageArgs.of().receiver(receiver).usage(usage)
                .status(ShortMessage.STATUS_UNUSED);
        return selectList(args, 0, 1).stream().findFirst().orElse(null);
    }

    /**
     * 统计指定接收者、指定用途自某一时刻起的发送数量
     */
    public long countSince(String receiver, short usage, OffsetDateTime begin) {
        ShortMessageArgs args = ShortMessageArgs.of().receiver(receiver).usage(usage).geSendDate(begin);
        return PageMethod.count(() -> selectList(args));
    }

    public List<ShortMessage> selectList(ShortMessageArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), GeneratedShortMessage.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<ShortMessage> selectList(ShortMessageArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<ShortMessage> selectPage(ShortMessageArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}