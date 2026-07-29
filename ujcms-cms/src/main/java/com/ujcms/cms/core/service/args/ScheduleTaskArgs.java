package com.ujcms.cms.core.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * ScheduleTask 查询参数
 *
 * @author PONY
 */
public class ScheduleTaskArgs extends BaseQueryArgs {
    private ScheduleTaskArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public static ScheduleTaskArgs of() {
        return of(new HashMap<>(16));
    }

    public static ScheduleTaskArgs of(Map<String, Object> queryMap) {
        return new ScheduleTaskArgs(queryMap);
    }

    public ScheduleTaskArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public ScheduleTaskArgs type(@Nullable String type) {
        if (type != null) {
            queryMap.put("EQ_type", type);
        }
        return this;
    }

    public ScheduleTaskArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }
}
