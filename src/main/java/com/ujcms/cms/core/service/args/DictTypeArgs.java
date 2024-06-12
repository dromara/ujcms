package com.ujcms.cms.core.service.args;

import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 字典类型查询参数
 *
 * @author PONY
 */
public class DictTypeArgs extends BaseQueryArgs {
    public DictTypeArgs scopeSiteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_1_siteId_Long", siteId);
            queryMap.put("EQ_1_scope_Short", String.valueOf(EntityConstants.SCOPE_GLOBAL));
        }
        return this;
    }

    public DictTypeArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public static DictTypeArgs of() {
        return of(new HashMap<>(16));
    }

    public static DictTypeArgs of(Map<String, Object> queryMap) {
        return new DictTypeArgs(queryMap);
    }

    private DictTypeArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
