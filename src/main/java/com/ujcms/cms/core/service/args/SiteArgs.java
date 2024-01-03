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
    private Integer fullOrgId;
    private boolean isQueryHasChildren = false;

    public SiteArgs customsQueryMap(Map<String, String> customsQueryMap) {
        this.customsQueryMap = customsQueryMap;
        return this;
    }

    public SiteArgs parentId(@Nullable Integer parentId) {
        if (parentId != null) {
            queryMap.put("EQ_parentId_Int", parentId);
            isQueryHasChildren = true;
        }
        return this;
    }

    public SiteArgs ancestorId(@Nullable Integer ancestorId) {
        if (ancestorId != null) {
            queryMap.put("EQ_descendant@SiteTree-ancestorId_Int", ancestorId);
        }
        return this;
    }

    public SiteArgs parentIdIsNull() {
        queryMap.put("IsNull_parentId", null);
        isQueryHasChildren = true;
        return this;
    }

    public SiteArgs fullOrgId(@Nullable Integer fullOrgId) {
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
    public Integer getFullOrgId() {
        return fullOrgId;
    }

    public boolean isQueryHasChildren() {
        return isQueryHasChildren;
    }

    public void setQueryHasChildren(boolean queryHasChildren) {
        isQueryHasChildren = queryHasChildren;
    }
}
