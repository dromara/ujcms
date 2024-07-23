package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * VisitLog 查询参数
 *
 * @author Generator
 */
public class VisitLogArgs extends BaseQueryArgs {
    private VisitLogArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public VisitLogArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public static VisitLogArgs of() {
        return of(new HashMap<>(16));
    }

    public static VisitLogArgs of(Map<String, Object> queryMap) {
        return new VisitLogArgs(queryMap);
    }
}