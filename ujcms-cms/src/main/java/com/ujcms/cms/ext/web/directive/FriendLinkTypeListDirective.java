package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.FriendLinkType;
import com.ujcms.cms.ext.service.FriendLinkTypeService;
import com.ujcms.cms.ext.service.args.FriendLinkTypeArgs;
import com.ujcms.common.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;

import java.io.IOException;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.web.support.Directives.getLong;

/**
 * 友情链接类型列表 标签
 *
 * @author PONY
 */
public class FriendLinkTypeListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";

    public static void assemble(FriendLinkTypeArgs args, Map<String, ?> params, Long defaultSiteId) {
        Long siteId = getLong(params, SITE_ID);
        if (siteId == null) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);
        Directives.handleOrderBy(args.getQueryMap(), params);
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        FriendLinkTypeArgs args = FriendLinkTypeArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env));

        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        List<FriendLinkType> list = service.selectList(args, offset, limit);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private final FriendLinkTypeService service;

    public FriendLinkTypeListDirective(FriendLinkTypeService service) {
        this.service = service;
    }
}
