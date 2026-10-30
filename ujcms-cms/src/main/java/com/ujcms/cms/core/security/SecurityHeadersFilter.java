package com.ujcms.cms.core.security;

import java.io.IOException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.function.Supplier;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import com.ujcms.cms.core.support.UrlConstants;
import org.jspecify.annotations.Nullable;
import org.springframework.web.filter.OncePerRequestFilter;

import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;

/**
 * Per-route Cache-Control + Content-Security-Policy header writer.
 *
 * <p>Replaces Spring Security's blanket {@code Cache-Control: no-store} on the routes a visitor's
 * own browser may cache (homepage, channel listings, articles, tags, member profiles, search) and
 * on the feeds a shared cache may hold (sitemap, RSS, robots.txt, llms.txt).
 *
 * <h2>Pages are browser-private, never shared-cacheable</h2>
 *
 * <p>Every FreeMarker page is rendered per session: the header shows the member menu, the article
 * page decides server-side whether to render the comment box, {@code [#if user??]} appears all over
 * the themes. The page bucket therefore answers {@code private, max-age=300, must-revalidate} plus
 * {@code Vary: Cookie}: the visitor's own browser keeps the page for five minutes and re-fetches it
 * once the session cookie changes at login or logout, while a CDN or reverse-proxy cache that follows
 * the origin never stores it. A {@code public} page in front of such a cache was served to logged-in
 * members as the anonymous rendering (comment box replaced by "please log in"), and whichever request
 * repopulated the edge could leak a member's personalised page to everyone for the TTL. Only the
 * feeds stay {@code public}: their body never depends on the viewer.
 *
 * <p>This filter runs before Spring Security, so the session is not resolved here (the container's
 * {@code getUserPrincipal()} is always {@code null}). The authenticated case is owned by
 * {@link AuthenticatedCacheControlFilter} inside the front-end security chain, which replaces
 * whatever this filter wrote with {@code no-store} once a non-anonymous authentication is on the
 * context. Contract: {@code docs/spec/http-cache/plan.md}.
 *
 * <h2>CSP on public routes is currently DISABLED</h2>
 *
 * <p>The public-bucket strict CSP shipped in V11.1 conflicted with the default theme's
 * use of standard Alpine.js — Alpine evaluates inline {@code x-data} / {@code @click} / etc.
 * expressions via {@code new Function(...)} which is blocked without {@code 'unsafe-eval'}.
 * Migrating every {@code x-data="{...}"} site to the {@code @alpinejs/csp} build's
 * {@code Alpine.data(name, fn)} registration was a substantial refactor for a defence-in-depth
 * layer whose primary value (blocking eval inside already-trusted scripts) was modest given the
 * existing input-sanitisation + output-escaping defences.
 *
 * <p>Realistic XSS vector for UJCMS:
 * <ol>
 *   <li>User-generated content (articles / comments) → goes through OWASP HTML sanitiser.</li>
 *   <li>Reflected URL params → FreeMarker {@code [#escape ?html]}.</li>
 *   <li>Session theft via {@code document.cookie} → mitigated by HttpOnly cookies.</li>
 *   <li>State-changing API calls → CSRF tokens.</li>
 * </ol>
 * The {@code script-src} nonce only mattered when all of the above failed; the residual
 * benefit didn't justify the theme-build constraints. Mainstream Vue/Alpine sites operate
 * without strict CSP for the same reasons.
 *
 * <p>The backoffice ({@code /cp/**}) still ships its CSP because (a) ElementPlus runtime
 * template compile already required {@code 'unsafe-eval'} so the trade-off is asymmetric,
 * (b) the admin surface is a higher-value attack target.
 *
 * <p>The per-request nonce attribute is still generated and exposed under
 * {@link #CSP_NONCE_ATTRIBUTE}. Templates that include {@code nonce="${csp_nonce!''}"} on
 * inline scripts continue to render correctly — without a CSP header the browser ignores the
 * attribute. Leaving the nonce machinery in place means re-enabling strict CSP is a one-line
 * change inside this filter's switch.
 *
 * <h2>Route → policy mapping</h2>
 * <table>
 * <tr><th>Bucket</th><th>Cache-Control</th><th>CSP</th></tr>
 * <tr><td>Anonymous GET to {@code /}, {@code /channel/**}, {@code /article/**},
 *     {@code /tag/**}, {@code /search}, {@code /users/**}</td>
 *     <td>{@code private, max-age=300, must-revalidate} + {@code Vary: Cookie}</td>
 *     <td>none (was strict-nonce; see header note)</td></tr>
 * <tr><td>Authenticated GET to any of the above (form login, remember-me, preview token)</td>
 *     <td>{@code no-store}, written by {@link AuthenticatedCacheControlFilter}</td>
 *     <td>none</td></tr>
 * <tr><td>Public anonymous GET to {@code /sitemap*.xml}, {@code /rss/**}, {@code /robots.txt}, {@code /llms.txt}
 *     (also under a site sub-directory, {@code /en/sitemap.xml})</td>
 *     <td>{@code public, max-age=3600, must-revalidate}</td>
 *     <td>none (was strict-nonce; see header note)</td></tr>
 * <tr><td>Backoffice {@code /cp/**}</td>
 *     <td>{@code no-store}</td>
 *     <td>looser (allows {@code 'unsafe-inline'} + {@code 'unsafe-eval'} for ElementPlus runtime)</td></tr>
 * <tr><td>Anything else (mutating method, {@code /api/**}, member pages, login)</td>
 *     <td>{@code no-store}</td>
 *     <td>none</td></tr>
 * </table>
 *
 * <p>This filter must run before Spring Security's {@code HeaderWriterFilter} (registered with
 * a high precedence in {@code SecurityConfig#securityHeadersFilterRegistration}) and Spring
 * Security's default Cache-Control header writer is disabled in both filter chains so this
 * filter's value survives.
 *
 * <h2>X-Frame-Options</h2>
 *
 * <p>This filter also owns {@code X-Frame-Options}: {@code DENY} on every response except draft
 * previews ({@code ?_preview=draft}), which get {@code SAMEORIGIN} so the console's live-preview
 * iframe can same-origin-embed the front page (template track via the {@code /_ujcms_preview}
 * proxy; production console/front-end share an origin). The front-end chain disables Spring
 * Security's frame-options writer — for the {@code DENY}/{@code SAMEORIGIN} modes that writer
 * overwrites unconditionally, so leaving it enabled would clobber the {@code SAMEORIGIN} back to
 * {@code DENY} and re-break the preview.
 */
