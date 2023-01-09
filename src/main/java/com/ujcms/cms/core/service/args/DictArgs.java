package com.ujcms.cms.core.service.args;

import com.ujcms.util.query.BaseQueryArgs;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 字典查询参数
 *
 * @author PONY
 */
public class DictArgs extends BaseQueryArgs {
    public DictArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Int", siteId);
        }
        return this;
    }

    public DictArgs typeId(@Nullable Integer typeId) {
        if (typeId != null) {
            queryMap.put("EQ_typeId", typeId);
        }
        return this;
    }

    public DictArgs typeAlias(@Nullable String typeAlias) {
        if (StringUtils.isNotBlank(typeAlias)) {
            queryMap.put("EQ_type@dictType-alias", typeAlias);
        }
        return this;
    }

    public DictArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }

    public static DictArgs of() {
        return of(new HashMap<>(16));
    }

    public static DictArgs of(Map<String, Object> queryMap) {
        return new DictArgs(queryMap);
    }

    private DictArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
