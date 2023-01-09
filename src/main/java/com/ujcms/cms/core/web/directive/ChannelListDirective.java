package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.util.freemarker.Freemarkers;
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

/**
 * 栏目列表 标签
 *
 * @author PONY
 */
public class ChannelListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型（Integer）
     */
    private static final String SITE_ID = "siteId";
    /**
     * 上级栏目别名。字符串（String）
     */
    private static final String PARENT_ID = "parentId";
    /**
     * 上级栏目ID。整型（Integer）
     */
    private static final String PARENT = "parentId";
    /**
     * 是否导航。布尔型（Boolean）
     */
    private static final String IS_NAV = "isNav";
    /**
     * 是否包含子栏目。布尔型（Boolean）。默认：false
     */
    private static final String IS_INCLUDE_CHILDREN = "isIncludeChildren";

    public static void assemble(ChannelArgs args, Map<String, ?> params, Integer defaultSiteId,
                                ChannelService channelService) {
        Integer siteId = Directives.getInteger(params, SITE_ID, defaultSiteId);
        args.siteId(siteId);
        boolean isIncludeChildren = Directives.getBoolean(params, IS_INCLUDE_CHILDREN, false);
        Integer parentId = Directives.getInteger(params, PARENT_ID);
        if (parentId == null) {
            String parentAlias = Directives.getString(params, PARENT);
            if (StringUtils.isNotBlank(parentAlias)) {
                Channel parent = channelService.findBySiteIdAndAlias(siteId, parentAlias);
                if (parent != null) {
                    parentId = parent.getId();
                }
            }
        }
        if (parentId != null) {
            if (isIncludeChildren) {
                args.subParentId(parentId);
            } else {
                args.parentId(parentId);
            }
        } else if (!isIncludeChildren) {
            args.parentIdIsNull();
        }
        Optional.ofNullable(Directives.getBoolean(params, IS_NAV)).ifPresent(args::isNav);
        Directives.handleOrderBy(args.getQueryMap(), params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);
        Integer defaultSiteId = Frontends.getSiteId(env);

        ChannelArgs args = ChannelArgs.of(Directives.getQueryMap(params));
        assemble(args, params, defaultSiteId, channelService);
        args.customsQueryMap(Directives.getCustomsQueryMap(params));

        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);

        List<Channel> list = channelService.selectList(args, offset, limit);
        list.forEach(channel -> {
            channel.getPaths().forEach(channelService::fetchFirstData);
            channel.getChildren().forEach(channelService::fetchFirstData);
        });
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }

    private final ChannelService channelService;

    public ChannelListDirective(ChannelService channelService) {
        this.channelService = channelService;
    }
}
