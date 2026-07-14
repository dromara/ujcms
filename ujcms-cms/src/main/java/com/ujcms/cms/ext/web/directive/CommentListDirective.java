package com.ujcms.cms.ext.web.directive;

import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Comment;
import com.ujcms.cms.ext.service.CommentService;
import com.ujcms.cms.ext.service.args.CommentArgs;
import com.ujcms.common.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.web.support.Directives.*;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 评论列表 标签
 *
 * @author PONY
 */
public class CommentListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Long)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 文章ID。整型(Long)。
     */
    public static final String ARTICLE_ID = "articleId";
    /**
     * 用户ID。整型(Long)。
     */
    public static final String USER_ID = "userId";
    /**
     * 上级评论ID。整型(Long)。
     */
    public static final String PARENT_ID = "parentId";
    /**
     * 是否推荐。布尔型。
     */
    public static final String IS_RECOMMENDED = "isRecommended";
    /**
     * 是否获取所有站点的数据。布尔型(Boolean)。默认：false。
     */
    public static final String IS_ALL_SITE = "isAllSite";
    /**
     * 状态。短整型(Short)。默认：0(已审核)。
     */
    public static final String STATUS = "status";

    public static void assemble(CommentArgs args, Map<String, ?> params, Long defaultSiteId) {
        Long siteId = getLong(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        args.siteId(siteId);
        Optional.ofNullable(getLong(params, ARTICLE_ID)).ifPresent(args::articleId);
        Optional.ofNullable(getLong(params, USER_ID)).ifPresent(args::userId);
        Optional.ofNullable(getLong(params, PARENT_ID)).ifPresent(args::parentId);
        Optional.ofNullable(getBoolean(params, IS_RECOMMENDED)).ifPresent(args::isRecommended);
        // 默认只获取已审核数据
        args.status(Optional.ofNullable(getShorts(params, STATUS))
                .orElse(Collections.singletonList(Comment.STATUS_AUDITED)));
        Directives.handleOrderBy(args.getQueryMap(), params, "created_desc,id_desc");
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        CommentArgs args = CommentArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env));

        if (isPage) {
            int page = Directives.getPage(params, env);
            int pageSize = Directives.getPageSize(params, env);
            Page<Comment> pagedList = springPage(service.selectPage(args, page, pageSize));
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            List<Comment> list = service.selectList(args, offset, limit);
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

    private final CommentService service;

    public CommentListDirective(CommentService service) {
        this.service = service;
    }
}
