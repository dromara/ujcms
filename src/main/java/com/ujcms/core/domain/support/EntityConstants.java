package com.ujcms.core.domain.support;

import com.ujcms.core.domain.Site;
import com.ofwise.util.query.QueryUtils;

import java.util.Map;

/**
 * 实体类常量
 *
 * @author PONY
 */
public class EntityConstants {
    /**
     * 共享范围：全局共享
     */
    public static final short SCOPE_GLOBAL = 2;
    /**
     * 共享范围：本站私有
     */
    public static final short SCOPE_PRIVATE = 0;

    /**
     * 获取 本站或全局共享 的 QueryMap
     */
    public static Map<String, Object> scopeSiteQuery(Site site, String queryString) {
        Map<String, Object> queryMap = QueryUtils.getQueryMap(queryString);
        queryMap.put("EQ_1_siteId_Int", String.valueOf(site.getId()));
        queryMap.put("EQ_1_scope_Short", String.valueOf(EntityConstants.SCOPE_GLOBAL));
        return queryMap;
    }
}
