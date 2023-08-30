package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Example 查询参数
 *
 * @author Generator
 */
public class ExampleArgs extends BaseQueryArgs {
    private ExampleArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public ExampleArgs name(@Nullable String name) {
        if (StringUtils.isNoneBlank(name)) {
            queryMap.put("Contains_name", name);
        }
        return this;
    }

    public static ExampleArgs of() {
        return of(new HashMap<>(16));
    }

    public static ExampleArgs of(Map<String, Object> queryMap) {
        return new ExampleArgs(queryMap);
    }
}