package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * PerformanceType 查询参数
 *
 * @author Generator
 */
public class PerformanceTypeArgs extends BaseQueryArgs {
    public PerformanceTypeArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    private PerformanceTypeArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public static PerformanceTypeArgs of() {
        return of(new HashMap<>(16));
    }

    public static PerformanceTypeArgs of(Map<String, Object> queryMap) {
        return new PerformanceTypeArgs(queryMap);
    }
}