public class SecurityHeadersFilter extends OncePerRequestFilter {

    /** Request attribute key under which the per-request CSP nonce is published. */
    public static final String CSP_NONCE_ATTRIBUTE = "csp_nonce";

    /** HSTS 值：1 年 + 包含子域。仅在 {@code ujcms.security.https-enabled} 开启时发送（Phase 4 / G6）。 */
    private static final String HSTS_VALUE = "max-age=31536000; includeSubDomains";

    private static final long PAGE_TTL_SECONDS = 300L;        // 5 min in the visitor's own browser
    private static final long SITEMAP_TTL_SECONDS = 3600L;    // 1 hour for /sitemap*.xml + /rss

    /** 前台页：仅访客自己的浏览器可缓存 5 分钟；共享缓存（CDN、反向代理）不得存储——页面按会话渲染。 */
    private static final String CACHE_PAGE =
            "private, max-age=" + PAGE_TTL_SECONDS + ", must-revalidate";
    /** 与 {@link #CACHE_PAGE} 同发：浏览器缓存按 Cookie 取值分键，登录 / 登出后会话 Cookie 一变即重新取页。 */
    private static final String VARY_COOKIE = "Cookie";
    private static final String CACHE_PUBLIC_SITEMAP =
            "public, max-age=" + SITEMAP_TTL_SECONDS + ", must-revalidate";
    private static final String CACHE_NO_STORE = "no-store";

