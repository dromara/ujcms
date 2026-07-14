package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 私信会话 查询参数
 *
 * @author PONY
 */
public class ConversationArgs extends BaseQueryArgs {
    public ConversationArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public static ConversationArgs of() {
        return of(new HashMap<>(16));
    }

    public static ConversationArgs of(Map<String, Object> queryMap) {
        return new ConversationArgs(queryMap);
    }

    private ConversationArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
