package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * FormType 查询参数
 *
 * @author Generator
 */
public class FormTypeArgs extends BaseQueryArgs {
    private FormTypeArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public FormTypeArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public static FormTypeArgs of() {
        return of(new HashMap<>(16));
    }

    public static FormTypeArgs of(Map<String, Object> queryMap) {
        return new FormTypeArgs(queryMap);
    }
}