    /** URL prefixes that, when GET-ed anonymously, qualify for the 5-minute browser cache. */
    private static final List<String> PAGE_PREFIXES = List.of(
            "/channel/", "/article/", "/tag/", "/users/");

    /** URL prefixes for the long-cache (sitemap / RSS / robots.txt / llms.txt) bucket. */
    private static final List<String> SITEMAP_RSS_PREFIXES = List.of(
            "/sitemap", "/rss", "/robots.txt", "/llms.txt");

    /**
     * 子目录站点（T2）：前台地址带一段站点子目录前缀（{@code /en/sitemap.xml}、{@code /en/channel/news}）。
     * 公共桶的前缀同时按去掉首段后的路径匹配；私有桶（{@code /cp/}、{@code /api/} 等）在此之前已判定，不受影响。
     * 首页只认精确 {@code /}：裸 {@code /xxx} 可能是任何端点，不按子目录首页放入公共桶。
     */
    private static final Pattern SUB_DIR_PATH =
            Pattern.compile("^/" + UrlConstants.SUB_DIR_SEGMENT_REGEX + "(/.*)$");

    /**
     * URL prefixes that NEVER receive public cache or strict CSP, even for anonymous GETs.
     * Keep narrow — anything not matched here defaults to no-store too.
     */
    private static final List<String> NEVER_CACHE_PREFIXES = List.of(
            "/cp/", "/api/backendapi/", "/api/", "/login", "/logout",
            "/settings/", "/oauth/", "/register", "/password-reset",
            "/message-board/create");

    private final SecureRandom random = new SecureRandom();

    /** 是否发送 HSTS 响应头（{@code ujcms.security.https-enabled}）。 */
    private final boolean hstsEnabled;
    /**
     * 后台 CSP 的额外来源：当前生效的第三方验证码厂商的脚本、接口、iframe 域名（CSP 指令名 → 来源列表），
     * 由 {@code CaptchaGuard#cspSources()} 按全局设置给出；自托管验证码为空。只放行选中的那一家
     */
    private final Supplier<Map<String, List<String>>> cspSources;

    public SecurityHeadersFilter() {
        this(false, Map::of);
    }

    public SecurityHeadersFilter(boolean hstsEnabled) {
        this(hstsEnabled, Map::of);
    }

    public SecurityHeadersFilter(boolean hstsEnabled, Supplier<Map<String, List<String>>> cspSources) {
        this.hstsEnabled = hstsEnabled;
        this.cspSources = cspSources;
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response,
                                    FilterChain chain) throws ServletException, IOException {
        String nonce = generateNonce();
        request.setAttribute(CSP_NONCE_ATTRIBUTE, nonce);

        // 传输安全（Phase 4 / G6）：开启后对所有响应发送 HSTS。仅 HTTPS 下浏览器才生效；HTTP 下被忽略。
        if (hstsEnabled) {
            response.setHeader("Strict-Transport-Security", HSTS_VALUE);
        }

        // 点击劫持防护：默认 DENY（与历史一致，禁止任何内嵌）。唯一例外是草稿预览页（?_preview=draft）：
        // 控制台「实时预览」需把前台页面同源内嵌至 iframe（模板轨道经 /_ujcms_preview 代理；生产期控制台
        // 与前台同源），故降为 SAMEORIGIN——仅允许同源内嵌，跨源点击劫持仍被拦截。本过滤器独占
        // X-Frame-Options：前台安全链已 .frameOptions().disable()，否则 Spring 的 XFrameOptionsHeaderWriter
        // 会对 DENY/SAMEORIGIN 模式无条件覆盖此处的取值（仅废弃的 ALLOW-FROM 模式才做 containsHeader 判空）。
        boolean draftPreview = "draft".equals(request.getParameter("_preview"));
        response.setHeader("X-Frame-Options", draftPreview ? "SAMEORIGIN" : "DENY");

        String path = request.getRequestURI();
        Bucket bucket = classify(request, path);
        switch (bucket) {
            case PAGE -> {
                response.setHeader("Cache-Control", CACHE_PAGE);
                response.addHeader("Vary", VARY_COOKIE);
            }
            case PUBLIC_LONG_CACHE -> response.setHeader("Cache-Control", CACHE_PUBLIC_SITEMAP);
            case BACKOFFICE -> {
                response.setHeader("Cache-Control", CACHE_NO_STORE);
                response.setHeader("Content-Security-Policy", backofficeCsp(cspSources.get()));
            }
            case NO_STORE -> response.setHeader("Cache-Control", CACHE_NO_STORE);
        }
        // PAGE / PUBLIC_LONG_CACHE intentionally do NOT set CSP — see the class Javadoc.
        // To re-enable strict CSP, add `response.setHeader("Content-Security-Policy", strictCsp(nonce));`
        // to those two switch arms above. `strictCsp(...)` and the per-request nonce attribute are
        // both retained so re-enabling is a one-line revert.

        chain.doFilter(request, response);
    }

