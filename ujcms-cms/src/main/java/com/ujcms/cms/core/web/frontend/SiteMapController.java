package com.ujcms.cms.core.web.frontend;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
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
import com.ujcms.cms.core.web.support.ConditionalResponses;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.common.web.exception.Http404Exception;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.apache.commons.text.StringEscapeUtils;
import org.jspecify.annotations.Nullable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.io.IOException;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.TimeUnit;
import java.util.function.Supplier;

/**
 * 前台 SiteMap Controller（docs/spec/sitemap/plan.md）
 * <p>
 * 输出 Sitemaps.org 0.9 索引文件 {@code /sitemap.xml}、栏目文件 {@code /sitemap-channel.xml}（首页在前）、
 * 文章分页文件 {@code /sitemap-article.xml}、{@code /sitemap-article-N.xml}（每页 {@value #PAGE_SIZE} 条，按 id 升序，
 * 旧页字节稳定）。只输出 {@code <loc>} 与 {@code <lastmod>}——Google、Bing 明确忽略 changefreq / priority，百度仅作提示；
 * 语言组站点附 {@code <xhtml:link rel="alternate" hreflang>}（含 x-default，取组内排序第一的成员，与默认主题 head 一致）；
 * 文章缩略图以 Google 图片扩展 {@code <image:image>} 输出。
 * <p>
 * 每站点、每文件缓存 1 小时（与 {@code SecurityHeadersFilter} 对 {@code /sitemap*} 的 Cache-Control 一致），
 * 弱 ETag + Last-Modified 回答条件请求，生成过程固定为桌面上下文以免缓存正文随爬虫 User-Agent 变化。
 * <p>
 * 百度自 2023 年起不处理索引型 sitemap：向百度须逐个提交子文件（见 docs/guide/sitemap.zh.md）。
 *
 * @author PONY
 */
