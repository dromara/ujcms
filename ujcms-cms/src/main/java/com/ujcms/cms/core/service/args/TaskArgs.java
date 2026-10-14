package com.ujcms.cms.core.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 组织查询参数
 *
 * @author PONY
 */
public class TaskArgs extends BaseQueryArgs {
    public TaskArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public TaskArgs type(@Nullable Short type) {
        if (type != null) {
            queryMap.put("EQ_type_Short", type);
        }
        return this;
    }

    public TaskArgs status(@Nullable Collection<Short> status) {
        if (status != null && !status.isEmpty()) {
            queryMap.put("In_status_Short", status);
        }
        return this;
    }

    public static TaskArgs of() {
        return of(new HashMap<>(16));
    }

    public static TaskArgs of(Map<String, Object> queryMap) {
        return new TaskArgs(queryMap);
    }

    private TaskArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

}
