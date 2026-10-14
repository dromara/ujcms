package com.ujcms.cms.core.service.args;

import com.ujcms.common.query.BaseQueryArgs;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.time.OffsetDateTime;
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

    /**
     * 仅返回当前 "处于发布期" 的区块项：
     * {@code (start_date_ IS NULL OR start_date_ <= now)
     *   AND (end_date_   IS NULL OR end_date_   > now)}。
     * 用法：管理端不要调用此方法（让运营看到已过期条目）；前台 API / 模板渲染时调用。
     */
    public BlockItemArgs activeAt(@Nullable OffsetDateTime now) {
        if (now != null) {
            queryMap.put("LE_0start_startDate_DateTime", now);
            queryMap.put("IsNull_0start_startDate_DateTime", null);
            queryMap.put("GT_0end_endDate_DateTime", now);
            queryMap.put("IsNull_0end_endDate_DateTime", null);
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
