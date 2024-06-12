package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.ActionBase;
import org.springframework.lang.Nullable;

import java.io.Serializable;

/**
 * 动作 实体类
 *
 * @author PONY
 */
public class Action extends ActionBase implements Serializable {
    private static final long serialVersionUID = 1L;

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