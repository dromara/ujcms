package com.ujcms.cms.ext.component;

import com.fasterxml.jackson.databind.JsonNode;
import com.sun.net.httpserver.HttpServer;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.mapper.ArticleMapper;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Constants;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.io.OutputStream;
import java.net.InetSocketAddress;
import java.net.http.HttpClient;
import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import static com.ujcms.cms.ext.component.SearchEnginePushService.ENGINE_BAIDU;
import static com.ujcms.cms.ext.component.SearchEnginePushService.ENGINE_INDEX_NOW;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * {@link SearchEnginePushService} 契约（docs/spec/sitemap/plan.md D13）：本地 HttpServer 充当百度与 IndexNow 端点，
 * 锁定请求格式（百度 text/plain 逐行、IndexNow JSON host/key/keyLocation/urlList）、按站点合并、百度 24 小时去重、
 * 开关关闭时不发请求。
 */
class SearchEnginePushServiceTest {
    private HttpServer server;
    private final List<String> baiduBodies = new CopyOnWriteArrayList<>();
    private final List<String> indexNowBodies = new CopyOnWriteArrayList<>();
    private final List<String> indexNowContentTypes = new CopyOnWriteArrayList<>();
    private String base;

    @BeforeEach
    void startServer() throws IOException {
        server = HttpServer.create(new InetSocketAddress("127.0.0.1", 0), 0);
        server.createContext("/urls", exchange -> {
            baiduBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 200, "{\"remain\":99,\"success\":2,\"not_same_site\":[],\"not_valid\":[]}");
        });
        server.createContext("/indexnow", exchange -> {
            indexNowContentTypes.add(exchange.getRequestHeaders().getFirst("Content-Type"));
            indexNowBodies.add(new String(exchange.getRequestBody().readAllBytes(), StandardCharsets.UTF_8));
            respond(exchange, 202, "");
        });
        server.start();
        base = "http://127.0.0.1:" + server.getAddress().getPort();
    }

    @AfterEach
    void stopServer() {
        server.stop(0);
    }

    private static void respond(com.sun.net.httpserver.HttpExchange exchange, int status, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        exchange.sendResponseHeaders(status, bytes.length == 0 ? -1 : bytes.length);
        if (bytes.length > 0) {
            try (OutputStream out = exchange.getResponseBody()) {
                out.write(bytes);
            }
        }
        exchange.close();
    }

    private Site mockSite(long id, Site.Geo geo) {
        Site site = mock(Site.class);
        when(site.getId()).thenReturn(id);
        when(site.getGeo()).thenReturn(geo);
        when(site.getDynamicFullUrl()).thenReturn("https://www.example.com/ctx");
        when(site.getFullUrl()).thenReturn("https://www.example.com/ctx");
        return site;
    }

    private SearchEnginePushService service(SiteService siteService) {
        return new SearchEnginePushService(siteService, mock(ArticleMapper.class), HttpClient.newHttpClient(),
                base + "/indexnow", false);
    }

    private Site.Geo bothEnabled() {
        Site.Geo geo = new Site.Geo();
        geo.setBaiduPushEnabled(true);
        // 百度接口地址由 Site.Geo 的 @Pattern 限定官方域名；服务本身信任设置值，测试指向本地端点
        geo.setBaiduPushUrl(base + "/urls?site=https://www.example.com&token=abc");
        geo.setIndexNowEnabled(true);
        geo.setIndexNowKey("0123456789abcdef0123456789abcdef");
        return geo;
    }

    @Test
    void flushSendsOneBatchPerSiteAndEngineInTheDocumentedFormats() throws Exception {
        Site site = mockSite(1L, bothEnabled());
        SiteService siteService = mock(SiteService.class);
        when(siteService.select(eq(1L))).thenReturn(site);
        SearchEnginePushService service = service(siteService);

        service.enqueue(1L, "https://www.example.com/ctx/article/1", true, true);
        service.enqueue(1L, "https://www.example.com/ctx/article/2", true, true);
        service.enqueue(1L, "https://www.example.com/ctx/article/2", true, true); // duplicate collapses
        service.flush();

        assertEquals(1, baiduBodies.size(), "one Baidu request per site per flush");
        List<String> lines = List.of(baiduBodies.get(0).split("\n"));
        assertEquals(2, lines.size(), "text/plain, one URL per line: " + lines);
        assertTrue(lines.contains("https://www.example.com/ctx/article/1"));
        assertTrue(lines.contains("https://www.example.com/ctx/article/2"));

        assertEquals(1, indexNowBodies.size(), "one IndexNow request per site per flush");
        assertTrue(indexNowContentTypes.get(0).startsWith("application/json"), indexNowContentTypes.toString());
        JsonNode json = Constants.MAPPER.readTree(indexNowBodies.get(0));
        assertEquals("www.example.com", json.get("host").asText(), "host from the site's dynamic URL");
        assertEquals("0123456789abcdef0123456789abcdef", json.get("key").asText());
        assertEquals("https://www.example.com/ctx/indexnow-0123456789abcdef0123456789abcdef.txt",
                json.get("keyLocation").asText(), "key file served by GeoController under the site path");
        assertEquals(2, json.get("urlList").size());
    }

    @Test
    void baiduIsNotResubmittedWithin24HoursButIndexNowIs() {
        Site site = mockSite(1L, bothEnabled());
        SiteService siteService = mock(SiteService.class);
        when(siteService.select(eq(1L))).thenReturn(site);
        SearchEnginePushService service = service(siteService);

        service.enqueue(1L, "https://www.example.com/ctx/article/1", true, true);
        service.flush();
        service.enqueue(1L, "https://www.example.com/ctx/article/1", true, true);
        service.flush();

        assertEquals(1, baiduBodies.size(), "Baidu quota protection: same URL once per 24 h");
        assertEquals(2, indexNowBodies.size(), "IndexNow accepts repeated update notifications");
    }

    @Test
    void disabledEnginesSendNothing() {
        Site.Geo geo = new Site.Geo();
        geo.setIndexNowEnabled(true); // key missing → treated as disabled
        Site site = mockSite(1L, geo);
        SiteService siteService = mock(SiteService.class);
        when(siteService.select(eq(1L))).thenReturn(site);
        SearchEnginePushService service = service(siteService);

        service.enqueue(1L, "https://www.example.com/ctx/article/1", true, true);
        service.flush();

        assertEquals(0, baiduBodies.size());
        assertEquals(0, indexNowBodies.size());
    }

    @Test
    void publishListenerIgnoresSitesWithoutPushSettings() {
        Site site = mockSite(1L, new Site.Geo());
        SiteService siteService = mock(SiteService.class);
        when(siteService.select(eq(1L))).thenReturn(site);
        ArticleMapper articleMapper = mock(ArticleMapper.class);
        SearchEnginePushService service = new SearchEnginePushService(siteService, articleMapper,
                HttpClient.newHttpClient(), base + "/indexnow", false);
        Article article = mock(Article.class);
        when(article.getSiteId()).thenReturn(1L);
        when(article.getId()).thenReturn(100L);

        service.afterArticlePublish(article);
        service.flush();

        verify(articleMapper, never()).select(eq(100L));
        assertEquals(0, baiduBodies.size() + indexNowBodies.size());
    }

    @Test
    void hostIsDerivedFromTheDynamicUrl() {
        Site site = mock(Site.class);
        when(site.getDynamicFullUrl()).thenReturn("http://localhost:8080/en");
        assertEquals("localhost", SearchEnginePushService.host(site));
        when(site.getDynamicFullUrl()).thenReturn(null);
        assertEquals(null, SearchEnginePushService.host(site));
        assertTrue(SearchEnginePushService.isBaiduSuccess("{\"remain\":1,\"success\":1}"));
        assertTrue(!SearchEnginePushService.isBaiduSuccess("{\"error\":401,\"message\":\"token is not valid\"}"));
    }

    @Test
    void pushTestSubmitsTheHomepageAndRecordsTheLastResult() throws Exception {
        Site site = mockSite(1L, bothEnabled());
        SearchEnginePushService service = service(mock(SiteService.class));
        assertNull(service.lastResult(1L, ENGINE_BAIDU), "nothing pushed yet");

        SearchEnginePushService.PushResult baidu = service.pushTest(site, ENGINE_BAIDU);
        SearchEnginePushService.PushResult indexNow = service.pushTest(site, ENGINE_INDEX_NOW);

        assertTrue(baidu.ok(), baidu.toString());
        assertEquals(200, baidu.status());
        assertEquals(1, baidu.submitted());
        assertEquals("https://www.example.com/ctx", baiduBodies.get(0), "desktop homepage, one line");
        assertTrue(indexNow.ok(), indexNow.toString());
        assertEquals(202, indexNow.status());
        JsonNode json = Constants.MAPPER.readTree(indexNowBodies.get(0));
        assertEquals("https://www.example.com/ctx", json.get("urlList").get(0).asText());
        assertEquals(baidu, service.lastResult(1L, ENGINE_BAIDU));
        assertEquals(indexNow, service.lastResult(1L, ENGINE_INDEX_NOW));
        assertNotNull(baidu.time());
    }

    @Test
    void baiduTestPushIgnoresTheDailyDedupeButStillRegistersIt() {
        Site site = mockSite(1L, bothEnabled());
        SiteService siteService = mock(SiteService.class);
        when(siteService.select(eq(1L))).thenReturn(site);
        SearchEnginePushService service = service(siteService);

        service.pushTest(site, ENGINE_BAIDU);
        service.pushTest(site, ENGINE_BAIDU);
        service.enqueue(1L, "https://www.example.com/ctx", true, false);
        service.flush();

        assertEquals(2, baiduBodies.size(), "two explicit tests sent, the queued duplicate suppressed by the 24 h dedupe");
    }

    @Test
    void transportFailuresAreRetriedThreeTimesThenDropped() {
        Site site = mockSite(1L, bothEnabled());
        SiteService siteService = mock(SiteService.class);
        when(siteService.select(eq(1L))).thenReturn(site);
        // port 1 refuses connections: a transport-level failure, not an engine verdict
        SearchEnginePushService service = new SearchEnginePushService(siteService, mock(ArticleMapper.class),
                HttpClient.newHttpClient(), "http://127.0.0.1:1/indexnow", false);

        service.enqueue(1L, "https://www.example.com/ctx/article/1", false, true);
        service.flush();
        assertEquals(1, service.pendingCount(1L), "first attempt failed → queued for retry");
        SearchEnginePushService.PushResult last = service.lastResult(1L, ENGINE_INDEX_NOW);
        assertNotNull(last);
        assertFalse(last.ok());
        assertEquals(0, last.status(), "status 0 marks a transport failure");
        service.flush();
        assertEquals(1, service.pendingCount(1L), "second attempt failed → still queued");
        service.flush();
        assertEquals(0, service.pendingCount(1L), "third attempt failed → dropped, no endless retry");
    }

    @Test
    void engineRejectionsAreNotRetried() {
        server.createContext("/indexnow-403", exchange -> respond(exchange, 403, ""));
        Site site = mockSite(1L, bothEnabled());
        SiteService siteService = mock(SiteService.class);
        when(siteService.select(eq(1L))).thenReturn(site);
        SearchEnginePushService service = new SearchEnginePushService(siteService, mock(ArticleMapper.class),
                HttpClient.newHttpClient(), base + "/indexnow-403", false);

        service.enqueue(1L, "https://www.example.com/ctx/article/1", false, true);
        service.flush();

        assertEquals(0, service.pendingCount(1L), "403 is a key problem; retrying cannot fix it");
        SearchEnginePushService.PushResult last = service.lastResult(1L, ENGINE_INDEX_NOW);
        assertNotNull(last);
        assertEquals(403, last.status());
        assertFalse(last.ok());
    }
}
