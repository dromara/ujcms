package com.ujcms.cms.core.service.args;

import com.ujcms.cms.core.domain.support.EntityConstants;
import com.ujcms.common.query.BaseQueryArgs;
import org.apache.commons.lang3.StringUtils;
import org.jspecify.annotations.Nullable;

import java.util.HashMap;
import java.util.Map;

/**
 * 区块查询参数
 *
 * @author PONY
 */
public class BlockArgs extends BaseQueryArgs {
    @Nullable
    private Long itemCountSiteId;

    /**
     * 同时查询各区块在指定站点的区块项数（{@code Block.itemCount}）。仅后台区块列表需要，其余调用方不付子查询的代价。
     * 须给出站点：全局区块的区块项分属各站，左侧列表显示的是本站的数量
     */
    public BlockArgs queryItemCount(Long siteId) {
        itemCountSiteId = siteId;
        return this;
    }

    @Nullable
    public Long getItemCountSiteId() {
        return itemCountSiteId;
    }

    public BlockArgs scopeSiteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_0scope_siteId_Long", siteId);
            queryMap.put("EQ_0scope_scope_Short", String.valueOf(EntityConstants.SCOPE_GLOBAL));
        }
        return this;
    }

    public BlockArgs siteId(@Nullable Long siteId) {
        if (siteId != null) {
            queryMap.put("EQ_siteId_Long", siteId);
        }
        return this;
    }

    public BlockArgs category(@Nullable String category) {
        if (StringUtils.isNotBlank(category)) {
            queryMap.put("EQ_category", category);
        }
        return this;
    }

    public BlockArgs alias(@Nullable String alias) {
        if (StringUtils.isNotBlank(alias)) {
            queryMap.put("EQ_alias", alias);
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
