package com.ujcms.core.web.api;

import com.ofwise.util.db.MyBatis;
import com.ofwise.util.function.Function3;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.security.Secures;
import com.ofwise.util.web.exception.Http404Exception;
import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.ArticleBuffer;
import com.ujcms.core.domain.Global;
import com.ujcms.core.service.ArticleBufferService;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.service.ChannelService;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.support.Utils;
import com.ujcms.core.web.directive.ArticleListDirective;
import com.ujcms.core.web.directive.ArticleNextDirective;
import com.ujcms.core.web.support.Directives;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ofwise.util.query.QueryUtils.QUERY_PREFIX;
import static com.ujcms.core.support.UrlConstants.API;

/**
 * 文章前台 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/article")
public class ArticleController {
    private GlobalService globalService;
    private ChannelService channelService;
    private ArticleService articleService;
    private ArticleBufferService bufferService;

    public ArticleController(GlobalService globalService, ChannelService channelService, ArticleService articleService,
                             ArticleBufferService bufferService) {
        this.globalService = globalService;
        this.channelService = channelService;
        this.articleService = articleService;
        this.bufferService = bufferService;
    }

    private <T> T query(HttpServletRequest request,
                        Function3<Map<String, String>, Map<String, Object>, Map<String, String>, T> handle) {
        Integer defaultSiteId = globalService.getUnique().getDefaultSiteId();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        Map<String, Object> queryMap = QueryUtils.getQueryMap(params, QUERY_PREFIX);
        ArticleListDirective.assemble(queryMap, params, defaultSiteId, channelService);
        Map<String, String> customsQueryMap = QueryUtils.getCustomsQueryMap(params);
        return handle.apply(params, queryMap, customsQueryMap);
    }

    @GetMapping
    public List<Article> list(HttpServletRequest request) {
        return query(request, (params, queryMap, customsQueryMap) -> {
            Integer offset = Directives.findOffset(params);
            Integer limit = Directives.findLimit(params);
            return articleService.selectList(queryMap, customsQueryMap, null, offset, limit);
        });
    }

    @GetMapping("/page")
    public Page<Article> page(HttpServletRequest request) {
        return query(request, (params, queryMap, customsQueryMap) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return MyBatis.toPage(articleService.selectPage(queryMap, customsQueryMap, null, page, pageSize));
        });
    }

    @GetMapping("/{id}")
    public Article show(@PathVariable Integer id) {
        return articleService.select(id);
    }

    @GetMapping("/next")
    public Article next(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return ArticleNextDirective.query(params, articleService::findNext);
    }

    @GetMapping("/prev")
    public Article prev(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        return ArticleNextDirective.query(params, articleService::findPrev);
    }

    @GetMapping("/view/{id}")
    public long view(@PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @PostMapping("/up/{id}")
    public int up(@PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            return 0;
        }
        int ups = buffer.getUps() + 1;
        buffer.setUps(ups);
        bufferService.update(buffer);
        return ups;
    }

    @PostMapping("/down/{id}")
    public int down(@PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            return 0;
        }
        int downs = buffer.getDowns() + 1;
        buffer.setDowns(downs);
        bufferService.update(buffer);
        return downs;
    }

    @GetMapping("/download-params/{id}")
    public String downloadParam(@PathVariable Integer id) {
        Global global = globalService.getUnique();
        long time = System.currentTimeMillis();
        String secret = global.getSecret();
        if (StringUtils.isBlank(secret)) {
            secret = Secures.randomAlphanumeric(32);
            global.setSecret(secret);
            globalService.update(global);
        }
        String key = Utils.getDownloadKey(id, time, secret);
        return "time=" + time + "&key=" + key;
    }

    @PostMapping("/download/{id}")
    public int download(@PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            return 0;
        }
        int downloads = buffer.getDownloads() + 1;
        buffer.setDownloads(downloads);
        bufferService.update(buffer);
        return downloads;
    }

    @GetMapping("/buffer/{id}")
    public ArticleBuffer buffer(@PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            throw new Http404Exception("ArticleBuffer not found. id=" + id);
        }
        return buffer;
    }
}
