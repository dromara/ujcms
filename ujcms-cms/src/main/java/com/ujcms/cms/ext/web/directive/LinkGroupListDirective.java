package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.LinkGroup;
import com.ujcms.cms.ext.service.LinkGroupService;
import com.ujcms.cms.ext.service.args.LinkGroupArgs;
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

import static com.ujcms.cms.core.web.support.Directives.getLong;

/**
 * 链接组列表 标签
 *
 * @author PONY
 */
public class LinkGroupListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 展示位。字符串(String)。按主题清单 linkSlots 声明的展示位名称筛选；不传则获取全部链接组。
     */
    public static final String SLOT = "slot";

    public static void assemble(LinkGroupArgs args, Map<String, ?> params, Long defaultSiteId) {
        Long siteId = getLong(params, SITE_ID);
        if (siteId == null) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);
        String slot = Directives.getString(params, SLOT);
        if (StringUtils.isNotBlank(slot)) {
            args.slot(slot);
        }
        Directives.handleOrderBy(args.getQueryMap(), params);
    }

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        LinkGroupArgs args = LinkGroupArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env));

        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        List<LinkGroup> list = service.selectList(args, offset, limit);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private final LinkGroupService service;

    public LinkGroupListDirective(LinkGroupService service) {
        this.service = service;
    }
}
