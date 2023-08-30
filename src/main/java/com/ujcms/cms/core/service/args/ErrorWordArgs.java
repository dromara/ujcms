package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * ErrorWord 查询参数
 *
 * @author Generator
 */
public class ErrorWordArgs extends BaseQueryArgs {
    private ErrorWordArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public ErrorWordArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }

    public static ErrorWordArgs of() {
        return of(new HashMap<>(16));
    }

    public static ErrorWordArgs of(Map<String, Object> queryMap) {
        return new ErrorWordArgs(queryMap);
    }
}