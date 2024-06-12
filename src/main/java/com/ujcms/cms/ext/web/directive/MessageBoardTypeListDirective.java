package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.MessageBoardType;
import com.ujcms.cms.ext.service.MessageBoardTypeService;
import com.ujcms.cms.ext.service.args.MessageBoardTypeArgs;
import com.ujcms.commons.freemarker.Freemarkers;
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
 * 留言类型列表 标签
 *
 * @author PONY
 */
public class MessageBoardTypeListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";

    public static void assemble(MessageBoardTypeArgs args, Map<String, ?> params, Long defaultSiteId) {
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

        MessageBoardTypeArgs args = MessageBoardTypeArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env));

        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        List<MessageBoardType> list = service.selectList(args, offset, limit);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }


    private final MessageBoardTypeService service;

    public MessageBoardTypeListDirective(MessageBoardTypeService service) {
        this.service = service;
    }
}
