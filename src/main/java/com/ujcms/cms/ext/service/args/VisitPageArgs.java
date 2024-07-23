package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;

import java.util.HashMap;
import java.util.Map;

import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

/**
 * VisitPage 查询参数
 *
 * @author Generator
 */
public class VisitPageArgs extends BaseQueryArgs {
    private VisitPageArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public VisitPageArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public VisitPageArgs begin(@Nullable String begin) {
        if (StringUtils.isNoneBlank(begin)) {
            queryMap.put("GE_dateString", begin);
        }
        return this;
    }

    public VisitPageArgs end(@Nullable String end) {
        if (StringUtils.isNoneBlank(end)) {
            queryMap.put("LE_dateString", end);
        }
        return this;
    }

    public VisitPageArgs orderByDateString() {
        queryMap.put("OrderBy", "dateString");
        return this;
    }

    public static VisitPageArgs of() {
        return of(new HashMap<>(16));
    }

    public static VisitPageArgs of(Map<String, Object> queryMap) {
        return new VisitPageArgs(queryMap);
    }
}