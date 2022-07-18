package com.ujcms.cms.core.service;

import com.github.pagehelper.Page;
import com.github.pagehelper.PageHelper;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.ShortMessage;
import com.ujcms.cms.core.mapper.ShortMessageMapper;
import com.ujcms.cms.core.service.args.ShortMessageArgs;
import com.ujcms.util.query.QueryInfo;
import com.ujcms.util.query.QueryParser;

import java.util.List;
import java.util.Objects;

import com.ujcms.util.security.Secures;
import com.ujcms.util.sms.AliyunUtils;
import com.ujcms.util.sms.TencentUtils;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import static com.ujcms.cms.core.domain.Config.Sms.PROVIDER_ALIYUN;
import static com.ujcms.cms.core.domain.Config.Sms.PROVIDER_TENCENTCLOUD;

/**
 * 短信 Service
 *
 * @author PONY
 */
@Service
public class ShortMessageService {
    private ShortMessageMapper mapper;

    private SeqService seqService;

    public ShortMessageService(ShortMessageMapper mapper, SeqService seqService) {
        this.mapper = mapper;
        this.seqService = seqService;
    }

    @Transactional(rollbackFor = Exception.class)
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

    @Transactional(rollbackFor = Exception.class)
    public void insert(ShortMessage bean) {
        bean.setId(seqService.getNextVal(ShortMessage.TABLE_NAME));
        mapper.insert(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public void update(ShortMessage bean) {
        mapper.update(bean);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(int id) {
        return mapper.delete(id);
    }

    @Transactional(rollbackFor = Exception.class)
    public int delete(List<Integer> ids) {
        return ids.stream().filter(Objects::nonNull).mapToInt(this::delete).sum();
    }

    @Nullable
    public ShortMessage select(int id) {
        return mapper.select(id);
    }

    public List<ShortMessage> selectList(ShortMessageArgs args) {
        QueryInfo queryInfo = QueryParser.parse(args.getQueryMap(), ShortMessage.TABLE_NAME, "id_desc");
        return mapper.selectAll(queryInfo);
    }

    public List<ShortMessage> selectList(ShortMessageArgs args, int offset, int limit) {
        return PageHelper.offsetPage(offset, limit, false).doSelectPage(() -> selectList(args));
    }

    public Page<ShortMessage> selectPage(ShortMessageArgs args, int page, int pageSize) {
        return PageHelper.startPage(page, pageSize).doSelectPage(() -> selectList(args));
    }
}