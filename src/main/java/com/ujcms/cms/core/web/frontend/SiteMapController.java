package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.http.HttpHeaders;
import org.springframework.lang.Nullable;
import org.springframework.stereotype.Controller;
import org.springframework.util.DigestUtils;
import org.springframework.util.StringUtils;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.context.request.ServletWebRequest;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.constraints.Min;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.time.format.DateTimeFormatter;
import java.util.List;
import java.util.Map;

/**
 * 前台 SiteMap Controller
 * <p>
 * 百度建议sitemap支持etag，这样百度会更频繁的访问sitemap。但是ShallowEtagHeaderFilter的UrlPatterns太难用，无法符合要求。
 *
 * <ul>
 * <li><a href="https://www.ujcms.com/knowledge/583.html">搜索引擎对sitemap处理及要求</a></li>
 * <li><a href="https://developer.mozilla.org/zh-CN/docs/Web/HTTP/Headers/ETag">HTTP Header ETag</a></li>
 * <li><a href="https://www.baeldung.com/etags-for-rest-with-spring">ETags for REST with Spring</a></li>
 * <li><a href="https://blog.csdn.net/RenshenLi/article/details/119190602">Spring ShallowEtagHeaderFilter 源码学习</a></li>
 * </ul>
 *
 * <pre>
 * <?xml version="1.0" encoding="UTF-8"?>
 * <sitemapindex xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
 *   <sitemap>
 *     <loc>http://www.example.com/sitemap1.xml.gz</loc>
 *   </sitemap>
 *   <sitemap>
 *     <loc>http://www.example.com/sitemap2.xml.gz</loc>
 *   </sitemap>
 * </sitemapindex>
 * </pre>
 *
 * <pre>
 * <?xml version="1.0" encoding="UTF-8"?>
 * <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
 *   <url>
 *     <loc>http://www.example.com/foo.html</loc>
 *     <lastmod>2018-06-04</lastmod>
 *   </url>
 * </urlset>
 * </pre>
 *
 * @author PONY
 * @see org.springframework.web.filter.ShallowEtagHeaderFilter
 */
@Controller("frontendSiteMapController")
@Validated
public class SiteMapController {
    private final ChannelService channelService;
    private final ArticleService articleService;
    private final SiteResolver siteResolver;

    public SiteMapController(ChannelService channelService, ArticleService articleService, SiteResolver siteResolver) {
        this.channelService = channelService;
        this.articleService = articleService;
        this.siteResolver = siteResolver;
    }

    @GetMapping(value = {"/sitemap.xml", "/{subDir:[\\w-]+}/sitemap.xml"})
    public void index(@PathVariable(required = false) String subDir,
                      HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site site = siteResolver.resolve(request, subDir);
        StringBuilder buff = new StringBuilder();

        sitemapIndexBegin(buff);
        sitemapIndexItem(buff, site.getUrl() + "/sitemap-channel.xml");
        sitemapIndexItem(buff, site.getUrl() + "/sitemap-article.xml");
        int count = articleService.countBySiteId(site.getId());
        for (int i = 2; count > PAGE_SIZE; count -= PAGE_SIZE) {
            sitemapIndexItem(buff, site.getUrl() + "/sitemap-article-" + i + ".xml");
        }
        sitemapIndexEnd(buff);

        updateResponse(request, response, buff.toString());
    }

    @GetMapping(value = {"/sitemap-channel.xml", "/{subDir:[\\w-]+}/sitemap-channel.xml"})
    public void channel(@PathVariable(required = false) String subDir,
                        HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site site = siteResolver.resolve(request, subDir);
        List<Channel> list = channelService.listByChannelForSitemap(site.getId());
        StringBuilder buff = new StringBuilder();

        sitemapBegin(buff);
        list.forEach(channel -> {
            if (!channel.isLink()) {
                channel.setSite(site);
                sitemapItem(buff, channel.getUrl(), null);
            }
        });
        sitemapEnd(buff);

        updateResponse(request, response, buff.toString());
    }

    @GetMapping(value = {"/sitemap-article.xml", "/{subDir:[\\w-]+}/sitemap-article.xml"})
    public void article(@PathVariable(required = false) String subDir,
                        HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site site = siteResolver.resolve(request, subDir);
        StringBuilder buff = new StringBuilder();

        sitemapBegin(buff);
        articleService.listBySiteIdForSiteMap(site.getId(), null, PAGE_SIZE).forEach(article -> {
            if (article.isNormal() && !article.isLink()) {
                article.setSite(site);
                sitemapItem(buff, article.getUrl(), article.getModified());
            }
        });
        sitemapEnd(buff);

        updateResponse(request, response, buff.toString());
    }

