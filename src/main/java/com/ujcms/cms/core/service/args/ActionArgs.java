package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import java.util.HashMap;
import java.util.Map;
import org.springframework.lang.Nullable;

/**
 * Action 查询参数
 *
 * @author Generator
 */
public class ActionArgs extends BaseQueryArgs {
    private ActionArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public ActionArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public static ActionArgs of() {
        return of(new HashMap<>(16));
    }

    public static ActionArgs of(Map<String, Object> queryMap) {
        return new ActionArgs(queryMap);
    }
}