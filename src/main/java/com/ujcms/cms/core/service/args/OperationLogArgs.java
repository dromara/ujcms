package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 操作日志查询参数
 *
 * @author PONY
 */
public class OperationLogArgs extends BaseQueryArgs {
    public static OperationLogArgs of() {
        return of(new HashMap<>(16));
    }

    public static OperationLogArgs of(Map<String, Object> queryMap) {
        return new OperationLogArgs(queryMap);
    }

    private OperationLogArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public OperationLogArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }
}
