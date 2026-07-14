package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 私信 查询参数
 *
 * @author PONY
 */
public class MessageArgs extends BaseQueryArgs {
    public MessageArgs conversationId(@Nullable Long conversationId) {
        if (conversationId != null) {
            queryMap.put("EQ_conversationId_Long", conversationId);
        }
        return this;
    }

    /**
     * 只获取ID小于该值的消息（向前翻阅历史，游标分页不受并发新消息影响）
     */
    public MessageArgs beforeId(@Nullable Long beforeId) {
        if (beforeId != null) {
            queryMap.put("LT_id_Long", beforeId);
        }
        return this;
    }

    /**
     * 只获取ID大于该值的消息（打开会话时轮询新消息）
     */
    public MessageArgs afterId(@Nullable Long afterId) {
        if (afterId != null) {
            queryMap.put("GT_id_Long", afterId);
        }
        return this;
    }

    public static MessageArgs of() {
        return of(new HashMap<>(16));
    }

    public static MessageArgs of(Map<String, Object> queryMap) {
        return new MessageArgs(queryMap);
    }

    private MessageArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