    enum Bucket { PAGE, PUBLIC_LONG_CACHE, BACKOFFICE, NO_STORE }

    Bucket classify(HttpServletRequest request, String path) {
        if (path.startsWith("/cp/")) {
            return Bucket.BACKOFFICE;
        }
        // 草稿预览（Phase 7 / §7.0）：带 _preview=draft 一律 no-store。本过滤器先于安全链运行，此时
        // 预览令牌尚未置上下文（getUserPrincipal() 仍为 null），缺此判定首页等 GET 会落入可缓存的
        // PAGE 桶——草稿内容绝不能被缓存命中。
        if ("draft".equals(request.getParameter("_preview"))) {
            return Bucket.NO_STORE;
        }
        // 主题预览覆盖（P2-1）：带 _theme 同理一律 no-store——候选主题渲染的响应绝不能被公共缓存
        // 命中后回放给匿名访客（未激活主题“泄漏”成线上页面）。
        String themePreview = request.getParameter("_theme");
        if (themePreview != null && !themePreview.isBlank()) {
            return Bucket.NO_STORE;
        }
        // GET-only for the cacheable buckets. POST/PUT/DELETE/PATCH always default to no-store.
        if (!"GET".equalsIgnoreCase(request.getMethod())
                && !"HEAD".equalsIgnoreCase(request.getMethod())) {
            return Bucket.NO_STORE;
        }
        // 登录态在此尚不可知（本过滤器先于安全链运行，容器层的 getUserPrincipal() 恒为 null）：已认证响应的
        // no-store 由前台安全链内的 AuthenticatedCacheControlFilter 在认证之后改写，这里不做任何身份判定。
        for (String prefix : NEVER_CACHE_PREFIXES) {
            if (path.startsWith(prefix)) {
                return Bucket.NO_STORE;
            }
        }
        String sitePath = stripSubDir(path);
        for (String prefix : SITEMAP_RSS_PREFIXES) {
            if (path.startsWith(prefix) || sitePath.startsWith(prefix)) {
                return Bucket.PUBLIC_LONG_CACHE;
            }
        }
        // Homepage exact match — startsWith("/") would over-match.
        if ("/".equals(path) || path.equals("/search") || sitePath.equals("/search")) {
            return Bucket.PAGE;
        }
        for (String prefix : PAGE_PREFIXES) {
            if (path.startsWith(prefix) || sitePath.startsWith(prefix)) {
                return Bucket.PAGE;
            }
        }
        return Bucket.NO_STORE;
    }

    /**
     * 去掉子目录站点的首段路径；无首段可去时原样返回
     */
    static String stripSubDir(String path) {
        Matcher matcher = SUB_DIR_PATH.matcher(path);
        return matcher.matches() ? matcher.group(1) : path;
    }

