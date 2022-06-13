package com.ujcms.cms.core.service.args;

import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.util.query.BaseQueryArgs;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 区块查询参数
 *
 * @author PONY
 */
public class BlockArgs extends BaseQueryArgs {
    public BlockArgs scopeSiteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_1_siteId_Int", siteId);
            queryMap.put("EQ_1_scope_Short", String.valueOf(EntityConstants.SCOPE_GLOBAL));
        }
        return this;
    }

    public BlockArgs siteId(@Nullable Integer siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Int", siteId);
        }
        return this;
    }

    public static BlockArgs of() {
        return of(new HashMap<>(16));
    }

    public static BlockArgs of(Map<String, Object> queryMap) {
        return new BlockArgs(queryMap);
    }

    private BlockArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
