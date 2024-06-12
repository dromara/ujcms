package com.ujcms.cms.core.service.args;

import com.ujcms.commons.query.BaseQueryArgs;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 区块项查询参数
 *
 * @author PONY
 */
public class BlockItemArgs extends BaseQueryArgs {
    public BlockItemArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public BlockItemArgs blockId(@Nullable Long blockId) {
        if (blockId != null) {
            queryMap.put("EQ_blockId_Long", blockId);
        }
        return this;
    }

    public BlockItemArgs blockAlias(@Nullable String blockAlias) {
        if (StringUtils.isNotBlank(blockAlias)) {
            queryMap.put("EQ_block-alias", blockAlias);
        }
        return this;
    }

    public BlockItemArgs enabled(@Nullable Boolean enabled) {
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }
        return this;
    }

    public static BlockItemArgs of() {
        return of(new HashMap<>(16));
    }

    public static BlockItemArgs of(Map<String, Object> queryMap) {
        return new BlockItemArgs(queryMap);
    }

    private BlockItemArgs(Map<String, Object> queryMap) {
        super(queryMap);
    }
}
