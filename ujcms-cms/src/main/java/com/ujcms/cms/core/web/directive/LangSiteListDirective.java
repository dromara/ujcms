package com.ujcms.cms.core.web.directive;

import com.ujcms.cms.core.component.LangAlternateService;
import com.ujcms.cms.core.component.LangAlternateService.LangAlternate;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.common.freemarker.Freemarkers;
import freemarker.core.Environment;
import freemarker.template.TemplateDirectiveBody;
import freemarker.template.TemplateDirectiveModel;
import freemarker.template.TemplateException;
import freemarker.template.TemplateModel;
import org.jspecify.annotations.Nullable;

import java.io.IOException;
import java.util.Collections;
import java.util.List;
import java.util.Map;

/**
 * 语言站点列表 标签（multilingual P3）。输出当前页面的语言备选列表（LangAlternate），
 * 供语言切换器与 hreflang 块使用。
 *
 * <p>页面上下文不经参数传递——标签直接读取数据模型的 {@code article}/{@code channel}/{@code isHome}
 * 键。动态控制器与静态页生成（HtmlService）注入同一组键，因此标签在两条渲染管线下行为一致，
 * 无需改动 HtmlService。
 *
 * <pre>
 * [@LangSiteList; alternates]      ← loopVars[0] = List&lt;LangAlternate&gt;
 *   ...body 渲染一次...
 * [/@LangSiteList]
 * </pre>
 *
 * @author PONY
 */
public class LangSiteListDirective implements TemplateDirectiveModel {
    /**
     * 站点ID。长整型（Long）。默认为数据模型中的当前站点（与 ChannelList.siteId 对齐）
     */
    private static final String SITE_ID = "siteId";
    /**
     * 是否包含当前站点自身条目。布尔型（Boolean）。默认：true
     */
    private static final String IS_INCLUDE_SELF = "isIncludeSelf";
    /**
     * 是否仅精确对等条目（hreflang 块所需）。布尔型（Boolean）。默认：false
     */
    private static final String IS_EXACT_ONLY = "isExactOnly";

    /**
     * 数据模型键：文章页上下文
     */
    private static final String ARTICLE_KEY = "article";
    /**
     * 数据模型键：栏目页上下文
     */
    private static final String CHANNEL_KEY = "channel";
    /**
     * 数据模型键：是否首页
     */
    private static final String IS_HOME_KEY = "isHome";

    @SuppressWarnings({"rawtypes", "unchecked"})
    @Override
    public void execute(Environment env, Map params, TemplateModel[] loopVars, TemplateDirectiveBody body)
            throws TemplateException, IOException {
        Freemarkers.requireLoopVars(loopVars);
        Freemarkers.requireBody(body);
        Site site = Frontends.getSite(env);
        Long siteId = Directives.getLong(params, SITE_ID);
        if (siteId != null && !siteId.equals(site.getId())) {
            site = siteService.select(siteId);
        }
        boolean isIncludeSelf = Directives.getBoolean(params, IS_INCLUDE_SELF, true);
        boolean isExactOnly = Directives.getBoolean(params, IS_EXACT_ONLY, false);

        List<LangAlternate> list = listAlternates(env, site, isIncludeSelf, isExactOnly);
        loopVars[0] = env.getObjectWrapper().wrap(list);
        body.render(env.getOut());
    }

    private List<LangAlternate> listAlternates(Environment env, @Nullable Site site,
                                               boolean isIncludeSelf, boolean isExactOnly)
            throws TemplateException {
        if (site == null) {
            // siteId 参数指向不存在的站点：与 ChannelList 的宽松取向一致，输出空列表而非中断渲染
            return Collections.emptyList();
        }
        Article article = Freemarkers.findModel(env, ARTICLE_KEY, Article.class);
        Channel channel = Freemarkers.findModel(env, CHANNEL_KEY, Channel.class);
        boolean isHome = Boolean.TRUE.equals(Freemarkers.getBoolean(env.getDataModel().get(IS_HOME_KEY), false));
        List<LangAlternate> list = langAlternateService.listAlternates(site, channel, article, isHome);
        if (isExactOnly) {
            list = list.stream().filter(LangAlternate::isExact).toList();
        }
        if (!isIncludeSelf) {
            list = list.stream().filter(alternate -> !alternate.isCurrent()).toList();
        }
        return list;
    }

    private final SiteService siteService;
    private final LangAlternateService langAlternateService;

    public LangSiteListDirective(SiteService siteService, LangAlternateService langAlternateService) {
        this.siteService = siteService;
        this.langAlternateService = langAlternateService;
    }
}
