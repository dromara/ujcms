package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.ext.domain.MessageBoard;
import com.ujcms.cms.ext.service.MessageBoardService;
import com.ujcms.cms.ext.service.args.MessageBoardArgs;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.freemarker.Freemarkers;
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

import static com.ujcms.cms.core.web.support.Directives.*;
import static com.ujcms.commons.db.MyBatis.springPage;

/**
 * 留言板列表 标签
 *
 * @author PONY
 */
public class MessageBoardListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Long)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 类型ID。整型(Long)。
     */
    public static final String TYPE_ID = "typeId";
    /**
     * 用户ID。整型(Long)。
     */
    public static final String USER_ID = "userId";
    /**
     * 是否推荐。布尔型
     */
    public static final String IS_RECOMMENDED = "isRecommended";
    /**
     * 是否回复。布尔型。
     */
    public static final String IS_REPLIED = "isReplied";
    /**
     * 是否获取所有站点的数据。布尔型(Boolean)。默认：false。
     */
    public static final String IS_ALL_SITE = "isAllSite";
    /**
     * 状态。短整型(Short)。
     */
    public static final String STATUS = "status";

    public static void assemble(MessageBoardArgs args, Map<String, ?> params, Long defaultSiteId) {
        Long siteId = getLong(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);
        Optional.ofNullable(getLong(params, TYPE_ID)).ifPresent(args::typeId);
        Optional.ofNullable(getLong(params, USER_ID)).ifPresent(args::userId);
        Optional.ofNullable(getBoolean(params, IS_RECOMMENDED)).ifPresent(args::isRecommended);
        Optional.ofNullable(getBoolean(params, IS_REPLIED)).ifPresent(args::isReplied);
        Optional.ofNullable(getShorts(params, STATUS)).ifPresent(args::status);
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
