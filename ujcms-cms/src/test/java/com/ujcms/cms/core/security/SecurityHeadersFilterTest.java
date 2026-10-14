package com.ujcms.cms.core.security;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertNotEquals;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.security.Principal;
import java.util.Base64;

import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockFilterChain;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

/**
 * Unit tests for {@link SecurityHeadersFilter}.
 *
 * <p>Covers the route → policy classification table documented on the filter, plus the
 * per-request CSP nonce contract (random per request, base64 16 bytes, exposed under the
 * documented request attribute, embedded in the CSP header value).
 *
 * <p>Phase 6 follow-up: closes Open items C-1 ({@code Cache-Control}) + C-2 ({@code
 * Content-Security-Policy}).
 */
class SecurityHeadersFilterTest {

    private static final String CACHE_CONTROL = "Cache-Control";
    private static final String CSP = "Content-Security-Policy";

    private MockHttpServletResponse runFilter(MockHttpServletRequest req) throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(req, resp, new MockFilterChain());
        return resp;
    }

    // ----------------------------------------------------------------- public bucket

    @Test
    void publicHomeGet_setsFiveMinuteCache_andNoCsp() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        MockHttpServletResponse resp = runFilter(req);

        assertEquals("public, max-age=300, must-revalidate", resp.getHeader(CACHE_CONTROL));
        // CSP is intentionally NOT emitted on public buckets — see SecurityHeadersFilter Javadoc.
        // The nonce request attribute is still published so templates with nonce="{{csp_nonce}}"
        // continue to render correctly; the browser ignores a nonce attribute when no CSP is set.
        assertNull(resp.getHeader(CSP), "public routes no longer ship CSP");
        assertNotNull(req.getAttribute(SecurityHeadersFilter.CSP_NONCE_ATTRIBUTE),
                "nonce attribute still published for template compatibility");
    }

    @Test
    void publicChannelGet_setsFiveMinuteCache() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/channel/news"));
        assertEquals("public, max-age=300, must-revalidate", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void publicArticleGet_setsFiveMinuteCache() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/article/3"));
        assertEquals("public, max-age=300, must-revalidate", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void searchRoot_isPublicCacheable() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/search"));
        assertEquals("public, max-age=300, must-revalidate", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void publicTagGet_setsFiveMinuteCache() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/tag/9"));
        assertEquals("public, max-age=300, must-revalidate", resp.getHeader(CACHE_CONTROL));
    }

    // ------------------------------------------------------- long-cache (sitemap / RSS)

    @Test
    void sitemapGet_setsOneHourCache_andNoCsp() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/sitemap.xml"));
        assertEquals("public, max-age=3600, must-revalidate", resp.getHeader(CACHE_CONTROL));
        assertNull(resp.getHeader(CSP), "public buckets no longer ship CSP");
    }

    @Test
    void sitemapArticlePagedGet_setsOneHourCache() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/sitemap-article-2.xml"));
        assertEquals("public, max-age=3600, must-revalidate", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void rssGet_setsOneHourCache() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/rss"));
        assertEquals("public, max-age=3600, must-revalidate", resp.getHeader(CACHE_CONTROL));
    }

    // -------------------------------------------------------- backoffice (looser CSP)

    @Test
    void subDirSiteSitemapAndRss_setOneHourCache() throws Exception {
        // 子目录站点（/en/...）：公共桶前缀按去掉首段后的路径匹配（plan.md D14）
        for (String path : new String[]{"/en/sitemap.xml", "/en/sitemap-article-2.xml", "/zh-cn/rss",
                "/robots.txt", "/en/robots.txt", "/llms.txt", "/en/llms.txt"}) {
            MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", path));
            assertEquals("public, max-age=3600, must-revalidate", resp.getHeader(CACHE_CONTROL), path);
        }
    }

    @Test
    void subDirSitePages_setFiveMinuteCache() throws Exception {
        for (String path : new String[]{"/en/channel/news", "/en/article/100", "/en/tag/1", "/en/search"}) {
            MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", path));
            assertEquals("public, max-age=300, must-revalidate", resp.getHeader(CACHE_CONTROL), path);
        }
    }

    @Test
    void subDirStripping_neverReachesPrivatePrefixes() throws Exception {
        // 私有桶先判定：/cp/、/api/ 下的同名路径不因首段剥离而进入公共桶；裸首段不是子目录首页
        assertEquals("no-store", runFilter(new MockHttpServletRequest("GET", "/api/sitemap.xml")).getHeader(CACHE_CONTROL));
        assertEquals("no-store", runFilter(new MockHttpServletRequest("GET", "/login/sitemap.xml")).getHeader(CACHE_CONTROL));
        assertEquals("no-store", runFilter(new MockHttpServletRequest("GET", "/en")).getHeader(CACHE_CONTROL));
        assertEquals("no-store", runFilter(new MockHttpServletRequest("GET", "/en/")).getHeader(CACHE_CONTROL));
        assertEquals("/sitemap.xml", SecurityHeadersFilter.stripSubDir("/en/sitemap.xml"));
        assertEquals("/sitemap.xml", SecurityHeadersFilter.stripSubDir("/sitemap.xml"));
        assertEquals("/", SecurityHeadersFilter.stripSubDir("/"));
    }

    @Test
    void cpRoute_setsNoStore_andLooserCsp() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/cp/index.html"));

        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
        String csp = resp.getHeader(CSP);
        assertNotNull(csp, "backoffice gets a CSP, just looser than the public one");
        assertTrue(csp.contains("'unsafe-inline'"), "ElementPlus needs unsafe-inline");
        assertTrue(csp.contains("'unsafe-eval'"), "ElementPlus runtime template compile needs unsafe-eval");
        assertTrue(csp.contains("font-src 'self' data:"),
                "bpmn-js palette icon font is base64 data: URIs; without font-src data: the flow designer shows tofu");
    }

    // -------------------------------------------------------------- private (no CSP)

    @Test
    void apiRoute_setsNoStore_noCsp() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/api/article/3"));

        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
        assertNull(resp.getHeader(CSP), "/api responses do not set CSP — JSON, not HTML");
    }

    @Test
    void loginRoute_setsNoStore() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/login"));
        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void settingsRoute_setsNoStore() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/settings/profile"));
        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void postRequest_neverCaches_evenOnPublicRoute() throws Exception {
        // POST /article/3/something would never be cacheable per HTTP semantics.
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("POST", "/article/3"));
        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void previewDraftRequest_setsNoStore_evenOnPublicRoute() throws Exception {
        // 草稿预览（Phase 7 / §7.0）：带 _preview=draft 的首页 GET 绝不能落入可缓存桶——草稿内容
        // 含未发布卡片，公共缓存命中会泄漏给匿名用户。本过滤器先于安全链运行，故按查询参数判定，
        // 不依赖此时尚未置上的认证。
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_preview", "draft");
        MockHttpServletResponse resp = runFilter(req);
        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void themePreviewRequest_setsNoStore_evenOnPublicRoute() throws Exception {
        // 主题预览覆盖（P2-1）：带 _theme 的首页 GET 绝不能落入可缓存桶——候选主题渲染被公共缓存
        // 命中会把未激活主题回放给匿名访客。同 _preview=draft，按查询参数判定。
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_theme", "candidate");
        MockHttpServletResponse resp = runFilter(req);
        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
    }

    @Test
    void authenticatedPublicRoute_setsNoStore() throws Exception {
        // A logged-in user hitting the homepage gets no public cache: the page may include
        // personalised chrome (member greeting, logged-in nav) so a shared CDN MUST NOT cache it.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        Principal user = mock(Principal.class);
        when(user.getName()).thenReturn("admin");
        req.setUserPrincipal(user);

        MockHttpServletResponse resp = runFilter(req);
        assertEquals("no-store", resp.getHeader(CACHE_CONTROL));
    }

    // ----------------------------------------------------------- X-Frame-Options

    private static final String XFO = "X-Frame-Options";

    @Test
    void publicRoute_setsFrameOptionsDeny() throws Exception {
        // 默认对所有响应 DENY，禁止任何内嵌（点击劫持防护）。
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/"));
        assertEquals("DENY", resp.getHeader(XFO));
    }

    @Test
    void apiRoute_setsFrameOptionsDeny() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/api/article/3"));
        assertEquals("DENY", resp.getHeader(XFO));
    }

    @Test
    void backofficeRoute_setsFrameOptionsDeny() throws Exception {
        MockHttpServletResponse resp = runFilter(new MockHttpServletRequest("GET", "/cp/index.html"));
        assertEquals("DENY", resp.getHeader(XFO));
    }

    @Test
    void draftPreview_setsFrameOptionsSameOrigin() throws Exception {
        // 草稿预览页（?_preview=draft）降为 SAMEORIGIN，令控制台「实时预览」可同源内嵌前台页面；
        // 仍只允许同源，跨源点击劫持依旧被拦截。
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        req.setParameter("_preview", "draft");
        MockHttpServletResponse resp = runFilter(req);
        assertEquals("SAMEORIGIN", resp.getHeader(XFO));
    }

    // --------------------------------------------------------------- nonce contract

    @Test
    void nonceIsBase64NoPadding_andSixteenBytes() throws Exception {
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/");
        runFilter(req);

        String nonce = (String) req.getAttribute(SecurityHeadersFilter.CSP_NONCE_ATTRIBUTE);
        assertNotNull(nonce, "filter must publish nonce as request attribute");
        // Base64-no-padding of 16 bytes is exactly 22 characters (ceil(16*4/3) without trailing =).
        assertEquals(22, nonce.length(),
                "16-byte base64-no-padding nonce should be 22 chars; got: " + nonce);
        // Must round-trip through standard base64 (with padding restored).
        byte[] decoded = Base64.getDecoder().decode(nonce + "==");
        assertEquals(16, decoded.length);
    }

    @Test
    void successiveRequestsGetDifferentNonces() throws Exception {
        MockHttpServletRequest req1 = new MockHttpServletRequest("GET", "/");
        MockHttpServletRequest req2 = new MockHttpServletRequest("GET", "/");
        SecurityHeadersFilter filter = new SecurityHeadersFilter();
        filter.doFilter(req1, new MockHttpServletResponse(), new MockFilterChain());
        filter.doFilter(req2, new MockHttpServletResponse(), new MockFilterChain());

        String n1 = (String) req1.getAttribute(SecurityHeadersFilter.CSP_NONCE_ATTRIBUTE);
        String n2 = (String) req2.getAttribute(SecurityHeadersFilter.CSP_NONCE_ATTRIBUTE);
        assertNotNull(n1);
        assertNotNull(n2);
        assertNotEquals(n1, n2, "each request must get a fresh nonce");
    }

    @Test
    void privateRoute_stillSetsNonceAttribute_butNoCspHeader() throws Exception {
        // The nonce attribute is set unconditionally so a private route that happens to render a
        // template page (rare but possible — e.g. a member page rendered via FreeMarker) still has
        // a nonce available even though the filter doesn't ship a CSP header for that bucket.
        MockHttpServletRequest req = new MockHttpServletRequest("GET", "/api/env/csrf");
        MockHttpServletResponse resp = runFilter(req);

        assertNotNull(req.getAttribute(SecurityHeadersFilter.CSP_NONCE_ATTRIBUTE),
                "nonce attribute is published on every request");
        assertNull(resp.getHeader(CSP), "/api routes never get a CSP header");
    }

    // ----------------------------------------------------------------- HSTS (Phase 4 / G6)

    private static final String HSTS = "Strict-Transport-Security";

    @Test
    void hstsDisabledByDefault() throws Exception {
        MockHttpServletResponse resp = new MockHttpServletResponse();
        new SecurityHeadersFilter().doFilter(new MockHttpServletRequest("GET", "/"), resp, new MockFilterChain());
        assertNull(resp.getHeader(HSTS), "HSTS must be off unless https-enabled");
    }

    @Test
    void hstsEmittedWhenEnabled_onEveryBucket() throws Exception {
        for (String uri : new String[] {"/", "/cp/index", "/api/backend/core/user", "/article/1"}) {
            MockHttpServletResponse resp = new MockHttpServletResponse();
            new SecurityHeadersFilter(true).doFilter(
                    new MockHttpServletRequest("GET", uri), resp, new MockFilterChain());
            assertEquals("max-age=31536000; includeSubDomains", resp.getHeader(HSTS), "HSTS on " + uri);
        }
    }

    private static void assertNull(Object o, String message) {
        assertTrue(o == null, message);
    }
}