    /**
     * Strict CSP for public/anonymous responses. Currently NOT emitted (see class Javadoc) —
     * retained so re-enabling is a one-line change in the bucket switch above.
     *
     * <p>Inline {@code <script>} blocks must carry the matching {@code nonce} attribute; the
     * reference theme already does this via {@code nonce="${csp_nonce!''}"}. {@code style-src
     * 'unsafe-inline'} stays permissive because Tailwind v4 emits utility-class style attributes
     * that aren't worth nonce-ing.
     */
    static String strictCsp(String nonce) {
        return "default-src 'self'; "
                + "script-src 'self' 'nonce-" + nonce + "'; "
                + "style-src 'self' 'unsafe-inline'; "
                + "img-src 'self' data: https:; "
                + "connect-src 'self'; "
                + "object-src 'none'; "
                + "base-uri 'self'; "
                + "frame-ancestors 'self'";
    }

    /**
     * Looser CSP for the {@code /cp/**} backoffice. ElementPlus performs runtime template
     * compilation which requires {@code 'unsafe-eval'}, and the Vue admin console emits inline
     * styles for chart components. Backoffice is admin-only behind authentication, so the
     * trade-off is acceptable.
     * {@code font-src} must allow {@code data:}: bpmn-js ships its palette icon font
     * ({@code bpmn-embedded.css}) only as base64 data URIs, and without this directive fonts
     * inherit {@code default-src 'self'} and the flow designer renders empty glyph boxes.
     */
    static String backofficeCsp() {
        return backofficeCsp(Map.of());
    }

    /**
     * @param extra 额外来源：CSP 指令名 → 来源列表（第三方验证码厂商）。未在基础策略中的指令（如 {@code frame-src}）
     *              追加为新指令；来源只接受 {@code https://} 开头的域名，其它一律忽略
     */
    static String backofficeCsp(Map<String, List<String>> extra) {
        Map<String, String> directives = new LinkedHashMap<>();
        directives.put("default-src", "'self'");
        directives.put("script-src", "'self' 'unsafe-inline' 'unsafe-eval'");
        directives.put("style-src", "'self' 'unsafe-inline'");
        directives.put("img-src", "'self' data: https:");
        directives.put("font-src", "'self' data:");
        directives.put("connect-src", "'self'");
        directives.put("object-src", "'none'");
        directives.put("base-uri", "'self'");
        for (Map.Entry<String, List<String>> entry : extra.entrySet()) {
            String name = entry.getKey();
            if (!name.matches("[a-z-]+-src")) {
                continue;
            }
            StringBuilder sb = new StringBuilder(directives.getOrDefault(name, "'self'"));
            for (String source : entry.getValue()) {
                if (source != null && source.matches("https://[A-Za-z0-9.*-]+(:\\d+)?")) {
                    sb.append(' ').append(source);
                }
            }
            directives.put(name, sb.toString());
        }
        StringBuilder csp = new StringBuilder();
        for (Map.Entry<String, String> entry : directives.entrySet()) {
            if (csp.length() > 0) {
                csp.append("; ");
            }
            csp.append(entry.getKey()).append(' ').append(entry.getValue());
        }
        return csp.toString();
    }

    private String generateNonce() {
        byte[] bytes = new byte[16];
        random.nextBytes(bytes);
        return Base64.getEncoder().withoutPadding().encodeToString(bytes);
    }

    /** Test-only helper: extract the nonce embedded in a CSP header value. */
    @Nullable
    static String extractNonce(@Nullable String cspHeader) {
        if (cspHeader == null) {
            return null;
        }
        int idx = cspHeader.indexOf("'nonce-");
        if (idx < 0) {
            return null;
        }
        int start = idx + "'nonce-".length();
        int end = cspHeader.indexOf('\'', start);
        return end < 0 ? null : cspHeader.substring(start, end);
    }
}
