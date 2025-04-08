package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.base.ChannelBase;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.commons.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.collections4.CollectionUtils;
import org.springframework.data.domain.Page;
import org.springframework.lang.Nullable;

import java.io.IOException;
import java.util.*;
import java.util.stream.Collectors;

import static com.ujcms.cms.core.web.support.Directives.*;
import static com.ujcms.commons.db.MyBatis.springPage;

/**
 * 文章列表 标签
 *
 * @author PONY
 */
public class ArticleListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。整型(Integer)。
     */
    public static final String SITE_ID = "siteId";
    /**
     * 栏目别名。多个栏目别名可以用逗号分开，如`news,sports`。字符串(String)。
     */
    public static final String CHANNEL = "channel";
    /**
     * 栏目ID。多个栏目ID可以用逗号分开，如`23,5,89`。整型(Integer)。
     */
    public static final String CHANNEL_ID = "channelId";
    /**
     * TagID。整型(Integer)。
     */
    public static final String TAG_ID = "tagId";
    /**
     * 开始发布日期。日期(OffsetDateTime)。
     */
    public static final String BEGIN_PUBLISH_DATE = "beginPublishDate";
    /**
     * 结束发布日期。日期(OffsetDateTime)。
     */
    public static final String END_PUBLISH_DATE = "endPublishDate";
    /**
     * 是否有标题图。布尔型(Boolean)。
     */
    public static final String IS_WITH_IMAGE = "isWithImage";
    /**
     * 标题或正文。字符串(String)。
     */
    public static final String Q = "q";
    /**
     * 标题。字符串(String)。
     */
    public static final String TITLE = "title";
    /**
     * 正文。字符串(String)。
     */
    public static final String TEXT = "text";
    /**
     * 不包含的文章ID。整型(Integer)。多个用英文逗号分隔，如'1,2,5'。
     */
    public static final String EXCLUDE_ID = "excludeId";
    /**
     * 文章状态。短整型(Short)。多个用英文逗号分隔，如'1,2,5'。默认：0（已发布）
     */
    public static final String STATUS = "status";
    /**
     * 是否包含子栏目的文章。布尔型(Boolean)。默认：true。
     */
    public static final String IS_INCLUDE_SUB_CHANNEL = "isIncludeSubChannel";
    /**
     * 是否包含子站点的文章。布尔型(Boolean)。默认：false。
     */
    public static final String IS_INCLUDE_SUB_SITE = "isIncludeSubSite";
    /**
     * 是否获取所有站点文章。布尔型(Boolean)。默认：false。
     */
    public static final String IS_ALL_SITE = "isAllSite";

    public static Collection<Long> getChannelIds(
            Map<String, ?> params, @Nullable Long siteId, Boolean isIncludeSubSite, ChannelService channelService) {
        Collection<Long> channelIds = Optional.ofNullable(getLongs(params, CHANNEL_ID))
                .orElse(Collections.emptyList());
        if (CollectionUtils.isEmpty(channelIds)) {
            Collection<String> channelAlias = getStrings(params, CHANNEL);
            if (CollectionUtils.isNotEmpty(channelAlias)) {
                channelIds = channelService.listBySiteIdAndAlias(siteId, channelAlias, isIncludeSubSite)
                        .stream().map(ChannelBase::getId).collect(Collectors.toList());
            }
        }
        return channelIds;
    }

    public static void assemble(ArticleArgs args, Map<String, ?> params, Long defaultSiteId,
                                ChannelService channelService) {
        Long siteId = getLong(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        Boolean isIncludeSubSite = getBoolean(params, IS_INCLUDE_SUB_SITE, false);

        Collection<Long> channelIds = getChannelIds(params, siteId, isIncludeSubSite, channelService);
        if (CollectionUtils.isNotEmpty(channelIds)) {
            if (Boolean.TRUE.equals(getBoolean(params, IS_INCLUDE_SUB_CHANNEL, true))) {
                args.channelAncestorIds(channelIds);
            } else {
                args.inChannelIds(channelIds);
            }
        } else if (siteId != null) {
            if (Boolean.TRUE.equals(isIncludeSubSite)) {
                args.siteAncestorId(siteId);
            } else {
                args.siteId(siteId);
            }
        }

        Collection<Short> status = getShorts(params, STATUS);
        if (status == null) {
            status = Collections.singletonList(Article.STATUS_PUBLISHED);
        }
        args.status(status);

        Optional.ofNullable(Directives.getLong(params, TAG_ID)).ifPresent(args::tagId);
        Optional.ofNullable(Directives.getOffsetDateTime(params, BEGIN_PUBLISH_DATE)).ifPresent(args::gePublishDate);
        Optional.ofNullable(Directives.getOffsetDateTime(params, END_PUBLISH_DATE)).ifPresent(args::lePublishDate);
        Optional.ofNullable(getBoolean(params, IS_WITH_IMAGE)).ifPresent(args::isWithImage);
        Optional.ofNullable(Directives.getString(params, Q)).ifPresent(args::q);
        Optional.ofNullable(Directives.getString(params, TITLE)).ifPresent(args::containsTitle);
        Optional.ofNullable(Directives.getString(params, TEXT)).ifPresent(args::containText);
        Optional.ofNullable(Directives.getLongs(params, EXCLUDE_ID)).ifPresent(args::excludeIds);

        Directives.handleOrderBy(args.getQueryMap(), params, "order_desc");
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);

        ArticleArgs args = ArticleArgs.of(Directives.getQueryMap(params));
        assemble(args, params, Frontends.getSiteId(env), channelService);
        args.customsQueryMap(Directives.getCustomsQueryMap(params));

        if (isPage) {
            int page = Directives.getPage(params, env);
            int pageSize = Directives.getPageSize(params, env);
            Page<Article> pagedList = springPage(articleService.selectPage(args, page, pageSize));
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            List<Article> list = articleService.selectList(args, offset, limit);
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

    private final ArticleService articleService;
    private final ChannelService channelService;

    public ArticleListDirective(ArticleService articleService, ChannelService channelService) {
        this.articleService = articleService;
        this.channelService = channelService;
    }
}
