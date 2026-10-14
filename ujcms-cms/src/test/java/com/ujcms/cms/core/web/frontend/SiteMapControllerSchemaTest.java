package com.ujcms.cms.core.web.frontend;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.junit.jupiter.api.Assertions.fail;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.io.ByteArrayInputStream;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.ZoneOffset;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicLong;

import javax.xml.XMLConstants;
import javax.xml.transform.stream.StreamSource;
import javax.xml.validation.Schema;
import javax.xml.validation.SchemaFactory;
import javax.xml.validation.Validator;


import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeAll;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import com.ujcms.cms.core.component.LangAlternateService;
import com.ujcms.cms.core.component.LangAlternateService.LangAlternate;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Group;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.GroupService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.common.web.exception.Http404Exception;

/**
 * {@link SiteMapController} 行为与 XSD 回归（docs/spec/sitemap/plan.md）：每个端点的输出都按 Sitemaps.org 0.9 官方
 * XSD 校验（百度站长平台入库用的同一套 schema），并锁定 2026-09 的决策——SQL 侧可收录过滤 + offset 分页（D1/D2）、
 * 只输出 loc/lastmod（D3）、首页在栏目文件之首（D4）、索引条目带 lastmod（D5）、匿名不可访问栏目剔除（D6）、
 * 桌面上下文生成（D7）、1 小时缓存 + 弱 ETag / Last-Modified（D8/D9）。
 * <p>
 * XSD 副本在 {@code src/test/resources/sitemap-xsd/}，离线运行。
 */
class SiteMapControllerSchemaTest {

    private static Schema sitemapSchema;
    private static Schema siteindexSchema;
    private static Schema sitemapWithXhtmlSchema;

    @BeforeAll
    static void loadSchemas() throws Exception {
        SchemaFactory factory = SchemaFactory.newInstance(XMLConstants.W3C_XML_SCHEMA_NS_URI);
        factory.setFeature(XMLConstants.FEATURE_SECURE_PROCESSING, true);
        sitemapSchema = factory.newSchema(new StreamSource(loadResource("sitemap-xsd/sitemap.xsd")));
        siteindexSchema = factory.newSchema(new StreamSource(loadResource("sitemap-xsd/siteindex.xsd")));
        // sitemap.xsd 的扩展点是 processContents="strict" 的 xsd:any——校验含 xhtml:link 的输出必须
        // 同时提供 xhtml 命名空间的 schema（本地最小替身，见 xhtml-link.xsd 头注释）
        sitemapWithXhtmlSchema = factory.newSchema(new StreamSource[]{
                new StreamSource(loadResource("sitemap-xsd/xhtml-link.xsd")),
                new StreamSource(loadResource("sitemap-xsd/sitemap-image.xsd")),
                new StreamSource(loadResource("sitemap-xsd/sitemap.xsd"))});
    }

    @AfterEach
    void clearMobileContext() {
        Contexts.clearMobile();
    }

    private static InputStream loadResource(String path) {
        InputStream in = SiteMapControllerSchemaTest.class.getClassLoader().getResourceAsStream(path);
        if (in == null) {
            throw new IllegalStateException("missing test resource: " + path);
        }
        return in;
    }

    private static void assertValid(Schema schema, byte[] xml) {
        Validator v = schema.newValidator();
        try {
            v.validate(new StreamSource(new ByteArrayInputStream(xml)));
        } catch (Exception e) {
            fail("XSD validation failed: " + e.getMessage()
                    + "\n--- XML body ---\n" + new String(xml, StandardCharsets.UTF_8));
        }
    }

    // ------------------------------------------------------------------ fixtures

    private static final AtomicLong IDS = new AtomicLong(1000);
    private static final OffsetDateTime MAY_1 = OffsetDateTime.of(2026, 5, 1, 12, 30, 0, 0, ZoneOffset.UTC);

    private static Site mockSite(long id, String fullUrl) {
        Site site = mock(Site.class);
        when(site.getId()).thenReturn(id);
        when(site.getFullUrl()).thenReturn(fullUrl);
        return site;
    }

