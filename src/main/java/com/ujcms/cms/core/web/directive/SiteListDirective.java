package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.web.support.Directives.getShorts;

/**
 * 站点列表 标签
 *
 * @author PONY
 */
public class SiteListDirective implements TemplateDirectiveModel {
    /**
     * 上级站点ID。整型（Integer）
     */
    private static final String PARENT_ID = "parentId";
    /**
     * 是否包含子站点。布尔型（Boolean）。默认：false
     */
    private static final String IS_INCLUDE_CHILDREN = "isIncludeChildren";
    /**
     * 状态。短整形（Short）
     */
    private static final String STATUS = "status";

    public static void assemble(SiteArgs args, Map<String, ?> params) {
        boolean isIncludeChildren = Directives.getBoolean(params, IS_INCLUDE_CHILDREN, false);
        Long parentId = Directives.getLong(params, PARENT_ID);
        if (isIncludeChildren) {
            args.ancestorId(parentId);
        } else {
            if (parentId != null) {
                args.parentId(parentId);
            } else {
                args.parentIdIsNull();
            }
        }

        Collection<Short> status = getShorts(params, STATUS);
        if (status == null) {
            status = Collections.singletonList(Site.STATUS_NORMAL);
        }
        args.status(status);

        Directives.handleOrderBy(args.getQueryMap(), params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        SiteArgs args = SiteArgs.of(Directives.getQueryMap(params));
        assemble(args, params);
        args.customsQueryMap(Directives.getCustomsQueryMap(params));
        List<Site> list = selectList(siteService, args, params);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }

    public static List<Site> selectList(SiteService siteService, SiteArgs args, Map<String, String> params) {
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return siteService.selectList(args, offset, limit);
    }

    private final SiteService siteService;

    public SiteListDirective(SiteService siteService) {
        this.siteService = siteService;
    }
}
