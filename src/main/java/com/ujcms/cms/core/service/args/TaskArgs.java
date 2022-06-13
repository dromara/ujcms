package com.ujcms.cms.core.service.args;

import com.ujcms.util.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 组织查询参数
 *
 * @author PONY
 */
public class TaskArgs extends BaseQueryArgs {
    public TaskArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Int", siteId);
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
