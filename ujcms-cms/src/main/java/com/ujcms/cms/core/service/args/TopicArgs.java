package com.ujcms.cms.core.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 专题查询参数
 *
 * @author PONY
 */
public class TopicArgs extends BaseQueryArgs {
    @Nullable
    private Long ancestorId;
    private boolean queryHasChildren = false;

    public TopicArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public TopicArgs ancestorId(@Nullable Long ancestorId) {
        if (ancestorId != null) {
            this.ancestorId = ancestorId;
        }
        return this;
    }

    public TopicArgs parentId(@Nullable Long parentId) {
        if (parentId != null) {
            queryMap.put("EQ_parentId_Long", parentId);
            queryHasChildren = true;
        }
        return this;
    }

    public TopicArgs parentIdAndSelf(@Nullable Long parentId) {
        if (parentId != null) {
            queryMap.put("EQ_0tree_parentId_Long", parentId);
            queryMap.put("EQ_0tree_id_Long", parentId);
        }
        return this;
    }

    public TopicArgs parentIdIsNull() {
        queryMap.put("IsNull_parentId", null);
        queryHasChildren = true;
        return this;
    }

    public static TopicArgs of() {
        return of(new HashMap<>(16));
    }

    public static TopicArgs of(Map<String, Object> queryMap) {
        return new TopicArgs(queryMap);
    }

    private TopicArgs(Map<String, Object> queryMap) {
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
