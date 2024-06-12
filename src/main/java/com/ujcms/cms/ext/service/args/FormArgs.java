package com.ujcms.cms.ext.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.lang.Nullable;

import java.util.Collection;
import java.util.HashMap;
import java.util.Map;

/**
 * Form 查询参数
 *
 * @author Generator
 */
public class FormArgs extends BaseQueryArgs {
    private FormArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    @Nullable
    private Collection<Long> orgIds;
    @Nullable
    private Collection<Long> orgRoleIds;
    @Nullable
    private Collection<Long> orgPermIds;

    public FormArgs orgIds(@Nullable Collection<Long> orgIds) {
        if (orgIds != null) {
            this.orgIds = orgIds;
        }
        return this;
    }

    public FormArgs orgPermission(Collection<Long> orgRoleIds, Collection<Long> orgPermIds) {
        this.orgRoleIds = orgRoleIds;
        this.orgPermIds = orgPermIds;
        return this;
    }

    public FormArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public FormArgs orgId(@Nullable Long orgId) {
        if (orgId != null) {
            queryMap.put("EQ_orgId_Long", orgId);
        }
        return this;
    }

    public FormArgs userId(@Nullable Long userId) {
        if (userId != null) {
            queryMap.put("EQ_userId_Long", userId);
        }
        return this;
    }

    public FormArgs typeId(@Nullable Long typeId) {
        if (typeId != null) {
            queryMap.put("EQ_typeId_Long", typeId);
        }
        return this;
    }

    public FormArgs status(@Nullable Collection<Short> status) {
        if (CollectionUtils.isNotEmpty(status)) {
            queryMap.put("In_status_Short", status);
        }
        return this;
    }

    @Nullable
    public Collection<Long> getOrgIds() {
        return orgIds;
    }

    @Nullable
    public Collection<Long> getOrgRoleIds() {
        return orgRoleIds;
    }

    @Nullable
    public Collection<Long> getOrgPermIds() {
        return orgPermIds;
    }

    public static FormArgs of() {
        return of(new HashMap<>(16));
    }

    public static FormArgs of(Map<String, Object> queryMap) {
        return new FormArgs(queryMap);
    }
}