    private static Channel mockChannel(String fullUrl, boolean isLink) {
        Channel c = mock(Channel.class);
        when(c.getId()).thenReturn(IDS.incrementAndGet());
        when(c.isLink()).thenReturn(isLink);
        when(c.getFullUrl()).thenReturn(fullUrl);
        return c;
    }

    private static Article mockArticle(String fullUrl, boolean isNormal, boolean isLink,
                                       OffsetDateTime modified) {
        Article a = mock(Article.class);
        when(a.getId()).thenReturn(IDS.incrementAndGet());
        when(a.isNormal()).thenReturn(isNormal);
        when(a.isLink()).thenReturn(isLink);
        when(a.getFullUrl()).thenReturn(fullUrl);
        when(a.getModified()).thenReturn(modified);
        return a;
    }

    /** 匿名用户组拥有全部浏览权限（默认数据）：不剔除任何栏目 */
    private static GroupService mockGroupService() {
        Group anonymous = mock(Group.class);
        when(anonymous.getId()).thenReturn(Group.ANONYMOUS_ID);
        when(anonymous.getAllAccessPermission()).thenReturn(true);
        GroupService groupService = mock(GroupService.class);
        when(groupService.getAnonymous()).thenReturn(anonymous);
        return groupService;
    }

    private static SiteMapController controller(ChannelService channelService,
                                                ArticleService articleService,
                                                LangAlternateService langAlternateService) {
        return new SiteMapController(channelService, articleService, mockGroupService(), langAlternateService);
    }

    private static SiteMapController controller(ChannelService channelService,
                                                ArticleService articleService) {
        return controller(channelService, articleService, mock(LangAlternateService.class));
    }

    private static String body(MockHttpServletResponse resp) throws Exception {
        return new String(resp.getContentAsByteArray(), StandardCharsets.UTF_8);
    }

    /** 去掉元素间的换行缩进（输出为每元素一行），让相邻性断言不依赖排版 */
    private static String compact(String xml) {
        return xml.replaceAll(">\\s+<", "><").trim();
    }

    private static MockHttpServletRequest get(String path) {
        return new MockHttpServletRequest("GET", path);
    }

    // --------------------------------------------------------------------- index

