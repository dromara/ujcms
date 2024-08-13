package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * 站点查询参数
 *
 * @author PONY
 */
public class SiteArgs extends BaseQueryArgs {
    @Nullable
    private Map<String, String> customsQueryMap;
    @Nullable
    private Long fullOrgId;
    private boolean queryHasChildren = false;

    public SiteArgs customsQueryMap(Map<String, String> customsQueryMap) {
        this.customsQueryMap = customsQueryMap;
        return this;
    }

    public SiteArgs parentId(@Nullable Long parentId) {
        if (parentId != null) {
            queryMap.put("EQ_parentId_Long", parentId);
            queryHasChildren = true;
        }
        return this;
    }

    public SiteArgs ancestorId(@Nullable Long ancestorId) {
        if (ancestorId != null) {
            queryMap.put("EQ_descendant@SiteTree-ancestorId_Long", ancestorId);
        }
        return this;
    }

    public SiteArgs parentIdAndSelf(@Nullable Long parentId) {
        if (parentId != null) {
            queryMap.put("EQ_1_parentId_Long", parentId);
            queryMap.put("EQ_1_id_Long", parentId);
        }
        return this;
    }

    public SiteArgs parentIdIsNull() {
        queryMap.put("IsNull_parentId", null);
        queryHasChildren = true;
        return this;
    }

    public SiteArgs fullOrgId(@Nullable Long fullOrgId) {
        if (fullOrgId != null) {
            this.fullOrgId = fullOrgId;
        }
        return this;
    }

    public SiteArgs status(@Nullable Collection<Short> status) {
        if (CollectionUtils.isNotEmpty(status)) {
            queryMap.put("In_status_Short", status);
        }
        return this;
    }

    public static SiteArgs of() {
        return of(new HashMap<>(16));
    }

    public static SiteArgs of(Map<String, Object> queryMap) {
        return new SiteArgs(queryMap);
    }

    private SiteArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    @Nullable
    public Map<String, String> getCustomsQueryMap() {
        return customsQueryMap;
    }

    @Nullable
    public Long getFullOrgId() {
        return fullOrgId;
    }

    public boolean isQueryHasChildren() {
        return queryHasChildren;
    }

    public void setQueryHasChildren(boolean queryHasChildren) {
        this.queryHasChildren = queryHasChildren;
    }
}
