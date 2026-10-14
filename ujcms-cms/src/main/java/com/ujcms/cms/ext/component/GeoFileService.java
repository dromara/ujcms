package com.ujcms.cms.ext.component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import com.ujcms.common.query.QueryParser;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Component;

import java.util.List;
import java.util.Locale;
import java.util.Objects;
import java.util.regex.Pattern;

/**
 * GEO 基础设施文件正文生成（docs/spec/ai/geo.md §5-§6）：llms.txt 与 robots.txt。
 * 前台 {@code GeoController} 输出、后台 {@code GeoController} 预览共用同一实现，保证控制台预览与实际响应逐字节一致。
 *
 * @author PONY
 */
@Component
public class GeoFileService {
    private final ChannelService channelService;
    private final ArticleService articleService;
    private final SiteService siteService;
    private final MessageSource messageSource;

    public GeoFileService(ChannelService channelService, ArticleService articleService, SiteService siteService,
                          MessageSource messageSource) {
        this.channelService = channelService;
        this.articleService = articleService;
        this.siteService = siteService;
        this.messageSource = messageSource;
    }

    /**
     * llms.txt 正文（llmstxt.org 约定）：站点名 + 描述引言、栏目列表、最新文章列表。
     * 栏目未指定时默认取前两级导航栏目；文章仅列已发布（{@code STATUS_PUBLISHED}）。
     * 小节标题按站点语言取自消息包（英文站不再出现中文标题）。
     */
    public String llmsTxt(Site site, Site.Geo geo) {
        Locale locale = localeOf(site);
        StringBuilder buff = new StringBuilder();
        buff.append("# ").append(site.getName()).append('\n');
        String description = site.getSeoDescription();
        if (StringUtils.isNotBlank(description)) {
            buff.append("> ").append(description.trim()).append('\n');
        }
        List<Long> channelIds = geo.getLlmsTxtChannelIds();
        List<Channel> channels = listChannels(site, channelIds);
        if (!channels.isEmpty()) {
            buff.append("\n## ").append(message("geo.llms.channels", "栏目", locale)).append('\n');
            for (Channel channel : channels) {
                if (channel.isLink()) {
                    continue;
                }
                channel.setSite(site);
                buff.append("- [").append(channel.getName()).append("](").append(channel.getFullUrl()).append(')');
                String seoDescription = channel.getSeoDescription();
                if (StringUtils.isNotBlank(seoDescription)) {
                    buff.append(": ").append(seoDescription.trim());
                }
                buff.append('\n');
            }
        }
        int articleCount = geo.getLlmsTxtArticleCount();
        if (articleCount > 0) {
            ArticleArgs args = ArticleArgs.of().siteId(site.getId())
                    .status(List.of(Article.STATUS_PUBLISHED));
            if (channelIds != null && !channelIds.isEmpty()) {
                args.channelAncestorIds(channelIds);
            }
            args.getQueryMap().put(QueryParser.ORDER_BY, "publishDate_desc");
            List<Article> articles = articleService.selectList(args, 0, articleCount);
            if (!articles.isEmpty()) {
                buff.append("\n## ").append(message("geo.llms.latestArticles", "最新文章", locale)).append('\n');
                for (Article article : articles) {
                    if (article.isLink()) {
                        continue;
                    }
                    article.setSite(site);
                    buff.append("- [").append(article.getTitle()).append("](")
                            .append(article.getFullUrl()).append(")\n");
                }
            }
        }
        return buff.toString();
    }

    /**
     * robots.txt 正文：自定义内容非空则原样输出，否则生成全站放行 + 按拒绝名单逐个 Disallow 的形式。
     * 随后追加 {@code Sitemap:} 行——自定义内容已自带 Sitemap 行时不追加（避免重复声明）；
     * 根站点还为同一主机下的子目录站点各补一行：爬虫只读主机根的 robots.txt（RFC 9309），
     * {@code /{subDir}/robots.txt} 不会被读取，子目录站点的站点地图只能在这里被发现。
     */
    public String robotsTxt(Site site, Site.Geo geo) {
        StringBuilder buff = new StringBuilder();
        String custom = geo.getRobotsCustom();
        boolean customUsed = StringUtils.isNotBlank(custom);
        if (customUsed) {
            buff.append(custom.trim()).append('\n');
        } else {
            buff.append("User-agent: *\nAllow: /\n");
            List<String> agents = geo.getDisallowedAiAgents();
            if (agents != null) {
                for (String agent : agents) {
                    if (StringUtils.isNotBlank(agent)) {
                        buff.append("\nUser-agent: ").append(agent.trim()).append("\nDisallow: /\n");
                    }
                }
            }
        }
        if (!customUsed || !hasSitemapLine(custom)) {
            buff.append("\nSitemap: ").append(site.getFullUrl()).append("/sitemap.xml\n");
            for (Site subSite : siteService.listSubDirSites(site)) {
                buff.append("Sitemap: ").append(subSite.getFullUrl()).append("/sitemap.xml\n");
            }
        }
        return buff.toString();
    }

    /**
     * 文本中是否已有 {@code Sitemap:} 指令行（不区分大小写，允许行首空白）
     */
    static boolean hasSitemapLine(@Nullable String text) {
        return text != null && SITEMAP_LINE.matcher(text).find();
    }

    private List<Channel> listChannels(Site site, @Nullable List<Long> channelIds) {
        if (channelIds != null && !channelIds.isEmpty()) {
            // 显式指定栏目：按运营方给定顺序逐个获取，并过滤跨站点ID
            return channelIds.stream().map(channelService::select)
                    .filter(Objects::nonNull)
                    .filter(channel -> site.getId().equals(channel.getSiteId()))
                    .toList();
        }
        ChannelArgs args = ChannelArgs.of().siteId(site.getId());
        args.isNav(true);
        args.getQueryMap().put("LE_depth_Short", TOP_NAV_DEPTH);
        return channelService.selectList(args);
    }

    private String message(String code, String defaultMessage, Locale locale) {
        return messageSource.getMessage(code, null, defaultMessage, locale);
    }

    /**
     * 站点语言对应的消息包 Locale。中文站直接取基础包（{@code Locale.ROOT} → messages.properties），
     * 不受 {@code fallbackToSystemLocale} 影响：否则英文 JVM 上中文站会落到 messages_en
     */
    static Locale localeOf(Site site) {
        String lang = StringUtils.defaultIfBlank(site.getLang(), "zh-CN");
        if (Strings.CI.startsWith(lang, "zh")) {
            return Locale.ROOT;
        }
        return Locale.forLanguageTag(lang);
    }

    private static final Pattern SITEMAP_LINE = Pattern.compile("(?im)^\\s*sitemap\\s*:");
    /**
     * 默认导航栏目层级：前两级
     */
    private static final short TOP_NAV_DEPTH = 2;
}
