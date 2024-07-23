package com.ujcms.cms.core.service.args;

import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.commons.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 模型查询参数
 *
 * @author PONY
 */
public class ModelArgs extends BaseQueryArgs {
    public ModelArgs scopeSiteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_1_siteId_Long", siteId);
            queryMap.put("EQ_1_scope_Short", String.valueOf(EntityConstants.SCOPE_GLOBAL));
        }
        return this;
    }

    public ModelArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public ModelArgs type(@Nullable String type) {
        if (type != null) {
            queryMap.put("EQ_type", type);
        }
        return this;
    }


    public static ModelArgs of() {
        return of(new HashMap<>(16));
    }

    public static ModelArgs of(Map<String, Object> queryMap) {
        return new ModelArgs(queryMap);
    }

    private ModelArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
