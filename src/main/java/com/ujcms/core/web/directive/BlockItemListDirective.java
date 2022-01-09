package com.ujcms.core.web.directive;

import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.BlockItem;
import com.ujcms.core.service.BlockItemService;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.web.support.Directives;
import com.ofwise.util.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.lang3.StringUtils;

import java.io.IOException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

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
     * 区块ID。字符串(String)
     */
    public static final String BLOCK_ID = "blockId";
    /**
     * 区块别名。字符串(String)
     */
    public static final String BLOCK_ALIAS = "blockAlias";
    /**
     * 是否启用。字符串(String)。可选值：all(全部), false(禁用)。默认值：启用。
     */
    public static final String ENABLED = "enabled";

    public static List<BlockItem> query(Map<String, ?> params, Integer defaultSiteId, BlockItemService service) {
        Map<String, Object> queryMap = new HashMap<>(16);

        Integer siteId = Directives.getInteger(params, SITE_ID);
        queryMap.put("EQ_siteId_Int", siteId != null ? siteId : defaultSiteId);

        Integer blockId = Directives.getInteger(params, BLOCK_ID);
        String blockAlias = Directives.getString(params, BLOCK_ALIAS);
        if (blockId != null) {
            queryMap.put("EQ_blockId_Int", blockId);
        } else if (StringUtils.isNotBlank(blockAlias)) {
            queryMap.put("EQ_block-alias", blockAlias);
        } else {
            throw new IllegalArgumentException("Params blockId or blockAlias is required.");
        }

        Boolean enabled = Directives.getDefaultBoolean(params, ENABLED, true);
        if (enabled != null) {
            queryMap.put("EQ_enabled_Boolean", enabled);
        }

        Directives.handleOrderBy(queryMap, params, "order");
        Integer offset = Directives.findOffset(params);
        Integer limit = Directives.findLimit(params);

        return service.selectList(queryMap, offset, limit);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.checkLoopVars(loopVars);
        Freemarkers.checkBody(body);

        List<Article> list = query(params, Frontends.getSiteId(env), service);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }

    private BlockItemService service;

    public BlockItemListDirective(BlockItemService service) {
        this.service = service;
    }
}
