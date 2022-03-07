package com.ujcms.core.web.directive;

import com.ujcms.core.domain.Article;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.service.ChannelService;
import com.ujcms.core.support.Frontends;
import com.ujcms.core.web.support.Directives;
import com.ofwise.util.db.MyBatis;
import com.ofwise.util.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.apache.commons.collections4.CollectionUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collection;
import java.util.List;
import java.util.Map;
import java.util.Optional;

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
     * 栏目别名。字符串(String)。
     */
    public static final String CHANNEL = "channel";
    /**
     * 栏目ID。整型(Integer)。
     */
    public static final String CHANNEL_ID = "channelId";
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
     * 是否包含子栏目的文章。布尔型(Boolean)。默认：false。
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

    public static void assemble(Map<String, Object> queryMap, Map<String, ?> params, Integer defaultSiteId,
                                ChannelService channelService) {
        Integer siteId = Directives.getInteger(params, SITE_ID);
        // 不获取所有站点，则给默认站点ID
        if (siteId == null && !Directives.getBoolean(params, IS_ALL_SITE, false)) {
            siteId = defaultSiteId;
        }
        Boolean isIncludeSubSite = Directives.getBoolean(params, IS_INCLUDE_SUB_SITE, false);
        if (siteId != null) {
            if (isIncludeSubSite) {
                queryMap.put("EQ_site@SiteTree@descendant-ancestorId_Integer", siteId);
            } else {
                queryMap.put("EQ_siteId_Integer", siteId);
            }
        }

        Integer channelId = Directives.getInteger(params, CHANNEL_ID);
        List<Integer> channelIds = new ArrayList<>();
        if (channelId != null) {
            channelIds.add(channelId);
        } else {
            Collection<String> channelAlias = Directives.getStrings(params, CHANNEL);
            if (CollectionUtils.isNotEmpty(channelAlias)) {
                channelService.listBySiteIdAndAlias(siteId, channelAlias, isIncludeSubSite)
                        .forEach(channel -> channelIds.add(channel.getId()));
            }
        }

        if (!channelIds.isEmpty()) {
            if (Directives.getBoolean(params, IS_INCLUDE_SUB_CHANNEL, false)) {
                queryMap.put("In_channel@ChannelTree@descendant-ancestorId_Integer", channelIds);
            } else {
                queryMap.put("In_channelId_Integer", channelIds);
            }
        } else if (siteId != null) {
            if (isIncludeSubSite) {
                queryMap.put("EQ_site@SiteTree@descendant-ancestorId_Integer", siteId);
            } else {
                queryMap.put("EQ_siteId_Integer", siteId);
            }
        }

        Optional.ofNullable(Directives.getOffsetDateTime(params, BEGIN_PUBLISH_DATE))
                .ifPresent(begin -> queryMap.put("GE_publishDate_DateTime", begin));
        Optional.ofNullable(Directives.getOffsetDateTime(params, END_PUBLISH_DATE))
                .ifPresent(end -> queryMap.put("LE_publishDate_DateTime", end));
        Optional.ofNullable(Directives.getBoolean(params, IS_WITH_IMAGE))
                .ifPresent(isWithImage -> queryMap.put("EQ_withImage_Boolean", isWithImage));
        Optional.ofNullable(Directives.getString(params, TITLE)).filter(StringUtils::isNotBlank)
                .ifPresent(title -> queryMap.put("Contains_@articleExt-title", title));
        Optional.ofNullable(Directives.getString(params, TEXT)).filter(StringUtils::isNotBlank)
                .ifPresent(text -> queryMap.put("Contains_@articleExt-text", text));
        Optional.ofNullable(Directives.getIntegers(params, EXCLUDE_ID)).filter(CollectionUtils::isNotEmpty)
                .ifPresent(excludeIds -> queryMap.put("NotIn_id", excludeIds));

        Directives.handleOrderBy(queryMap, params, "publishDate_desc, id_desc");
    }

    protected void doExecute(Environment env, Map<String, TemplateModel> params, TemplateModel[] loopVars,
                             TemplateDirectiveBody body, boolean isPage) throws TemplateException, IOException {
        Freemarkers.checkLoopVars(loopVars);
        Freemarkers.checkBody(body);

        Map<String, Object> queryMap = Directives.getQueryMap(params);
        assemble(queryMap, params, Frontends.getSiteId(env), channelService);
        Map<String, String> customsQueryMap = Directives.getCustomsQueryMap(params);

        if (isPage) {
            int page = Directives.getPage(params, env);
            int pageSize = Directives.getPageSize(params, env);
            Page<Article> pagedList = MyBatis.toPage(
                    articleService.selectPage(queryMap, customsQueryMap, null, page, pageSize));
            Directives.setTotalPages(pagedList.getTotalPages());
            loopVars[0] = env.getObjectWrapper().wrap(pagedList);
        } else {
            Integer offset = Directives.findOffset(params);
            Integer limit = Directives.findLimit(params);
            List<Article> list = articleService.selectList(queryMap, customsQueryMap, null, offset, limit);
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

    private ArticleService articleService;
    private ChannelService channelService;

    public ArticleListDirective(ArticleService articleService, ChannelService channelService) {
        this.articleService = articleService;
        this.channelService = channelService;
    }
}
