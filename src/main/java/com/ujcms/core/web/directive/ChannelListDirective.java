package com.ujcms.core.web.directive;

import com.ujcms.core.domain.Channel;
import com.ujcms.core.service.ChannelService;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.web.support.Directives;
import com.ofwise.util.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

/**
 * 栏目列表 标签
 *
 * @author PONY
 */
public class ChannelListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型（Integer）。
     */
    private static final String SITE_ID = "siteId";
    /**
     * 上级栏目。整型（Integer）。
     */
    private static final String PARENT_ID = "parentId";
    /**
     * 是否导航。布尔型（Boolean）。
     */
    private static final String IS_NAV = "isNav";
    /**
     * 是否包含子栏目。布尔型（Boolean）。
     */
    private static final String IS_INCLUDE_CHILDREN = "isIncludeChildren";

    public static void assemble(Map<String, Object> queryMap, Map<String, ?> params, Integer defaultSiteId) {
        Integer siteId = Directives.getInteger(params, SITE_ID, defaultSiteId);
        queryMap.put("EQ_siteId_Integer", siteId.toString());

        Integer parentId = Directives.getInteger(params, PARENT_ID);
        if (parentId != null) {
            Boolean isIncludeChildren = Directives.getBoolean(params, IS_INCLUDE_CHILDREN);
            if (isIncludeChildren != null && isIncludeChildren) {
                queryMap.put("EQ_descendant@ChannelTree-ancestorId_Integer", parentId.toString());
            } else {
                queryMap.put("EQ_parentId_Integer", parentId.toString());
            }
        } else {
            queryMap.put("IsNull_parentId", null);
        }

        Boolean isNav = Directives.getBoolean(params, IS_NAV);
        if (isNav != null) {
            queryMap.put("EQ_nav_Boolean", isNav);
        }

        Directives.handleOrderBy(queryMap, params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.checkLoopVars(loopVars);
        Freemarkers.checkBody(body);
        Integer defaultSiteId = Frontends.getSiteId(env);

        Map<String, Object> queryMap = Directives.getQueryMap(params);
        assemble(queryMap, params, defaultSiteId);
        Map<String, String> customsQueryMap = Directives.getCustomsQueryMap(params);

        Integer offset = Directives.findOffset(params);
        Integer limit = Directives.findLimit(params);

        List<Channel> list = channelService.selectList(queryMap, customsQueryMap, offset, limit);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private ChannelService channelService;

    public ChannelListDirective(ChannelService channelService) {
        this.channelService = channelService;
    }
}
