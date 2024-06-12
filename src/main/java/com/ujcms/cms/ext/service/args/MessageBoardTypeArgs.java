package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * MessageBoardType 查询参数
 *
 * @author Generator
 */
public class MessageBoardTypeArgs extends BaseQueryArgs {
    private MessageBoardTypeArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public static MessageBoardTypeArgs of() {
        return of(new HashMap<>(16));
    }

    public static MessageBoardTypeArgs of(Map<String, Object> queryMap) {
        return new MessageBoardTypeArgs(queryMap);
    }

    public MessageBoardTypeArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }
}