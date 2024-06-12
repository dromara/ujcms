package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * VisitTrend 查询参数
 *
 * @author Generator
 */
public class VisitTrendArgs extends BaseQueryArgs {
    private VisitTrendArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public VisitTrendArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public VisitTrendArgs period(@Nullable Short period) {
        if (period != null) {
            queryMap.put("EQ_period_Short", period);
        }
        return this;
    }

    public VisitTrendArgs begin(@Nullable String begin) {
        if (StringUtils.isNoneBlank(begin)) {
            queryMap.put("GE_dateString", begin);
        }
        return this;
    }

    public VisitTrendArgs end(@Nullable String end) {
        if (StringUtils.isNoneBlank(end)) {
            queryMap.put("LE_dateString", end);
        }
        return this;
    }

    public VisitTrendArgs orderByDateString() {
        queryMap.put("OrderBy", "dateString");
        return this;
    }

    public static VisitTrendArgs of() {
        return of(new HashMap<>(16));
    }

    public static VisitTrendArgs of(Map<String, Object> queryMap) {
        return new VisitTrendArgs(queryMap);
    }
}