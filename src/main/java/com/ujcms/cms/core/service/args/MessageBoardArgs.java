package com.ujcms.cms.core.service.args;

import com.ujcms.util.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 留言版查询参数
 *
 * @author PONY
 */
public class MessageBoardArgs extends BaseQueryArgs {
    public MessageBoardArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Int", siteId);
        }
        return this;
    }

    public MessageBoardArgs typeId(@Nullable Integer typeId) {
        if (typeId != null) {
            queryMap.put("EQ_typeId_Int", typeId);
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

    public MessageBoardArgs status(Collection<Integer> status) {
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
