package com.ujcms.cms.ext.service.args;

import java.util.HashMap;
import java.util.List;
import java.util.Map;

import org.apache.commons.collections4.CollectionUtils;
import org.jspecify.annotations.Nullable;

import com.ujcms.common.query.BaseQueryArgs;

/**
 * FormType 查询参数
 *
 * @author Generator
 */
public class FormTypeArgs extends BaseQueryArgs {
    private boolean queryFormCount = false;

    /**
     * 同时查询各表单类型的表单数（{@code FormType.formCount}）。仅后台表单类型列表需要，其余调用方不付子查询的代价
     */
    public FormTypeArgs queryFormCount() {
        queryFormCount = true;
        return this;
    }

    public boolean isQueryFormCount() {
        return queryFormCount;
    }

    private FormTypeArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }

    public FormTypeArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public FormTypeArgs mode(@Nullable List<Short> mode) {
        if (CollectionUtils.isNotEmpty(mode)) {
            queryMap.put("In_mode_Short", mode);
        }
        return this;
    }

    public FormTypeArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }

    public FormTypeArgs viewable(@Nullable Boolean viewable) {
        if (viewable != null) {
            queryMap.put("EQ_viewable_Boolean", viewable);
        }
        return this;
    }

    public static FormTypeArgs of() {
        return of(new HashMap<>(16));
    }

    public static FormTypeArgs of(Map<String, Object> queryMap) {
        return new FormTypeArgs(queryMap);
    }
}