    @Test
    void sitemapIndexValidatesWithNoExtraArticlePages() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listModifiedForSitemap(eq(1L)))
                .thenReturn(List.of(MAY_1.minusDays(3), MAY_1, MAY_1.minusDays(1)));
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.index(site, get("/sitemap.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(siteindexSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        // 索引条目带 lastmod（D5）：栏目文件 = 全站最新，文章页 = 页内最新
        assertTrue(xml.contains("<loc>https://example.com/sitemap-channel.xml</loc><lastmod>2026-05-01</lastmod>"),
                "index must reference the channel sitemap with the site-wide lastmod");
        assertTrue(xml.contains("<loc>https://example.com/sitemap-article.xml</loc><lastmod>2026-05-01</lastmod>"),
                "index must reference page-1 article sitemap with its lastmod");
        assertFalse(xml.contains("sitemap-article-2.xml"), "3 articles < PAGE_SIZE → no second page");
        String etag = resp.getHeader("ETag");
        assertNotNull(etag, "controller emits an ETag");
        assertTrue(etag.startsWith("W/\""), "weak ETag so Tomcat may gzip the body (D9): " + etag);
        assertNotNull(resp.getHeader("Last-Modified"), "Last-Modified derived from the newest entry");
        assertEquals("application/xml;charset=UTF-8", resp.getContentType());
    }

    @Test
    void sitemapIndexValidatesWithMultipleArticlePages() throws Exception {
        // 25 000 可收录文章 → 页 1..3；每页 lastmod 为该页切片的最大值
        Site site = mockSite(1L, "https://example.com");
        int size = SiteMapController.PAGE_SIZE * 2 + 5000;
        List<OffsetDateTime> modified = new ArrayList<>(Collections.nCopies(size, OffsetDateTime.of(2026, 1, 1, 0, 0, 0, 0, ZoneOffset.UTC)));
        modified.set(7, OffsetDateTime.of(2026, 3, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        modified.set(SiteMapController.PAGE_SIZE + 42, OffsetDateTime.of(2026, 4, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        modified.set(size - 1, OffsetDateTime.of(2026, 5, 1, 0, 0, 0, 0, ZoneOffset.UTC));
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listModifiedForSitemap(eq(1L))).thenReturn(modified);
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.index(site, get("/sitemap.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(siteindexSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("<loc>https://example.com/sitemap-channel.xml</loc><lastmod>2026-05-01</lastmod>"));
        assertTrue(xml.contains("<loc>https://example.com/sitemap-article.xml</loc><lastmod>2026-03-01</lastmod>"),
                "page-1 lastmod is the max of its own slice");
        assertTrue(xml.contains("<loc>https://example.com/sitemap-article-2.xml</loc><lastmod>2026-04-01</lastmod>"));
        assertTrue(xml.contains("<loc>https://example.com/sitemap-article-3.xml</loc><lastmod>2026-05-01</lastmod>"));
        assertFalse(xml.contains("sitemap-article-4.xml"), "3 pages exactly");
    }

    // ------------------------------------------------------------------- channel

    @Test
    void sitemapChannelListsHomepageFirstAndDropsLinkChannels() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        Channel real1 = mockChannel("https://example.com/news", false);
        Channel real2 = mockChannel("https://example.com/about", false);
        Channel external = mockChannel("https://other.example.com/redirect", true);
        when(real2.getModified()).thenReturn(MAY_1.minusDays(10));

        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(eq(1L))).thenReturn(List.of(real1, external, real2));
        ArticleService articleService = mock(ArticleService.class);
        Map<Long, OffsetDateTime> lastmods = Map.of(real1.getId(), MAY_1, real2.getId(), MAY_1.minusDays(30));
        when(articleService.listChannelModifiedForSitemap(eq(1L))).thenReturn(lastmods);
        SiteMapController controller = controller(channelService, articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.channel(site, get("/sitemap-channel.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        int home = xml.indexOf("<loc>https://example.com</loc><lastmod>2026-05-01</lastmod>");
        assertTrue(home > 0, "homepage first, lastmod = newest article site-wide (D4)");
        assertTrue(home < xml.indexOf("https://example.com/news"), "homepage precedes channels");
        assertTrue(xml.contains("<loc>https://example.com/news</loc><lastmod>2026-05-01</lastmod>"),
                "channel lastmod = newest article in its subtree (D3)");
        assertTrue(xml.contains("<loc>https://example.com/about</loc><lastmod>2026-04-21</lastmod>"),
                "channel lastmod takes the later of article max and the channel's own modified");
        assertFalse(xml.contains("other.example.com/redirect"), "isLink() channels must be excluded");
        assertFalse(xml.contains("<changefreq>"), "changefreq dropped — Google/Bing ignore it (D3)");
        assertFalse(xml.contains("<priority>"), "priority dropped — Google/Bing ignore it (D3)");
    }

    @Test
    void sitemapChannelValidatesWithEmptyChannelList() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(any())).thenReturn(List.of());
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listChannelModifiedForSitemap(any())).thenReturn(Map.of());
        SiteMapController controller = controller(channelService, articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.channel(site, get("/sitemap-channel.xml"), resp);

        // XSD 要求 urlset 至少一个 url：首页条目保证新站输出合法；无文章时不伪造 lastmod
        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("<url><loc>https://example.com</loc></url>"), "homepage only, no fake lastmod");
        assertNotNull(resp.getHeader("ETag"));
        assertEquals(null, resp.getHeader("Last-Modified"), "no Last-Modified when nothing to base it on");
    }

    @Test
    void sitemapChannelEmptyListWithLinkOnlyChannels() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        Channel ext1 = mockChannel("https://other.example.com/a", true);
        Channel ext2 = mockChannel("https://other.example.com/b", true);
        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(any())).thenReturn(List.of(ext1, ext2));
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listChannelModifiedForSitemap(any())).thenReturn(Map.of());
        SiteMapController controller = controller(channelService, articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.channel(site, get("/sitemap-channel.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("<loc>https://example.com</loc>"), "homepage still listed");
        assertFalse(xml.contains("other.example.com"), "external link channels must not surface");
    }

    @Test
    void sitemapChannelExcludesChannelsAnonymousCannotOpen() throws Exception {
        // 匿名用户组无全部浏览权限，仅授权 news：members 栏目页对爬虫回 401，不进 sitemap（D6）
        Site site = mockSite(1L, "https://example.com");
        Channel news = mockChannel("https://example.com/news", false);
        Channel members = mockChannel("https://example.com/members", false);
        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(eq(1L))).thenReturn(List.of(news, members));
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listChannelModifiedForSitemap(any())).thenReturn(Map.of());
        Group anonymous = mock(Group.class);
        when(anonymous.getId()).thenReturn(Group.ANONYMOUS_ID);
        when(anonymous.getAllAccessPermission()).thenReturn(false);
        GroupService groupService = mock(GroupService.class);
        when(groupService.getAnonymous()).thenReturn(anonymous);
        List<Long> allowed = List.of(news.getId());
        when(groupService.listAccessPermissions(eq(Group.ANONYMOUS_ID), eq(1L))).thenReturn(allowed);
        SiteMapController controller = new SiteMapController(channelService, articleService, groupService,
                mock(LangAlternateService.class));

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.channel(site, get("/sitemap-channel.xml"), resp);

        String xml = compact(body(resp));
        assertValid(sitemapSchema, resp.getContentAsByteArray());
        assertTrue(xml.contains("https://example.com/news"));
        assertFalse(xml.contains("https://example.com/members"), "login-gated channel excluded");
    }

    // ------------------------------------------------------------- article p.1

    @Test
    void sitemapArticleValidatesPageOne() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        Article a1 = mockArticle("https://example.com/article/100", true, false, MAY_1);
        Article a2 = mockArticle("https://example.com/article/101", true, false, null);
        Article external = mockArticle("https://other.example.com/x", true, true, MAY_1);
        Article draft = mockArticle("https://example.com/article/102", false, false, MAY_1);

        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listForSitemap(eq(1L), eq(1), eq(SiteMapController.PAGE_SIZE)))
                .thenReturn(List.of(a1, external, draft, a2));
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.article(site, get("/sitemap-article.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("<url><loc>https://example.com/article/100</loc><lastmod>2026-05-01</lastmod></url>"),
                "published article with ISO_LOCAL_DATE lastmod");
        assertTrue(xml.contains("<url><loc>https://example.com/article/101</loc></url>"),
                "article without modified date omits lastmod rather than faking it");
        assertFalse(xml.contains("other.example.com/x"), "link articles excluded (belt-and-braces over SQL)");
        assertFalse(xml.contains("/article/102"), "non-normal articles excluded (belt-and-braces over SQL)");
        assertFalse(xml.contains("<changefreq>"));
        assertFalse(xml.contains("<priority>"));
        assertEquals("Fri, 01 May 2026 12:30:00 GMT", resp.getHeader("Last-Modified"),
                "Last-Modified = newest entry on the page");
    }

    @Test
    void sitemapArticlePageOneFallsBackToHomepageOnEmptySite() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listForSitemap(eq(1L), anyInt(), anyInt())).thenReturn(List.of());
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.article(site, get("/sitemap-article.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapSchema, body);
        assertTrue(compact(new String(body, StandardCharsets.UTF_8)).contains("<url><loc>https://example.com</loc></url>"),
                "XSD needs >= 1 url: greenfield sites fall back to the homepage");
    }

    // -------------------------------------------------------- article paged > 1

    @Test
    void sitemapArticlePagedUsesOffsetPaging() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        OffsetDateTime modified = OffsetDateTime.of(2026, 4, 15, 9, 0, 0, 0, ZoneOffset.UTC);
        Article a1 = mockArticle("https://example.com/article/3000", true, false, modified);
        Article a2 = mockArticle("https://example.com/article/3001", true, false, modified);
        Article external = mockArticle("https://other.example.com/r", true, true, modified);

        ArticleService articleService = mock(ArticleService.class);
        // 页 N 直接按 (page, PAGE_SIZE) offset 分页（D2）——不再经 statForSitemap 求边界 id
        when(articleService.listForSitemap(eq(1L), eq(2), eq(SiteMapController.PAGE_SIZE)))
                .thenReturn(List.of(a1, external, a2));
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.article(site, 2, get("/sitemap-article-2.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("/article/3000"), "page-2 articles must be listed");
        assertTrue(xml.contains("/article/3001"));
        assertFalse(xml.contains("other.example.com/r"), "isLink() filter applies on page-2 too");
        assertTrue(xml.contains("<lastmod>2026-04-15</lastmod>"), "page-N emits lastmod like page-1");
        assertFalse(xml.contains("<priority>"), "no priority on any page");
    }

    @Test
    void sitemapArticlePageBelowTwoIs404WithoutQuerying() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        ArticleService articleService = mock(ArticleService.class);
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        // 第 1 页地址是 /sitemap-article.xml；-1 / -0 不是已发布地址，404 而非校验异常（曾为 @Min(2) → 500 + 堆栈）
        for (int page : new int[]{1, 0}) {
            assertThrows(Http404Exception.class, () -> controller.article(site, page,
                    get("/sitemap-article-" + page + ".xml"), new MockHttpServletResponse()));
        }
        verify(articleService, never()).listForSitemap(any(), anyInt(), anyInt());
    }

    @Test
    void sitemapOutputIsOneElementPerLine() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        ArticleService articleService = mock(ArticleService.class);
        List<Article> articles = List.of(mockArticle("https://example.com/article/100", true, false, MAY_1));
        when(articleService.listForSitemap(eq(1L), eq(1), anyInt())).thenReturn(articles);
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.article(site, get("/sitemap-article.xml"), resp);
        String raw = body(resp);
        // Chromium 遇 xhtml 命名空间不启用树视图而直接渲染 DOM：换行缩进让页面与 view-source 都可读
        assertTrue(raw.startsWith("<?xml version=\"1.0\" encoding=\"UTF-8\"?>\n<urlset "), raw);
        assertTrue(raw.contains("\n  <url>\n    <loc>https://example.com/article/100</loc>\n    <lastmod>2026-05-01</lastmod>\n  </url>\n"), raw);
        assertTrue(raw.endsWith("</urlset>\n"), raw);
    }

    @Test
    void sitemapArticlePageBeyondRangeIs404AndNotCached() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listForSitemap(eq(1L), eq(9), anyInt())).thenReturn(List.of());
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        assertThrows(Http404Exception.class,
                () -> controller.article(site, 9, get("/sitemap-article-9.xml"), new MockHttpServletResponse()));
        assertThrows(Http404Exception.class,
                () -> controller.article(site, 9, get("/sitemap-article-9.xml"), new MockHttpServletResponse()));
        verify(articleService, times(2)).listForSitemap(eq(1L), eq(9), anyInt());
    }

    // ------------------------------------------------------- cache + conditional

    @Test
    void secondRequestIsServedFromCacheAndIfNoneMatchYields304() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        Channel news = mockChannel("https://example.com/news", false);
        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(eq(1L))).thenReturn(List.of(news));
        ArticleService articleService = mock(ArticleService.class);
        Map<Long, OffsetDateTime> lastmods = Map.of(news.getId(), MAY_1);
        when(articleService.listChannelModifiedForSitemap(eq(1L))).thenReturn(lastmods);
        SiteMapController controller = controller(channelService, articleService);

        MockHttpServletResponse first = new MockHttpServletResponse();
        controller.channel(site, get("/sitemap-channel.xml"), first);
        String etag = first.getHeader("ETag");
        assertNotNull(etag);

        MockHttpServletRequest conditional = get("/sitemap-channel.xml");
        conditional.addHeader("If-None-Match", etag);
        MockHttpServletResponse second = new MockHttpServletResponse();
        controller.channel(site, conditional, second);

        assertEquals(304, second.getStatus(), "weak ETag must satisfy If-None-Match");
        assertEquals(0, second.getContentAsByteArray().length, "304 carries no body");
        assertEquals(etag, second.getHeader("ETag"));
        verify(channelService, times(1)).listByChannelForSitemap(eq(1L));
        verify(articleService, times(1)).listChannelModifiedForSitemap(eq(1L));

        MockHttpServletRequest since = get("/sitemap-channel.xml");
        since.addHeader("If-Modified-Since", first.getHeader("Last-Modified"));
        MockHttpServletResponse third = new MockHttpServletResponse();
        controller.channel(site, since, third);
        assertEquals(304, third.getStatus(), "Last-Modified must satisfy If-Modified-Since");
    }

    @Test
    void generationRunsInDesktopContextAndRestoresTheCallerContext() throws Exception {
        // 静态化站点的 URL 组装读 Contexts.isMobile()：缓存正文不得随爬虫 UA 变化（D7）
        Site site = mockSite(1L, "https://example.com");
        AtomicBoolean seenMobile = new AtomicBoolean(true);
        when(site.getFullUrl()).thenAnswer(inv -> {
            seenMobile.set(Contexts.isMobile());
            return "https://example.com";
        });
        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(any())).thenReturn(List.of());
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listChannelModifiedForSitemap(any())).thenReturn(Map.of());
        SiteMapController controller = controller(channelService, articleService);

        Contexts.setMobile(true);
        controller.channel(site, get("/sitemap-channel.xml"), new MockHttpServletResponse());

        assertFalse(seenMobile.get(), "URLs are assembled in desktop context");
        assertTrue(Contexts.isMobile(), "caller's mobile flag restored afterwards");
    }

    // -------------------------------------------- xhtml:link language alternates (P3)

    @Test
    void sitemapChannelEmitsXhtmlLinkAlternatesForLanguageGroups() throws Exception {
        Site site = mockSite(1L, "https://www.example.com");
        when(site.getLangTag()).thenReturn("zh-CN");
        Site peer = mockSite(2L, "https://www.example.com/en");
        when(peer.getLangTag()).thenReturn("en");
        Channel channel = mockChannel("https://www.example.com/channel/news", false);

        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(eq(1L))).thenReturn(List.of(channel));
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listChannelModifiedForSitemap(any())).thenReturn(Map.of());
        LangAlternateService langAlternateService = mock(LangAlternateService.class);
        // LangAlternate 构造器会调用 site.getLangTag()（mock）——须在 when(...) 之外先构造，避免未完成打桩
        List<LangAlternate> alternates = List.of(
                new LangAlternate(site, "/channel/news", "https://www.example.com/channel/news", true, true),
                new LangAlternate(peer, "/en/channel/news", "https://www.example.com/en/channel/news", true, false));
        when(langAlternateService.listExactAlternates(eq(site), any(Channel.class))).thenReturn(alternates);
        when(langAlternateService.listExactAlternates(eq(site), nullable(Channel.class))).thenReturn(alternates);
        SiteMapController controller = controller(channelService, articleService, langAlternateService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.channel(site, get("/sitemap-channel.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        // strict-any 扩展点：连同 xhtml 最小 schema 一起校验
        assertValid(sitemapWithXhtmlSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("xmlns:xhtml=\"http://www.w3.org/1999/xhtml\""),
                "urlset must declare the xhtml namespace");
        assertTrue(xml.contains("<xhtml:link rel=\"alternate\" hreflang=\"zh-CN\""
                        + " href=\"https://www.example.com/channel/news\"/>"),
                "self-referencing alternate required by Google");
        assertTrue(xml.contains("<xhtml:link rel=\"alternate\" hreflang=\"en\""
                        + " href=\"https://www.example.com/en/channel/news\"/>"),
                "peer alternate must be emitted");
        assertTrue(xml.contains("<xhtml:link rel=\"alternate\" hreflang=\"x-default\""
                        + " href=\"https://www.example.com/channel/news\"/>"),
                "x-default = first member in group order, same rule as the theme head (D11)");
    }

    @Test
    void sitemapChannelOmitsAlternatesForUngroupedSites() throws Exception {
        // 未分组站点：listExactAlternates 只返回自引用一条 → 不写 xhtml:link
        Site site = mockSite(1L, "https://example.com");
        when(site.getLangTag()).thenReturn("zh-CN");
        Channel channel = mockChannel("https://example.com/news", false);
        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(eq(1L))).thenReturn(List.of(channel));
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listChannelModifiedForSitemap(any())).thenReturn(Map.of());
        LangAlternateService langAlternateService = mock(LangAlternateService.class);
        List<LangAlternate> selfOnly = List.of(
                new LangAlternate(site, "/news", "https://example.com/news", true, true));
        when(langAlternateService.listExactAlternates(eq(site), nullable(Channel.class))).thenReturn(selfOnly);
        SiteMapController controller = controller(channelService, articleService, langAlternateService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.channel(site, get("/sitemap-channel.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapSchema, body);
        assertFalse(new String(body, StandardCharsets.UTF_8).contains("<xhtml:link"),
                "a lone self-reference is meaningless — ungrouped sites emit no alternates, no x-default either");
    }

    // ---------------------------------------- article xhtml:link via translation links (P5)

    @Test
    void sitemapArticleEmitsXhtmlLinkForLinkedTranslations() throws Exception {
        Site site = mockSite(1L, "https://www.example.com");
        when(site.getLangTag()).thenReturn("zh-CN");
        Site peer = mockSite(2L, "https://www.example.com/en");
        when(peer.getLangTag()).thenReturn("en");
        OffsetDateTime modified = OffsetDateTime.of(2026, 7, 1, 8, 0, 0, 0, ZoneOffset.UTC);
        Article linked = mockArticle("https://www.example.com/article/100", true, false, modified);
        Article unlinked = mockArticle("https://www.example.com/article/101", true, false, modified);

        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listForSitemap(eq(1L), eq(1), anyInt())).thenReturn(List.of(linked, unlinked));
        LangAlternateService langAlternateService = mock(LangAlternateService.class);
        // LangAlternate 构造器调用 site.getLangTag()（mock）——在 when(...) 之外先构造
        List<LangAlternate> alternates = List.of(
                new LangAlternate(site, "/article/100", "https://www.example.com/article/100", true, true),
                new LangAlternate(peer, "/en/article/200", "https://www.example.com/en/article/200", true, false));
        Map<Long, List<LangAlternate>> byArticle = Map.of(linked.getId(), alternates);
        when(langAlternateService.listExactAlternatesByArticles(eq(site), anyList())).thenReturn(byArticle);
        SiteMapController controller = controller(mock(ChannelService.class), articleService, langAlternateService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.article(site, get("/sitemap-article.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapWithXhtmlSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("<xhtml:link rel=\"alternate\" hreflang=\"en\""
                        + " href=\"https://www.example.com/en/article/200\"/>"),
                "linked article must carry its peer translation alternate");
        assertTrue(xml.contains("<xhtml:link rel=\"alternate\" hreflang=\"zh-CN\""
                        + " href=\"https://www.example.com/article/100\"/>"),
                "linked article must carry the self-referencing alternate");
        assertTrue(xml.contains("<xhtml:link rel=\"alternate\" hreflang=\"x-default\""
                        + " href=\"https://www.example.com/article/100\"/>"),
                "article entries carry x-default too");
        // 未链接文章保持无 xhtml:link：检查 101 的 <url> 块内不含 xhtml
        int unlinkedStart = xml.indexOf("https://www.example.com/article/101");
        int unlinkedEnd = xml.indexOf("</url>", unlinkedStart);
        assertFalse(xml.substring(unlinkedStart, unlinkedEnd).contains("<xhtml:link"),
                "unlinked article must keep its alternate-free output");
    }

    // ------------------------------------------------------ image extension (D12)

    @Test
    void sitemapArticleEmitsImageExtensionForThumbnails() throws Exception {
        Site site = mockSite(1L, "https://example.com");
        when(site.getDynamicFullUrl()).thenReturn("https://example.com:8443/ctx/en");
        Article rootRelative = mockArticle("https://example.com/article/1", true, false, MAY_1);
        when(rootRelative.getImage()).thenReturn("/uploads/2026/a.jpg");
        Article absolute = mockArticle("https://example.com/article/2", true, false, MAY_1);
        when(absolute.getImage()).thenReturn("https://cdn.example.com/b.jpg");
        Article protocolRelative = mockArticle("https://example.com/article/3", true, false, MAY_1);
        when(protocolRelative.getImage()).thenReturn("//cdn.example.com/c.jpg");
        Article relative = mockArticle("https://example.com/article/4", true, false, MAY_1);
        when(relative.getImage()).thenReturn("img/d.jpg");
        Article none = mockArticle("https://example.com/article/5", true, false, MAY_1);

        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listForSitemap(eq(1L), eq(1), anyInt()))
                .thenReturn(List.of(rootRelative, absolute, protocolRelative, relative, none));
        SiteMapController controller = controller(mock(ChannelService.class), articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.article(site, get("/sitemap-article.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapWithXhtmlSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("xmlns:image=\"http://www.google.com/schemas/sitemap-image/1.1\""));
        assertTrue(xml.contains("<image:image><image:loc>https://example.com:8443/uploads/2026/a.jpg</image:loc></image:image>"),
                "root-relative upload path gets the site origin (scheme://host:port, no context path / sub-dir)");
        assertTrue(xml.contains("<image:loc>https://cdn.example.com/b.jpg</image:loc>"), "absolute kept as is");
        assertTrue(xml.contains("<image:loc>https://cdn.example.com/c.jpg</image:loc>"), "protocol-relative gets the scheme");
        assertFalse(xml.contains("img/d.jpg"), "relative path cannot be resolved → no image element");
        int noneStart = xml.indexOf("https://example.com/article/5");
        assertFalse(xml.substring(noneStart, xml.indexOf("</url>", noneStart)).contains("<image:"),
                "articles without a thumbnail carry no image element");
    }

    @Test
    void absoluteUrlHelperIsNullSafe() {
        Site site = mockSite(1L, "https://example.com");
        assertEquals(null, SiteMapController.absoluteUrl(site, null));
        assertEquals(null, SiteMapController.absoluteUrl(site, "  "));
        assertEquals(null, SiteMapController.absoluteUrl(site, "/uploads/x.jpg"), "no dynamic URL → cannot resolve");
        when(site.getDynamicFullUrl()).thenReturn("not a url");
        assertEquals(null, SiteMapController.absoluteUrl(site, "/uploads/x.jpg"));
        assertEquals("https://a/b.jpg", SiteMapController.absoluteUrl(site, "https://a/b.jpg"));
    }

    // -------------------------------------------------------- XML escaping in <loc>

    @Test
    void sitemapEscapesAmpersandInLoc() throws Exception {
        // UJCMS 规范 URL 今日不含查询串；锁定 loc 经 XML 转义，将来引入 & 时不会输出畸形 XML
        Site site = mockSite(1L, "https://example.com");
        Channel withAmpersand = mockChannel("https://example.com/c?a=1&b=2", false);
        ChannelService channelService = mock(ChannelService.class);
        when(channelService.listByChannelForSitemap(any())).thenReturn(List.of(withAmpersand));
        ArticleService articleService = mock(ArticleService.class);
        when(articleService.listChannelModifiedForSitemap(any())).thenReturn(Map.of());
        SiteMapController controller = controller(channelService, articleService);

        MockHttpServletResponse resp = new MockHttpServletResponse();
        controller.channel(site, get("/sitemap-channel.xml"), resp);

        byte[] body = resp.getContentAsByteArray();
        assertValid(sitemapSchema, body);
        String xml = compact(new String(body, StandardCharsets.UTF_8));
        assertTrue(xml.contains("https://example.com/c?a=1&amp;b=2"), "ampersand in loc must be XML-escaped");
        assertFalse(xml.contains("a=1&b=2"), "raw ampersand must not survive into the response");
    }
}
