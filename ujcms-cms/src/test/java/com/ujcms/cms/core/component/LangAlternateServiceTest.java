package com.ujcms.cms.core.component;

import com.ujcms.cms.core.component.LangAlternateService.LangAlternate;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ArticleI18nService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.SiteService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Map;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertSame;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link LangAlternateService} 映射规则表测试（language-surface.md §2 + translation-links.md §4）：
 * 文章页优先翻译等价集直链（exact=true），未链接回退 exact=false；栏目页同别名命中 exact=true、
 * 未命中回退最近的同别名上级栏目、再不然首页，exact=false；首页 exact=true；其它页 exact=false；当前站点条目自引用；
 * 跨站 URL 按对等站点重组；sitemap 批量方法分桶。
 */
class LangAlternateServiceTest {
    private SiteService siteService;
    private ChannelService channelService;
    private ArticleI18nService articleI18nService;
    private LangAlternateService service;

    /**
     * 当前站点（语言组主站点，zh-CN，域名寻址）
     */
    private Site master;
    /**
     * 对等语言站点（en，子目录寻址，域名不同）
     */
    private Site peer;

    @BeforeEach
    void setUp() {
        siteService = mock(SiteService.class);
        channelService = mock(ChannelService.class);
        articleI18nService = mock(ArticleI18nService.class);
        service = new LangAlternateService(siteService, channelService, articleI18nService);

        master = site(1L, "www.example.com", null, "zh-CN");
        peer = site(2L, "en.example.com", null, "en");
        peer.setLangParentId(1L);
        when(siteService.listByLangGroup(master)).thenReturn(List.of(master, peer));
    }

    private static Site site(long id, String domain, String subDir, String lang) {
        Site site = new Site();
        site.setId(id);
        site.setName("site-" + id);
        site.setProtocol("https");
        site.setDomain(domain);
        site.setSubDir(subDir);
        site.setLang(lang);
        return site;
    }

    private static Channel channel(Site site, String slug) {
        Channel channel = new Channel();
        channel.setSlug(slug);
        channel.setSite(site);
        return channel;
    }

    @Test
    void ungroupedSiteReturnsSingleCurrentEntry() {
        Site alone = site(9L, "www.example.org", null, "zh-CN");
        when(siteService.listByLangGroup(alone)).thenReturn(List.of(alone));
        List<LangAlternate> list = service.listAlternates(alone, null, null, true);
        assertEquals(1, list.size());
        assertTrue(list.get(0).isCurrent());
        assertTrue(list.get(0).isExact());
    }

    @Test
    void homeContextPeersAreExactHomeLinks() {
        List<LangAlternate> list = service.listAlternates(master, null, null, true);
        assertEquals(2, list.size());
        LangAlternate current = list.get(0);
        assertTrue(current.isCurrent());
        assertEquals("zh-CN", current.getLang());
        LangAlternate alt = list.get(1);
        assertSame(peer, alt.getSite());
        assertEquals("en", alt.getLang());
        assertTrue(alt.isExact());
        assertTrue(alt.getFullUrl().contains("en.example.com"), alt.getFullUrl());
    }

    @Test
    void channelContextSlugHitIsExactAndUsesPeerChannelUrl() {
        Channel channel = channel(master, "news");
        Channel peerChannel = new Channel();
        peerChannel.setSlug("news");
        when(channelService.findBySiteIdAndSlug(2L, "news")).thenReturn(peerChannel);

        List<LangAlternate> list = service.listAlternates(master, channel, null, false);
        LangAlternate alt = list.get(1);
        assertTrue(alt.isExact());
        // setSite(peer) 重挂后，URL 按对等站点的域名组装
        assertTrue(alt.getFullUrl().contains("en.example.com"), alt.getFullUrl());
        assertTrue(alt.getFullUrl().contains("news"), alt.getFullUrl());
        // 当前条目自引用栏目 URL
        assertTrue(list.get(0).getFullUrl().contains("www.example.com"), list.get(0).getFullUrl());
        assertTrue(list.get(0).getFullUrl().contains("news"), list.get(0).getFullUrl());
    }

