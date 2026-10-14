package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Block;
import com.ujcms.cms.core.service.BlockService;
import com.ujcms.cms.core.service.args.BlockArgs;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.common.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 区块列表 标签
 *
 * <p>按分类 (category) 拉取一组结构相同的 Block，
 * 用于模板中 "for each block in 'friend-links' category" 这类场景。
 *
 * @author PONY
 */
public class BlockListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型（Integer）
     */
    public static final String SITE_ID = "siteId";
    /**
     * 区块分类。字符串（String）
     */
    public static final String CATEGORY = "category";

    public static List<Block> query(Map<String, ?> params, Long defaultSiteId, BlockService service) {
        Long siteId = Directives.getLong(params, SITE_ID, defaultSiteId);
        String category = Directives.getString(params, CATEGORY);

        BlockArgs args = BlockArgs.of()
                .scopeSiteId(siteId)
                .category(category);
        Directives.handleOrderBy(args.getQueryMap(), params, "order");
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(args, offset, limit);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        List<Block> list = query(params, Frontends.getSiteId(env), service);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }

    private final BlockService service;

    public BlockListDirective(BlockService service) {
        this.service = service;
    }
}
