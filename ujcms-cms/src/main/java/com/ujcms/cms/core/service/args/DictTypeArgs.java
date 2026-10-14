package com.ujcms.cms.core.service.args;

import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.common.query.BaseQueryArgs;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 字典类型查询参数
 *
 * @author PONY
 */
public class DictTypeArgs extends BaseQueryArgs {
    private boolean queryEntryCount = false;

    /**
     * 同时查询各类型的条目数（{@code DictType.entryCount}）。仅后台类型列表需要，其余调用方不付子查询的代价
     */
    public DictTypeArgs queryEntryCount() {
        queryEntryCount = true;
        return this;
    }

    public boolean isQueryEntryCount() {
        return queryEntryCount;
    }

    public DictTypeArgs scopeSiteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_0scope_siteId_Long", siteId);
            queryMap.put("EQ_0scope_scope_Short", String.valueOf(EntityConstants.SCOPE_GLOBAL));
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
