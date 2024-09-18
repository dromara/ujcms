package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 留言板 查询参数
 *
 * @author PONY
 */
public class MessageBoardArgs extends BaseQueryArgs {
    public MessageBoardArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public MessageBoardArgs typeId(@Nullable Long typeId) {
        if (typeId != null) {
            queryMap.put("EQ_typeId_Long", typeId);
        }
        return this;
    }

    public MessageBoardArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public MessageBoardArgs isRecommended(@Nullable Boolean isRecommended) {
        if (isRecommended != null) {
            queryMap.put("EQ_recommended_Boolean", isRecommended);
        }
        return this;
    }

    public MessageBoardArgs isReplied(@Nullable Boolean isReplied) {
        if (isReplied != null) {
            queryMap.put("EQ_replied_Boolean", isReplied);
        }
        return this;
    }

    public MessageBoardArgs status(Collection<Short> status) {
        if (!status.isEmpty()) {
            queryMap.put("In_status_Short", status);
        }
        return this;
    }


    public static MessageBoardArgs of() {
        return of(new HashMap<>(16));
    }

    public static MessageBoardArgs of(Map<String, Object> queryMap) {
        return new MessageBoardArgs(queryMap);
    }

    private MessageBoardArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
