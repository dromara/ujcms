package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * VisitStat 查询参数
 *
 * @author Generator
 */
public class VisitStatArgs extends BaseQueryArgs {
    private VisitStatArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public VisitStatArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public VisitStatArgs begin(@Nullable String begin) {
        if (StringUtils.isNoneBlank(begin)) {
            queryMap.put("GE_dateString", begin);
        }
        return this;
    }

    public VisitStatArgs end(@Nullable String end) {
        if (StringUtils.isNoneBlank(end)) {
            queryMap.put("LE_dateString", end);
        }
        return this;
    }

    public VisitStatArgs type(@Nullable Short type) {
        if (type != null) {
            queryMap.put("EQ_type_Short", type);
        }
        return this;
    }

    public VisitStatArgs orderByDateString() {
        queryMap.put("OrderBy", "dateString");
        return this;
    }

    public static VisitStatArgs of() {
        return of(new HashMap<>(16));
    }

    public static VisitStatArgs of(Map<String, Object> queryMap) {
        return new VisitStatArgs(queryMap);
    }
}