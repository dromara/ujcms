package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.MessageBoard;
import com.ujcms.cms.core.service.MessageBoardService;
import com.ujcms.cms.core.service.args.MessageBoardArgs;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.util.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.web.support.Directives.getBoolean;
import static com.ujcms.cms.core.web.support.Directives.getInteger;
import static com.ujcms.util.db.MyBatis.springPage;

/**
 * 留言板列表 标签
 *
 * @author PONY
 */
public class MessageBoardListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 类型ID。整型(Integer)。
     */
    public static final String TYPE_ID = "typeId";
    /**
     * 是否推荐。布尔型
     */
    public static final String IS_RECOMMENDED = "isRecommended";
    /**
     * 是否回复。布尔型。
     */
    public static final String IS_REPLIED = "isReplied";
    /**
     * 状态。布尔型。
     */
    public static final String STATUS = "status";

    public static void assemble(MessageBoardArgs args, Map<String, ?> params, Integer defaultSiteId) {
        Integer siteId = Optional.ofNullable(getInteger(params, SITE_ID)).orElse(defaultSiteId);
        args.siteId(siteId);
        Optional.ofNullable(Directives.getInteger(params, TYPE_ID)).ifPresent(args::typeId);
        Optional.ofNullable(getBoolean(params, IS_RECOMMENDED)).ifPresent(args::isRecommended);
        Optional.ofNullable(getBoolean(params, IS_REPLIED)).ifPresent(args::isReplied);
        Optional.ofNullable(Directives.getIntegers(params, STATUS)).ifPresent(args::status);
        Directives.handleOrderBy(args.getQueryMap(), params, "created_desc,id_desc");
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        MessageBoardArgs args = MessageBoardArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env));

        if (isPage) {
            int page = Directives.getPage(params, env);
            int pageSize = Directives.getPageSize(params, env);
            Page<MessageBoard> pagedList = springPage(service.selectPage(args, page, pageSize));
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            List<MessageBoard> list = service.selectList(args, offset, limit);
            loopVars[0] = env.getObjectWrapper().wrap(list);
        }
        body.render(env.getOut());
    }

    @SuppressWarnings("unchecked")
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        doExecute(env, params, loopVars, body, false);
    }

    private final MessageBoardService service;

    public MessageBoardListDirective(MessageBoardService service) {
        this.service = service;
    }
}
