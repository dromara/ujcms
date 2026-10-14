package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Link;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.service.LinkGroupService;
import com.ujcms.cms.ext.service.LinkService;
import com.ujcms.cms.ext.service.args.LinkArgs;
import com.ujcms.common.freemarker.Freemarkers;
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

import static com.ujcms.cms.core.web.support.Directives.getLong;

/**
 * 链接列表 标签
 *
 * @author PONY
 */
public class LinkListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 链接组ID。长整型(Long)。默认获取全部链接组。
     */
    public static final String GROUP_ID = "groupId";
    /**
     * 链接组别名。字符串(String)。用于按别名获取指定链接组的链接。
     */
    public static final String GROUP_ALIAS = "groupAlias";
    /**
     * 是否启用。字符串(String)。可选值：all(全部), false(禁用)。默认值：启用。
     */
    public static final String IS_ENABLED = "isEnabled";

    public static void assemble(LinkArgs args, Map<String, ?> params, Long defaultSiteId,
                                LinkGroupService groupService) {
        Long siteId = getLong(params, SITE_ID);
        if (siteId == null) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);
        Long groupId = getLong(params, GROUP_ID);
        String groupAlias = Directives.getString(params, GROUP_ALIAS);
        if (groupId == null && StringUtils.isNotBlank(groupAlias)) {
            groupId = Optional.ofNullable(groupService.selectByAlias(siteId, groupAlias))
                    .map(LinkGroup::getId).orElse(-1L);
        }
        args.groupId(groupId);
        Optional.ofNullable(Directives.getBooleanDefault(params, IS_ENABLED, true)).ifPresent(args::enabled);
        Directives.handleOrderBy(args.getQueryMap(), params);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        LinkArgs args = LinkArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env), groupService);

        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        List<Link> list = service.selectList(args, offset, limit);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private final LinkService service;
    private final LinkGroupService groupService;

    public LinkListDirective(LinkService service, LinkGroupService groupService) {
        this.service = service;
        this.groupService = groupService;
    }
}
