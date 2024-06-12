package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.page.PageMethod;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.mapper.ShortMessageMapper;
import com.ujcms.cms.core.service.args.ShortMessageArgs;
import com.ujcms.commons.db.identifier.SnowflakeSequence;
import com.ujcms.commons.query.QueryInfo;
import com.ujcms.commons.query.QueryParser;
import com.ujcms.commons.sms.AliyunUtils;
import com.ujcms.commons.sms.TencentUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

import static com.ujcms.cms.core.domain.Config.Sms.PROVIDER_ALIYUN;
import static com.ujcms.cms.core.domain.Config.Sms.PROVIDER_TENCENTCLOUD;

/**
 * 短信 Service
 *
 * @author PONY
 */
@Service
public class ShortMessageService {
    private final ShortMessageMapper mapper;
    private final SnowflakeSequence snowflakeSequence;

    public ShortMessageService(ShortMessageMapper mapper, SnowflakeSequence snowflakeSequence) {
        this.mapper = mapper;
        this.snowflakeSequence = snowflakeSequence;
    }

    /**
     * 验证短消息是否正确。不管是否成功验证，该短消息都会被标记为已使用，不可再次验证。
     *
     * @param id       短消息ID
     * @param receiver 接收者（手机号码或邮箱地址）
     * @param code     验证码
     * @param expires  过期时间
     * @return 是否验证成功
     */
    @Transactional(rollbackFor = Exception.class)
    public boolean validateCode(@Nullable Long id, @Nullable String receiver, @Nullable String code, int expires) {
        if (id == null) {
            return false;
        }
        ShortMessage bean = select(id);
        if (bean == null || bean.isUsed()) {
            return false;
        }
        if (bean.isExpired(expires)) {
            bean.setStatus(ShortMessage.STATUS_EXPIRED);
        } else if (bean.isWrong(receiver, code)) {
            bean.setStatus(ShortMessage.STATUS_WRONG);
        } else {
            bean.setStatus(ShortMessage.STATUS_CORRECT);
        }
        update(bean);
        return bean.getStatus() == ShortMessage.STATUS_CORRECT;
    }

    /**
     * 尝试短消息是否正确。
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
        if (bean == null || bean.isUsed()) {
            return false;
        }
        if (bean.isExpired(expires)) {
            bean.setStatus(ShortMessage.STATUS_EXPIRED);
            update(bean);
            return false;
        }
        if (bean.isWrong(receiver, code)) {
            bean.setAttempts(bean.getAttempts() + 1);
            if (bean.getAttempts() > ShortMessage.MAX_ATTEMPTS) {
                bean.setStatus(ShortMessage.STATUS_EXCEEDED);
            }
            update(bean);
            return false;
        }
        return true;
    }

    @Nullable
    public String sendMobileMessage(String mobile, String code, Config.Sms sms) {
        if (sms.getProvider() == PROVIDER_ALIYUN) {
            return AliyunUtils.sendSms(sms.getAccessKeyId(), sms.getAccessKeySecret(),
                    sms.getSignName(), sms.getTemplateCode(), sms.getCodeName(), code, mobile);
        } else if (sms.getProvider() == PROVIDER_TENCENTCLOUD) {
            return TencentUtils.sendSms(sms.getSecretId(), sms.getSecretKey(), sms.getSdkAppId(), sms.getRegion(),
                    sms.getSignName(), sms.getTemplateId(), code, mobile);
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
        String text = StringUtils.replace(email.getText(), "${code}", code);
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

    public List<ShortMessage> selectList(ShortMessageArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ShortMessage.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<ShortMessage> selectList(ShortMessageArgs args, int offset, int limit) {
        return PageMethod.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<ShortMessage> selectPage(ShortMessageArgs args, int page, int pageSize) {
        return PageMethod.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}