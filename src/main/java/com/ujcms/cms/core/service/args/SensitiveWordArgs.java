package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * SensitiveWord 查询参数
 *
 * @author Generator
 */
public class SensitiveWordArgs extends BaseQueryArgs {
    private SensitiveWordArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public SensitiveWordArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }

    public static SensitiveWordArgs of() {
        return of(new HashMap<>(16));
    }

    public static SensitiveWordArgs of(Map<String, Object> queryMap) {
        return new SensitiveWordArgs(queryMap);
    }
}