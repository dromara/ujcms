package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * Tag 查询参数
 *
 * @author Generator
 */
public class TagArgs extends BaseQueryArgs {
    private TagArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public TagArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public TagArgs name(@Nullable String name) {
        if (StringUtils.isNoneBlank(name)) {
            queryMap.put("Contains_name", name);
        }
        return this;
    }

    public TagArgs isReferred(@Nullable Boolean isReferred) {
        if (isReferred != null) {
            if (isReferred) {
                queryMap.put("GT_refers_Int", 0);
            } else {
                queryMap.put("LE_refers_Int", 0);
            }
        }
        return this;
    }

    public static TagArgs of() {
        return of(new HashMap<>(16));
    }

    public static TagArgs of(Map<String, Object> queryMap) {
        return new TagArgs(queryMap);
    }
}