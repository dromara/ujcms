package com.ujcms.cms.ext.web.frontend;

import com.github.benmanes.caffeine.cache.Cache;
import com.github.benmanes.caffeine.cache.Caffeine;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.ConditionalResponses;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.cms.ext.component.GeoFileService;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.util.Objects;
import java.util.concurrent.TimeUnit;

/**
 * 前台 GEO（生成式引擎优化）Controller。提供 llms.txt、robots.txt（docs/spec/ai/geo.md §5-§6）与 IndexNow 密钥文件
 * （docs/spec/sitemap/plan.md D13）。正文由 {@link GeoFileService} 生成，与后台预览共用。
 * <p>
 * 完全跟随 {@link com.ujcms.cms.core.web.frontend.SiteMapController} 的模式：子目录站点变体、当前站点解析、
 * 弱 ETag + 条件响应（{@link ConditionalResponses}）。
 * 动态生成 + 缓存（sitemap 先例），不走静态生成机制。robots.txt 开关关闭时回退到 webapp 根下的
 * 物理文件（本映射优先级高于静态资源处理器，须手工回退），无文件则 404；llms.txt 关闭即 404（升级前无此文件）。
 * 子目录站点的 {@code /{subDir}/robots.txt} 仅供查看：爬虫只读主机根的 robots.txt，根站点会为子目录站点补 Sitemap 行。
 *
 * @author PONY
 */
@FrontendController("frontendGeoController")
public class GeoController {
    private final GeoFileService geoFileService;

    public GeoController(GeoFileService geoFileService) {
        this.geoFileService = geoFileService;
    }

    @GetMapping("/llms.txt")
    public void llmsTxt(Site site, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site.Geo geo = site.getGeo();
        if (!geo.isLlmsTxtEnabled()) {
            throw new Http404Exception("llms.txt disabled. site: " + site.getId());
        }
        ConditionalResponses.Body body = CACHE.get(new CacheKey(site.getId(), KIND_LLMS, snapshot(site)),
                key -> ConditionalResponses.body(geoFileService.llmsTxt(site, geo), null));
        ConditionalResponses.write(request, response, body, CONTENT_TYPE);
    }

    /**
     * IndexNow 密钥文件（docs/spec/sitemap/plan.md D13）：原样输出站点配置的密钥；未开启或密钥不符则 404。
     * 文件位于站点路径下（子目录站点为 {@code /{subDir}/indexnow-{key}.txt}）——IndexNow 规定子目录下的密钥文件只对
     * 该路径内的地址生效，恰与子目录站点的地址范围一致。
     */
    @GetMapping("/indexnow-{key:[A-Za-z0-9-]{8,128}}.txt")
    public void indexNowKey(Site site, @PathVariable String key, HttpServletResponse response) throws IOException {
        Site.Geo geo = site.getGeo();
        if (!geo.isIndexNowEnabled() || !key.equals(geo.getIndexNowKey())) {
            throw new Http404Exception("IndexNow key file not found. site: " + site.getId());
        }
        response.setContentType(CONTENT_TYPE);
        response.getOutputStream().write(key.getBytes(StandardCharsets.UTF_8));
    }

    @GetMapping("/robots.txt")
    public void robotsTxt(Site site, HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site.Geo geo = site.getGeo();
        if (!geo.isRobotsEnabled()) {
            // 升级前部署可能有手工维护的物理 robots.txt：关闭开关须能恢复它，而非 404
            if (writePhysicalFile(request, response)) {
                return;
            }
            throw new Http404Exception("robots.txt disabled. site: " + site.getId());
        }
        ConditionalResponses.Body body = CACHE.get(new CacheKey(site.getId(), KIND_ROBOTS, snapshot(site)),
                key -> ConditionalResponses.body(geoFileService.robotsTxt(site, geo), null));
        ConditionalResponses.write(request, response, body, CONTENT_TYPE);
    }

    /**
     * 输出 webapp 根下与请求路径同名的物理文件
     *
     * @return 文件存在并已输出则为 {@code true}
     */
    private static boolean writePhysicalFile(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        try (InputStream in = request.getServletContext().getResourceAsStream(request.getServletPath())) {
            if (in == null) {
                return false;
            }
            response.setContentType("text/plain;charset=UTF-8");
            in.transferTo(response.getOutputStream());
            return true;
        }
    }

    /**
     * GEO 设置快照。作为缓存键的一部分：站点设置保存后设置 JSON 变化，旧缓存键不再命中即视为失效——
     * 无需核心层向扩展层驱逐缓存的跨层钩子（core 不依赖 ext），旧条目由 TTL/容量自然淘汰。
     * 根站点 robots.txt 里的子目录站点 Sitemap 行随 TTL 刷新（新增子目录站点最长 1 小时后出现）。
     */
    private String snapshot(Site site) {
        return Objects.toString(site.getGeoSettings(), "");
    }

    private record CacheKey(Long siteId, String kind, String settings) {
    }

    /**
     * 每站点、每文件缓存 1 小时（AI 爬虫抓取频率低，sitemap 先例的动态 + 缓存策略）。
     */
    private static final Cache<CacheKey, ConditionalResponses.Body> CACHE = Caffeine.newBuilder()
            .expireAfterWrite(1, TimeUnit.HOURS).maximumSize(1_000).build();

    private static final String CONTENT_TYPE = "text/plain;charset=UTF-8";
    private static final String KIND_LLMS = "llms";
    private static final String KIND_ROBOTS = "robots";
}
