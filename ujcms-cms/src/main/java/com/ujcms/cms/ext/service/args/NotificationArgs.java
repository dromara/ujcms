package com.ujcms.cms.ext.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 通知 查询参数
 *
 * @author PONY
 */
public class NotificationArgs extends BaseQueryArgs {
    public NotificationArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public NotificationArgs type(@Nullable Short type) {
        if (type != null) {
            queryMap.put("EQ_type_Short", type);
        }
        return this;
    }

    public NotificationArgs isRead(@Nullable Boolean isRead) {
        if (isRead != null) {
            queryMap.put("EQ_read_Boolean", isRead);
        }
        return this;
    }

    public static NotificationArgs of() {
        return of(new HashMap<>(16));
    }

    public static NotificationArgs of(Map<String, Object> queryMap) {
        return new NotificationArgs(queryMap);
    }

    private NotificationArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
