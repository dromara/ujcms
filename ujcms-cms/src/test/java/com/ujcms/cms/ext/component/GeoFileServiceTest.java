package com.ujcms.cms.ext.component;

import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ChannelArgs;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.support.StaticMessageSource;

import java.util.List;
import java.util.Locale;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link GeoFileService} 契约（docs/spec/ai/geo.md §5-§6）：robots.txt 的 Sitemap 行规则（自定义内容已含 Sitemap 时不追加、
 * 根站点为子目录站点补行）与 llms.txt 小节标题随站点语言。
 */
class GeoFileServiceTest {
    private final ChannelService channelService = mock(ChannelService.class);
    private final ArticleService articleService = mock(ArticleService.class);
    private final SiteService siteService = mock(SiteService.class);
    private final StaticMessageSource messageSource = new StaticMessageSource();
    private final GeoFileService service = new GeoFileService(channelService, articleService, siteService, messageSource);

    @BeforeEach
    void messages() {
        messageSource.addMessage("geo.llms.channels", Locale.ROOT, "栏目");
        messageSource.addMessage("geo.llms.latestArticles", Locale.ROOT, "最新文章");
        messageSource.addMessage("geo.llms.channels", Locale.ENGLISH, "Channels");
        messageSource.addMessage("geo.llms.latestArticles", Locale.ENGLISH, "Latest Articles");
    }

    private static Site site(String lang, String fullUrl) {
        Site site = mock(Site.class);
        when(site.getId()).thenReturn(1L);
        when(site.getName()).thenReturn("Example");
        when(site.getLang()).thenReturn(lang);
        when(site.getFullUrl()).thenReturn(fullUrl);
        return site;
    }

    // ------------------------------------------------------------------ robots.txt

    @Test
    void rootSiteListsItsOwnAndEverySubDirectorySitemap() {
        Site root = site("zh-CN", "https://www.example.com");
        Site en = site("en", "https://www.example.com/en");
        when(siteService.listSubDirSites(root)).thenReturn(List.of(en));

        String body = service.robotsTxt(root, new Site.Geo());

        assertEquals("User-agent: *\nAllow: /\n\nUser-agent: Bytespider\nDisallow: /\n\n"
                + "Sitemap: https://www.example.com/sitemap.xml\n"
                + "Sitemap: https://www.example.com/en/sitemap.xml\n", body,
                "crawlers only read the host-root robots.txt, so sub-directory sitemaps must be announced there");
    }

    @Test
    void customContentWithItsOwnSitemapLineIsServedVerbatim() {
        Site root = site("zh-CN", "https://www.example.com");
        Site.Geo geo = new Site.Geo();
        geo.setRobotsCustom("User-agent: *\nDisallow: /private/\n\nsitemap: https://cdn.example.com/sm.xml\n");

        String body = service.robotsTxt(root, geo);

        assertEquals("User-agent: *\nDisallow: /private/\n\nsitemap: https://cdn.example.com/sm.xml\n", body,
                "no duplicate Sitemap declaration");
        verify(siteService, never()).listSubDirSites(any());
    }

    @Test
    void customContentWithoutSitemapLineGetsTheSitemapLinesAppended() {
        Site root = site("zh-CN", "https://www.example.com");
        Site.Geo geo = new Site.Geo();
        geo.setRobotsCustom("User-agent: *\nDisallow: /private/");

        assertEquals("User-agent: *\nDisallow: /private/\n\nSitemap: https://www.example.com/sitemap.xml\n",
                service.robotsTxt(root, geo));
    }

    @Test
    void nullAgentListIsTolerated() {
        Site root = site("zh-CN", "https://www.example.com");
        Site.Geo geo = new Site.Geo();
        geo.setDisallowedAiAgents(null);

        assertEquals("User-agent: *\nAllow: /\n\nSitemap: https://www.example.com/sitemap.xml\n",
                service.robotsTxt(root, geo));
    }

    @Test
    void sitemapLineDetectionIsCaseInsensitiveAndAnchoredToLineStart() {
        assertTrue(GeoFileService.hasSitemapLine("Sitemap: https://a/b.xml"));
        assertTrue(GeoFileService.hasSitemapLine("User-agent: *\n  SITEMAP : https://a/b.xml"));
        assertFalse(GeoFileService.hasSitemapLine("# see sitemap: elsewhere"));
        assertFalse(GeoFileService.hasSitemapLine(null));
    }

    // ------------------------------------------------------------------ llms.txt

    private void stubContent(Site site) {
        Channel channel = mock(Channel.class);
        when(channel.getName()).thenReturn("News");
        when(channel.getFullUrl()).thenReturn("https://www.example.com/channel/news");
        when(channelService.selectList(any(ChannelArgs.class))).thenReturn(List.of(channel));
        Article article = mock(Article.class);
        when(article.getTitle()).thenReturn("Hello");
        when(article.getFullUrl()).thenReturn("https://www.example.com/article/1");
        when(articleService.selectList(any(ArticleArgs.class), eq(0), eq(20))).thenReturn(List.of(article));
    }

    @Test
    void llmsTxtHeadingsFollowTheSiteLanguage() {
        Site en = site("en", "https://www.example.com");
        stubContent(en);

        String body = service.llmsTxt(en, new Site.Geo());

        assertEquals("# Example\n\n## Channels\n- [News](https://www.example.com/channel/news)\n\n"
                + "## Latest Articles\n- [Hello](https://www.example.com/article/1)\n", body);
    }

    @Test
    void chineseSitesUseTheBaseBundleRegardlessOfSystemLocale() {
        Site zh = site("zh-CN", "https://www.example.com");
        stubContent(zh);

        String body = service.llmsTxt(zh, new Site.Geo());

        assertTrue(body.contains("\n## 栏目\n"), body);
        assertTrue(body.contains("\n## 最新文章\n"), body);
        assertEquals(Locale.ROOT, GeoFileService.localeOf(zh));
        assertEquals(Locale.ENGLISH, GeoFileService.localeOf(site("en", "")));
        assertEquals(Locale.ROOT, GeoFileService.localeOf(site(null, "")), "blank language defaults to Chinese");
    }
}
