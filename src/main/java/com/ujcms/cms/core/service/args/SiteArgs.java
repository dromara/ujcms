package com.ujcms.cms.core.service.args;

import com.ujcms.util.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

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
    private Integer parentId;
    @Nullable
    private Integer fullOrgId;

    public SiteArgs customsQueryMap(Map<String, String> customsQueryMap) {
        this.customsQueryMap = customsQueryMap;
        return this;
    }

    public SiteArgs parentId(@Nullable Integer parentId) {
        if (parentId != null) {
            this.parentId = parentId;
        }
        return this;
    }

    public SiteArgs fullOrgId(@Nullable Integer fullOrgId) {
        if (fullOrgId != null) {
            this.fullOrgId = fullOrgId;
            queryMap.put("Distinct", true);
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
    public Integer getParentId() {
        return parentId;
    }

    @Nullable
    public Integer getFullOrgId() {
        return fullOrgId;
    }
}
