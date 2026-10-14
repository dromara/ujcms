package com.ujcms.cms.core.service.args;

import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.common.query.BaseQueryArgs;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 字典查询参数
 *
 * @author PONY
 */
public class DictArgs extends BaseQueryArgs {
    public DictArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    /**
     * 站点可见范围：所属字典类型为全局共享，或属于当前站点（本站 ∪ 全局）。
     * <p>
     * 同组（{@code 1_}）不带子组的两个条件落入各自的子组，组内 OR、组间 AND
     * （{@code SeqMapper.Select_All}）。值直接放 {@link Long}/{@link Short} 对象——
     * 不加 {@code _Long}/{@code _Short} 后缀也不 {@code String.valueOf}：字符串 {@code '2'}
     * 对 SMALLINT 在 PostgreSQL 上不可比较。
     */
    public DictArgs scopeSiteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_0scope_type@dictType-siteId", siteId);
            queryMap.put("EQ_0scope_type@dictType-scope", EntityConstants.SCOPE_GLOBAL);
        }
        return this;
    }


    public DictArgs typeId(@Nullable Long typeId) {
        if (typeId != null) {
            queryMap.put("EQ_typeId_Long", typeId);
        }
        return this;
    }
    public DictArgs inTypeIds(@Nullable Collection<Long> typeIds) {
        if (CollectionUtils.isNotEmpty(typeIds)) {
            queryMap.put("In_typeId_Long", typeIds);
        }
        return this;
    }

    public DictArgs typeAlias(@Nullable String typeAlias) {
        if (StringUtils.isNotBlank(typeAlias)) {
            queryMap.put("EQ_type@dictType-alias", typeAlias);
        }
        return this;
    }
    public DictArgs inTypeAlias(@Nullable Collection<String> typeAlias) {
        if (CollectionUtils.isNotEmpty(typeAlias)) {
            queryMap.put("In_type@dictType-alias", typeAlias);
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