    @Test
    void channelContextSlugMissFallsBackToPeerHomeInexact() {
        Channel channel = channel(master, "news");
        when(channelService.findBySiteIdAndSlug(2L, "news")).thenReturn(null);

        List<LangAlternate> list = service.listAlternates(master, channel, null, false);
        LangAlternate alt = list.get(1);
        assertFalse(alt.isExact());
        assertEquals(peer.getFullUrl(), alt.getFullUrl());
    }

    /**
     * 两站栏目树不逐级一致：子栏目在对方没有同别名栏目时，落到最近的同别名上级（exact=false），
     * 而不是一步退回首页
     */
    @Test
    void channelContextSlugMissFallsBackToNearestAncestorPeer() {
        Channel root = channel(master, "news");
        Channel child = channel(master, "china");
        child.setParent(root);
        Channel peerRoot = new Channel();
        peerRoot.setSlug("news");
        when(channelService.findBySiteIdAndSlug(2L, "china")).thenReturn(null);
        when(channelService.findBySiteIdAndSlug(2L, "news")).thenReturn(peerRoot);

        LangAlternate alt = service.listAlternates(master, child, null, false).get(1);
        assertFalse(alt.isExact());
        assertTrue(alt.getFullUrl().contains("en.example.com"), alt.getFullUrl());
        assertTrue(alt.getFullUrl().contains("news"), alt.getFullUrl());
    }

    /**
     * 多级上溯取最近命中：中间层先命中即止，不再看更上层
     */
    @Test
    void ancestorFallbackPicksNearestMatch() {
        Channel root = channel(master, "product");
        Channel middle = channel(master, "camera");
        middle.setParent(root);
        Channel leaf = channel(master, "lens");
        leaf.setParent(middle);
        Channel peerMiddle = new Channel();
        peerMiddle.setSlug("camera");
        when(channelService.findBySiteIdAndSlug(2L, "camera")).thenReturn(peerMiddle);

        LangAlternate alt = service.listAlternates(master, leaf, null, false).get(1);
        assertFalse(alt.isExact());
        assertTrue(alt.getFullUrl().contains("camera"), alt.getFullUrl());
        verify(channelService, never()).findBySiteIdAndSlug(2L, "product");
    }

    /**
     * 未链接文章同样沿所在栏目上溯
     */
    @Test
    void unlinkedArticleFallsBackToNearestAncestorPeer() {
        Channel root = channel(master, "news");
        Channel child = channel(master, "china");
        child.setParent(root);
        Article article = new Article();
        article.setId(10L);
        article.setChannel(child);
        article.setSite(master);
        Channel peerRoot = new Channel();
        peerRoot.setSlug("news");
        when(channelService.findBySiteIdAndSlug(2L, "news")).thenReturn(peerRoot);

        LangAlternate alt = service.listAlternates(master, child, article, false).get(1);
        assertFalse(alt.isExact());
        assertTrue(alt.getFullUrl().contains("news"), alt.getFullUrl());
    }

    /**
     * 只要 exact 条目的调用方（sitemap）不为回退目标付查找：未命中即止，不上溯
     */
    @Test
    void exactOnlyDoesNotWalkAncestors() {
        Channel root = channel(master, "news");
        Channel child = channel(master, "china");
        child.setParent(root);

        List<LangAlternate> list = service.listExactAlternates(master, child);
        assertEquals(1, list.size());
        assertTrue(list.get(0).isCurrent());
        verify(channelService).findBySiteIdAndSlug(2L, "china");
        verify(channelService, never()).findBySiteIdAndSlug(2L, "news");
    }

    /**
     * 文章页 + 翻译等价集直链（P5）：对等成员文章 → exact=true 文章直链，URL 按对等站点重组
     */
    @Test
    void linkedArticleIsExactDirectLink() {
        Channel channel = channel(master, "news");
        Article article = new Article();
        article.setId(10L);
        article.setChannel(channel);
        article.setSite(master);
        Article peerArticle = new Article();
        peerArticle.setId(77L);
        when(articleI18nService.findByGroupAndSiteId(10L, 2L)).thenReturn(peerArticle);

        List<LangAlternate> list = service.listAlternates(master, channel, article, false);
        LangAlternate alt = list.get(1);
        assertTrue(alt.isExact());
        assertTrue(alt.getFullUrl().contains("en.example.com"), alt.getFullUrl());
        assertTrue(alt.getFullUrl().contains("/article/77"), alt.getFullUrl());
        // 直链命中后不再走栏目别名回退
        assertEquals(2, list.size());
    }