    @GetMapping(value = {"/sitemap-article-{page:[\\d]+}.xml", "/{subDir:[\\w-]+}/sitemap-article-{page:[\\d]+}.xml"})
    public void article(@PathVariable(required = false) String subDir, @PathVariable @Min(2) Integer page,
                        HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site site = siteResolver.resolve(request, subDir);
        StringBuilder buff = new StringBuilder();
        // 上一页加1条，即本页第一条
        int minCount = PAGE_SIZE * (page - 1) + 1;
        Map<String, Object> stat = articleService.statForSitemap(site.getId(), minCount);
        Long count = (Long) stat.get("count");
        Long maxId = (Long) stat.get("maxId");
        if (count == null || maxId == null || count < minCount) {
            throw new Http404Exception("sitemap page not found: " + page);
        }
        sitemapBegin(buff);
        articleService.listBySiteIdForSiteMap(site.getId(), maxId, PAGE_SIZE * page).forEach(article -> {
            if (!article.isLink()) {
                article.setSite(site);
                sitemapItem(buff, article.getUrl(), null);
            }
        });
        sitemapEnd(buff);

        updateResponse(request, response, buff.toString());
    }


    private void sitemapIndexBegin(StringBuilder buff) {
        buff.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        buff.append("<sitemapindex xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
    }

    private void sitemapIndexEnd(StringBuilder buff) {
        buff.append("</sitemapindex>");
    }

    /**
     * <pre>
     * <sitemap>
     *   <loc>http://www.example.com/sitemap1.xml.gz</loc>
     * </sitemap>
     * </pre>
     */
    private void sitemapIndexItem(StringBuilder buff, String loc) {
        buff.append("<sitemap>");
        buff.append("<loc>");
        buff.append(loc);
        buff.append("</loc>");
        buff.append("</sitemap>");
    }

    /**
     * <pre>
     * <?xml version="1.0" encoding="UTF-8"?>
     * <urlset xmlns="http://www.sitemaps.org/schemas/sitemap/0.9">
     *   <url>
     *     <loc>http://www.example.com/foo.html</loc>
     *     <lastmod>2018-06-04</lastmod>
     *   </url>
     * </urlset>
     * </pre>
     */
    private void sitemapBegin(StringBuilder buff) {
        buff.append("<?xml version=\"1.0\" encoding=\"UTF-8\"?>");
        buff.append("<urlset xmlns=\"http://www.sitemaps.org/schemas/sitemap/0.9\">");
    }

    private void sitemapEnd(StringBuilder buff) {
        buff.append("</urlset>");
    }

    private void sitemapItem(StringBuilder buff, String loc, @Nullable OffsetDateTime lastModify) {
        buff.append("<url>");
        buff.append("<loc>");
        buff.append(loc);
        buff.append("</loc>");
        if (lastModify != null) {
            buff.append("<lastmod>");
            buff.append(lastModify.format(DateTimeFormatter.ISO_LOCAL_DATE));
            buff.append("</lastmod>");
        }
        buff.append("</url>");
    }

    private void updateResponse(HttpServletRequest request, HttpServletResponse response, String body) throws IOException {
        byte[] bytes = body.getBytes(StandardCharsets.UTF_8);
        String eTag = request.getHeader(HttpHeaders.ETAG);
        if (!StringUtils.hasText(eTag)) {
            eTag = generateEtagHeaderValue(bytes);
            response.setHeader(HttpHeaders.ETAG, eTag);
        }
        if (new ServletWebRequest(request, response).checkNotModified(eTag)) {
            return;
        }
        response.setContentType("application/xml;charset=UTF-8");
        response.getOutputStream().write(bytes);
    }

    private String generateEtagHeaderValue(byte[] bytes) {
        // length of W/ + " + 0 + 32bits md5 hash + "
        StringBuilder builder = new StringBuilder(37);
        builder.append("\"0");
        DigestUtils.appendMd5DigestAsHex(bytes, builder);
        builder.append('"');
        return builder.toString();
    }

    /**
     * 每页2000条数据。参考WordPress的sitemap每页为2000条记录。
     */
    private static final int PAGE_SIZE = 2000;
}
