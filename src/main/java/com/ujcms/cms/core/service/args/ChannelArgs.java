package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 栏目查询参数
 *
 * @author PONY
 */
public class ChannelArgs extends BaseQueryArgs {
    @Nullable
    private Map<String, String> customsQueryMap;
    @Nullable
    private Collection<Integer> articleRoleIds;
    private boolean onlyParent = false;
    private boolean queryHasChildren = false;

    public ChannelArgs customsQueryMap(Map<String, String> customsQueryMap) {
        this.customsQueryMap = customsQueryMap;
        return this;
    }

    public ChannelArgs articleRoleIds(@Nullable Collection<Integer> articleRoleIds) {
        if (articleRoleIds != null) {
            this.articleRoleIds = articleRoleIds;
        }
        return this;
    }

    public ChannelArgs siteAncestorId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_site@SiteTree@descendant-ancestorId_Int", siteId);
        }
        return this;
    }

    public ChannelArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Int", siteId);
        }
        return this;
    }

    public ChannelArgs ancestorId(@Nullable Integer ancestorId) {
        if (ancestorId != null) {
            queryMap.put("EQ_descendant@ChannelTree-ancestorId_Int", ancestorId);
        }
        return this;
    }

    public ChannelArgs parentId(@Nullable Integer parentId) {
        if (parentId != null) {
            queryMap.put("EQ_parentId_Int", parentId);
            queryHasChildren = true;
        }
        return this;
    }

    public ChannelArgs parentIdIsNull() {
        queryMap.put("IsNull_parentId", null);
        queryHasChildren = true;
        return this;
    }

    public ChannelArgs inAliases(Collection<String> aliases) {
        if (CollectionUtils.isNotEmpty(aliases)) {
            queryMap.put("In_alias", aliases);
        }
        return this;
    }

    public ChannelArgs isNav(@Nullable Boolean isNav) {
        if (isNav != null) {
            queryMap.put("EQ_nav_Boolean", isNav);
        }
        return this;
    }

    public static ChannelArgs of() {
        return of(new HashMap<>(16));
    }

    public static ChannelArgs of(Map<String, Object> queryMap) {
        return new ChannelArgs(queryMap);
    }

    private ChannelArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    @Nullable
    public Map<String, String> getCustomsQueryMap() {
        return customsQueryMap;
    }

    public boolean isOnlyParent() {
        return onlyParent;
    }

    public void setOnlyParent(boolean onlyParent) {
        this.onlyParent = onlyParent;
    }

    public boolean isQueryHasChildren() {
        return queryHasChildren;
    }

    public void setQueryHasChildren(boolean queryHasChildren) {
        this.queryHasChildren = queryHasChildren;
    }

    @Nullable
    public Collection<Integer> getArticleRoleIds() {
        return articleRoleIds;
    }
}