    /**
     * sitemap 批量（P5）：源文章ID → exact 备选（含自引用），按组内站点排序分桶；
     * 未分组站点与未链接文章无条目
     */
    @Test
    void listExactAlternatesByArticlesBucketsPerArticle() {
        Article a10 = new Article();
        a10.setId(10L);
        a10.setSiteId(1L);
        Article a77 = new Article();
        a77.setId(77L);
        a77.setSiteId(2L);
        Article unlinked = new Article();
        unlinked.setId(30L);
        unlinked.setSiteId(1L);
        when(articleI18nService.listMembersByArticleIds(List.of(10L, 30L)))
                .thenReturn(Map.of(10L, List.of(a10, a77)));

        Map<Long, List<LangAlternate>> result =
                service.listExactAlternatesByArticles(master, List.of(a10, unlinked));
        assertEquals(1, result.size());
        List<LangAlternate> alternates = result.get(10L);
        assertEquals(2, alternates.size());
        // 组内站点顺序：master 在前（自引用），peer 在后
        assertTrue(alternates.get(0).isCurrent());
        assertTrue(alternates.get(1).isExact());
        assertTrue(alternates.get(1).getFullUrl().contains("/article/77"), alternates.get(1).getFullUrl());
    }

    @Test
    void listExactAlternatesByArticlesEmptyForUngroupedSite() {
        Site alone = site(9L, "www.example.org", null, "zh-CN");
        when(siteService.listByLangGroup(alone)).thenReturn(List.of(alone));
        Article article = new Article();
        article.setId(10L);
        assertTrue(service.listExactAlternatesByArticles(alone, List.of(article)).isEmpty());
    }

    /**
     * 文章页未链接翻译：栏目不是该文章的译文——即使别名命中也是 exact=false
     */
    @Test
    void articleContextIsNeverExact() {
        Channel channel = channel(master, "news");
        Article article = new Article();
        article.setChannel(channel);
        article.setSite(master);
        Channel peerChannel = new Channel();
        peerChannel.setSlug("news");
        when(channelService.findBySiteIdAndSlug(2L, "news")).thenReturn(peerChannel);

        List<LangAlternate> list = service.listAlternates(master, channel, article, false);
        LangAlternate alt = list.get(1);
        assertFalse(alt.isExact());
        assertTrue(alt.getFullUrl().contains("en.example.com"), alt.getFullUrl());
        // 当前条目仍为 exact 自引用（hreflang 模板守卫 size>1 不会单独输出它）
        assertTrue(list.get(0).isCurrent());
        assertTrue(list.get(0).isExact());
    }

    /**
     * 非首页、无栏目/文章上下文（搜索、标签页等）：对等首页 exact=false
     */
    @Test
    void otherPagesFallBackToPeerHomeInexact() {
        List<LangAlternate> list = service.listAlternates(master, null, null, false);
        LangAlternate alt = list.get(1);
        assertFalse(alt.isExact());
        assertEquals(peer.getFullUrl(), alt.getFullUrl());
    }

    @Test
    void blankLangPeerIsSkipped() {
        Site blankLang = site(3L, "fr.example.com", null, "fr");
        blankLang.setLang("");
        when(siteService.listByLangGroup(master)).thenReturn(List.of(master, blankLang, peer));
        List<LangAlternate> list = service.listAlternates(master, null, null, true);
        assertEquals(2, list.size());
    }

    @Test
    void listExactAlternatesFiltersAndTreatsNullChannelAsHome() {
        // 首页语义：全部 exact
        assertEquals(2, service.listExactAlternates(master, null).size());
        // 栏目未命中 → 对等条目 exact=false 被过滤，仅剩自引用
        Channel channel = channel(master, "news");
        when(channelService.findBySiteIdAndSlug(2L, "news")).thenReturn(null);
        List<LangAlternate> list = service.listExactAlternates(master, channel);
        assertEquals(1, list.size());
        assertTrue(list.get(0).isCurrent());
    }
}
