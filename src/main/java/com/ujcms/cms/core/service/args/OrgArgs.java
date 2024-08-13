package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 组织查询参数
 *
 * @author PONY
 */
public class OrgArgs extends BaseQueryArgs {
    @Nullable
    private Long ancestorId;
    private boolean queryHasChildren = false;

    public OrgArgs ancestorId(@Nullable Long ancestorId) {
        if (ancestorId != null) {
            this.ancestorId = ancestorId;
        }
        return this;
    }

    public OrgArgs parentId(@Nullable Long parentId) {
        if (parentId != null) {
            queryMap.put("EQ_parentId_Long", parentId);
            queryHasChildren = true;
        }
        return this;
    }

    public OrgArgs parentIdAndSelf(@Nullable Long parentId) {
        if (parentId != null) {
            queryMap.put("EQ_1_parentId_Long", parentId);
            queryMap.put("EQ_1_id_Long", parentId);
        }
        return this;
    }

    public OrgArgs parentIdIsNull() {
        queryMap.put("IsNull_parentId", null);
        queryHasChildren = true;
        return this;
    }

    public static OrgArgs of() {
        return of(new HashMap<>(16));
    }

    public static OrgArgs of(Map<String, Object> queryMap) {
        return new OrgArgs(queryMap);
    }

    private OrgArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    @Nullable
    public Long getAncestorId() {
        return ancestorId;
    }

    public boolean isQueryHasChildren() {
        return queryHasChildren;
    }

    public void setQueryHasChildren(boolean queryHasChildren) {
        this.queryHasChildren = queryHasChildren;
    }
}
