package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.args.BlockItemArgs;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.web.support.Directives.getBoolean;

/**
 * 区块列表 标签
 *
 * @author PONY
 */
public class BlockItemListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 区块别名。字符串(String)
     */
    public static final String BLOCK = "block";
    /**
     * 区块ID。字符串(String)
     */
    public static final String BLOCK_ID = "blockId";
    /**
     * 是否启用。字符串(String)。可选值：all(全部), false(禁用)。默认值：启用。
     */
    public static final String IS_ENABLED = "isEnabled";
    /**
     * 是否获取所有站点文章。布尔型(Boolean)。默认：false。
     */
    public static final String IS_ALL_SITE = "isAllSite";

    public static List<BlockItem> query(Map<String, ?> params, Long defaultSiteId, BlockItemService service) {
        BlockItemArgs args = BlockItemArgs.of();
        Long siteId = Directives.getLong(params, SITE_ID);

        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);

        Long blockId = Directives.getLong(params, BLOCK_ID);
        String blockAlias = Directives.getString(params, BLOCK);
        if (blockId != null) {
            args.blockId(blockId);
        } else if (StringUtils.isNotBlank(blockAlias)) {
            args.blockAlias(blockAlias);
        } else {
            throw new IllegalArgumentException("Params blockId or block is required.");
        }

        Optional.ofNullable(Directives.getBooleanDefault(params, IS_ENABLED, true)).ifPresent(args::enabled);

        Directives.handleOrderBy(args.getQueryMap(), params, "order");
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);

        return service.selectList(args, offset, limit);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        List<Article> list = query(params, Frontends.getSiteId(env), service);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }

    private final BlockItemService service;

    public BlockItemListDirective(BlockItemService service) {
        this.service = service;
    }
}