@FrontendController("frontendSiteMapController")
public class SiteMapController {
    private final ChannelService channelService;
    private final ArticleService articleService;
    private final GroupService groupService;
    private final LangAlternateService langAlternateService;
    /**
     * 每站点、每文件缓存 1 小时（plan.md D8）。实例级而非静态：各 Controller 实例（含单元测试）互不干扰
     */
    private final Cache<CacheKey, ConditionalResponses.Body> cache = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS).maximumSize(10_000).build();

    public SiteMapController(ChannelService channelService, ArticleService articleService, GroupService groupService,
                             LangAlternateService langAlternateService) {
        this.channelService = channelService;
        this.articleService = articleService;
        this.groupService = groupService;
        this.langAlternateService = langAlternateService;
    }

    @GetMapping("/sitemap.xml")
    public void index(Site site, HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, site, KIND_INDEX, 0, () -> buildIndex(site));
    }

    @GetMapping("/sitemap-channel.xml")
    public void channel(Site site, HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, site, KIND_CHANNEL, 0, () -> buildChannel(site));
    }

    @GetMapping("/sitemap-article.xml")
    public void article(Site site, HttpServletRequest request, HttpServletResponse response) throws IOException {
        respond(request, response, site, KIND_ARTICLE, 1, () -> buildArticle(site, 1));
    }

    @GetMapping("/sitemap-article-{page:[\\d]+}.xml")
    public void article(Site site, @PathVariable int page,
                        HttpServletRequest request, HttpServletResponse response) throws IOException {
        // 第 1 页只在 /sitemap-article.xml，索引也从不链接 -0/-1：按未发布地址 404
        // （曾用 @Min(2) 校验，抓取器命中时得到的是 ConstraintViolationException → 500 与堆栈）
        if (page < 2) {
            throw new Http404Exception("sitemap page not found: " + page);
        }
        respond(request, response, site, KIND_ARTICLE, page, () -> buildArticle(site, page));
    }

    // region build

    /**
     * 索引文件：栏目文件 + 文章分页文件，各带 lastmod（该文件内条目的最大修改时间），供引擎只抓取有变化的子文件（plan.md D5）。
     * 页数由同一可收录条件的修改时间列表切片得出，与分页查询天然一致。
     */
    private Sitemap buildIndex(Site site) {
        List<OffsetDateTime> modified = articleService.listModifiedForSitemap(site.getId());
        OffsetDateTime siteLastmod = max(modified);
        StringBuilder buff = new StringBuilder(512);
        buff.append(XML_DECLARATION).append('\n');
        buff.append("<sitemapindex xmlns=\"").append(SITEMAP_NS).append("\">\n");
        sitemapIndexItem(buff, site.getFullUrl() + "/sitemap-channel.xml", siteLastmod);
        int pages = Math.max(1, (modified.size() + PAGE_SIZE - 1) / PAGE_SIZE);
        for (int page = 1; page <= pages; page++) {
            int from = (page - 1) * PAGE_SIZE;
            int to = Math.min(modified.size(), page * PAGE_SIZE);
            OffsetDateTime pageLastmod = from < to ? max(modified.subList(from, to)) : null;
            sitemapIndexItem(buff, site.getFullUrl() + articleSitemapPath(page), pageLastmod);
        }
        buff.append("</sitemapindex>\n");
        return new Sitemap(buff.toString(), siteLastmod);
    }

    /**
     * 栏目文件：首页在前（lastmod = 全站最新文章），其后为非链接、匿名用户可访问的栏目，
     * lastmod = max(栏目子树内最新可收录文章, 栏目自身修改时间)（plan.md D3/D4/D6）。
     */
    private Sitemap buildChannel(Site site) {
        List<Channel> channels = channelService.listByChannelForSitemap(site.getId());
        Map<Long, OffsetDateTime> articleLastmods = articleService.listChannelModifiedForSitemap(site.getId());
        OffsetDateTime siteLastmod = max(articleLastmods.values());
        Set<Long> accessible = anonymousAccessibleChannelIds(site.getId());
        StringBuilder buff = new StringBuilder(4096);
        sitemapBegin(buff);
        sitemapItem(buff, site.getFullUrl(), siteLastmod, langAlternateService.listExactAlternates(site, null));
        for (Channel channel : channels) {
            if (channel.isLink() || (accessible != null && !accessible.contains(channel.getId()))) {
                continue;
            }
            channel.setSite(site);
            OffsetDateTime lastmod = later(articleLastmods.get(channel.getId()), channel.getModified());
            // 语言备选：仅 exact（同别名栏目）条目；含自引用（Google 要求）
            sitemapItem(buff, channel.getFullUrl(), lastmod, langAlternateService.listExactAlternates(site, channel));
        }
        sitemapEnd(buff);
        return new Sitemap(buff.toString(), siteLastmod);
    }

    /**
     * 文章分页文件。可收录条件已在 SQL 内过滤，Java 侧 isNormal/isLink 仅为兜底；页外请求 404（不缓存）。
     * 两遍：先收集本页文章，批量取翻译等价集备选（无逐行 N+1），再写入。
     */
    private Sitemap buildArticle(Site site, int page) {
        List<Article> articles = new ArrayList<>(PAGE_SIZE);
        for (Article article : articleService.listForSitemap(site.getId(), page, PAGE_SIZE)) {
            if (article.isNormal() && !article.isLink()) {
                article.setSite(site);
                articles.add(article);
            }
        }
        if (articles.isEmpty() && page > 1) {
            throw new Http404Exception("sitemap page not found: " + page);
        }
        Map<Long, List<LangAlternate>> alternatesByArticleId =
                langAlternateService.listExactAlternatesByArticles(site, articles);
        StringBuilder buff = new StringBuilder(articles.size() * 160 + 256);
        sitemapBegin(buff);
        OffsetDateTime pageLastmod = null;
        for (Article article : articles) {
            sitemapItem(buff, article.getFullUrl(), article.getModified(),
                    alternatesByArticleId.getOrDefault(article.getId(), List.of()),
                    absoluteUrl(site, article.getImage()));
            pageLastmod = later(pageLastmod, article.getModified());
        }
        if (articles.isEmpty()) {
            // XSD 要求 <urlset> 至少含一个 <url>：新站尚无文章时回退为站点首页，保持文件合法
            sitemapItem(buff, site.getFullUrl(), null, List.of());
        }
        sitemapEnd(buff);
        return new Sitemap(buff.toString(), pageLastmod);
    }

    /**
     * 匿名用户组可访问的栏目ID集合；拥有全部浏览权限时为 {@code null}（不过滤）。
     * 匿名不可访问的栏目页对爬虫回 401，不进 sitemap；文章页前台不做栏目访问校验，不受此影响。
     */
    @Nullable
    private Set<Long> anonymousAccessibleChannelIds(Long siteId) {
        Group anonymous = groupService.getAnonymous();
        if (Boolean.TRUE.equals(anonymous.getAllAccessPermission())) {
            return null;
        }
        return new HashSet<>(groupService.listAccessPermissions(anonymous.getId(), siteId));
    }
    // endregion

    // region respond

    private void respond(HttpServletRequest request, HttpServletResponse response, Site site,
                         String kind, int page, Supplier<Sitemap> builder) throws IOException {
        CacheKey key = new CacheKey(site.getId(), kind, page);
        ConditionalResponses.Body body = cache.getIfPresent(key);
        if (body == null) {
            // 构建可能抛 404（页外）：先构建再放入，404 不进缓存
            Sitemap sitemap = inDesktopContext(builder);
            body = ConditionalResponses.body(sitemap.xml(), sitemap.lastModified());
            cache.put(key, body);
        }
        ConditionalResponses.write(request, response, body, CONTENT_TYPE);
    }

    /**
     * 固定为桌面上下文生成（plan.md D7）：静态化站点的 URL 组装读取 {@link Contexts#isMobile()}，
     * 不固定则缓存正文取决于首个命中的爬虫 User-Agent（Googlebot 多以手机 UA 抓取）。
     */
    private static <T> T inDesktopContext(Supplier<T> supplier) {
        boolean mobile = Contexts.isMobile();
        Contexts.setMobile(false);
        try {
            return supplier.get();
        } finally {
            Contexts.setMobile(mobile);
        }
    }
    // endregion

    // region xml
    // 每个元素独占一行、两空格缩进：Chromium（Chrome/Edge）遇到 XHTML 命名空间元素（xhtml:link）不启用
    // XML 树视图而直接渲染 DOM，紧凑输出会显示成 loc 与 lastmod 首尾相连的一行文本；换行后页面与 view-source
    // 均可读，gzip 后体积差异可忽略，引擎不关心空白。

    private static void sitemapIndexItem(StringBuilder buff, String loc, @Nullable OffsetDateTime lastmod) {
        buff.append("  <sitemap>\n");
        buff.append("    <loc>").append(StringEscapeUtils.escapeXml11(loc)).append("</loc>\n");
        appendLastmod(buff, lastmod);
        buff.append("  </sitemap>\n");
    }

    private static void sitemapBegin(StringBuilder buff) {
        buff.append(XML_DECLARATION).append('\n');
        // xhtml / image 命名空间供语言备选与图片扩展使用；无条件声明（XSD 允许，比按内容条件化更简单）
        buff.append("<urlset xmlns=\"").append(SITEMAP_NS).append("\"")
                .append(" xmlns:xhtml=\"http://www.w3.org/1999/xhtml\"")
                .append(" xmlns:image=\"").append(IMAGE_NS).append("\">\n");
    }

    private static void sitemapEnd(StringBuilder buff) {
        buff.append("</urlset>\n");
    }

    private static void sitemapItem(StringBuilder buff, String loc, @Nullable OffsetDateTime lastmod,
                                    List<LangAlternate> alternates) {
        sitemapItem(buff, loc, lastmod, alternates, null);
    }

    private static void sitemapItem(StringBuilder buff, String loc, @Nullable OffsetDateTime lastmod,
                                    List<LangAlternate> alternates, @Nullable String imageUrl) {
        buff.append("  <url>\n");
        // 转义 XML 特殊字符（& < > ' "）：今日 URL 不含这些字符，防将来引入查询串或非 ASCII 别名
        buff.append("    <loc>").append(StringEscapeUtils.escapeXml11(loc)).append("</loc>\n");
        appendLastmod(buff, lastmod);
        // 语言备选。孤立自引用无意义（未分组站点保持无备选输出），>=2 条才写
        if (alternates.size() > 1) {
            for (LangAlternate alternate : alternates) {
                appendAlternate(buff, alternate.getLang(), alternate.getFullUrl());
            }
            // x-default：组内排序（order_）第一的成员，与默认主题 layout/theme.html 的 head 输出一致
            appendAlternate(buff, "x-default", alternates.get(0).getFullUrl());
        }
        // Google 图片扩展：仅 <image:loc>（caption/title 等已被 Google 废弃）；需绝对地址
        if (imageUrl != null) {
            buff.append("    <image:image><image:loc>").append(StringEscapeUtils.escapeXml11(imageUrl))
                    .append("</image:loc></image:image>\n");
        }
        buff.append("  </url>\n");
    }

    private static void appendAlternate(StringBuilder buff, String hreflang, String href) {
        buff.append("    <xhtml:link rel=\"alternate\" hreflang=\"")
                .append(StringEscapeUtils.escapeXml11(hreflang))
                .append("\" href=\"")
                .append(StringEscapeUtils.escapeXml11(href))
                .append("\"/>\n");
    }

    /**
     * 站内资源地址转绝对地址：委托 {@link Site#toAbsoluteUrl(String, String)}（模板 JSON-LD 与 sitemap 共用同一规则）
     */
    @Nullable
    static String absoluteUrl(Site site, @Nullable String url) {
        return Site.toAbsoluteUrl(site.getDynamicFullUrl(), url);
    }

    /**
     * lastmod 取日期精度（Bing 建议 YYYY-MM-DD）；无依据时省略，不伪造
     */
    private static void appendLastmod(StringBuilder buff, @Nullable OffsetDateTime lastmod) {
        if (lastmod != null) {
            buff.append("    <lastmod>").append(lastmod.format(DateTimeFormatter.ISO_LOCAL_DATE)).append("</lastmod>\n");
        }
    }

    private static String articleSitemapPath(int page) {
        return page <= 1 ? "/sitemap-article.xml" : "/sitemap-article-" + page + ".xml";
    }

    @Nullable
    private static OffsetDateTime max(Collection<OffsetDateTime> values) {
        OffsetDateTime result = null;
        for (OffsetDateTime value : values) {
            result = later(result, value);
        }
        return result;
    }

    @Nullable
    private static OffsetDateTime later(@Nullable OffsetDateTime a, @Nullable OffsetDateTime b) {
        if (a == null) {
            return b;
        }
        return b != null && b.isAfter(a) ? b : a;
    }
    // endregion

    private record Sitemap(String xml, @Nullable OffsetDateTime lastModified) {
    }

    private record CacheKey(Long siteId, String kind, int page) {
    }

    private static final String KIND_INDEX = "index";
    private static final String KIND_CHANNEL = "channel";
    private static final String KIND_ARTICLE = "article";
    private static final String CONTENT_TYPE = "application/xml;charset=UTF-8";
    private static final String XML_DECLARATION = "<?xml version=\"1.0\" encoding=\"UTF-8\"?>";
    private static final String SITEMAP_NS = "http://www.sitemaps.org/schemas/sitemap/0.9";
    private static final String IMAGE_NS = "http://www.google.com/schemas/sitemap-image/1.1";
    /**
     * 每页 10000 条（plan.md D2）：Google/Bing 上限 50000 条 / 50MB，百度 50000 条 / 10MB——含 5 种语言备选时约 7MB；
     * 百度不接受索引文件、每站最多 200 个子文件，10000 条比 WordPress 的 2000 条把配额压力降到五分之一。
     */
    static final int PAGE_SIZE = 10_000;
}
