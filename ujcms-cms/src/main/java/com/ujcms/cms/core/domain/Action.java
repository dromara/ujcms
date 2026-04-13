package com.ujcms.cms.core.domain;

import org.springframework.lang.Nullable;

import com.ujcms.cms.core.domain.generated.GeneratedAction;

import java.io.Serial;

/**
 * 动作 实体类
 *
 * @author PONY
 */
public class Action extends GeneratedAction {
    @Serial
    private static final long serialVersionUID = 1;

    public Action() {
    }

    public Action(String refType, Long refId, String refOption,
                  Long siteId, @Nullable Long userId, String ip, Long cookie) {
        setRefType(refType);
        setRefId(refId);
        setRefOption(refOption);

        setSiteId(siteId);
        setUserId(userId);
        setIp(ip);
        setCookie(cookie);
    }
}