package com.ujcms.commons.query;

import java.util.Map;

/**
 * 基础查询参数类
 *
 * @author PONY
 */
public abstract class BaseQueryArgs {
    protected Map<String, Object> queryMap;

    protected BaseQueryArgs(Map<String, Object> queryMap) {
        this.queryMap = queryMap;
    }

    public Map<String, Object> getQueryMap() {
        return queryMap;
    }
}
