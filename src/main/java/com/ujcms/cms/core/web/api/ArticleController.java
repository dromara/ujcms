package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.util.db.MyBatis;
import com.ujcms.util.query.QueryUtils;
import com.ujcms.util.web.exception.Http404Exception;
import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.service.ArticleBufferService;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.support.Utils;
import com.ujcms.cms.core.web.directive.ArticleNextDirective;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.web.directive.ArticleListDirective;
import org.springframework.data.domain.Page;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.ujcms.util.query.QueryUtils.QUERY_PREFIX;

/**
 * 文章前台 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(UrlConstants.API + "/article")
public class ArticleController {
    private ConfigService configService;
    private ChannelService channelService;
    private ArticleService articleService;
    private ArticleBufferService bufferService;
    private Props props;

    public ArticleController(ConfigService configService, ChannelService channelService, ArticleService articleService,
                             ArticleBufferService bufferService, Props props) {
        this.configService = configService;
        this.channelService = channelService;
        this.articleService = articleService;
        this.bufferService = bufferService;
        this.props = props;
    }

    private <T> T query(HttpServletRequest request,
                        BiFunction<ArticleArgs, Map<String, String>, T> handle) {
        Integer defaultSiteId = configService.getUnique().getDefaultSiteId();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        ArticleArgs args = ArticleArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        ArticleListDirective.assemble(args, params, defaultSiteId, channelService);
        args.customsQueryMap(QueryUtils.getCustomsQueryMap(params));
        return handle.apply(args, params);
    }

    @GetMapping
    public List<Article> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return articleService.selectList(args, offset, limit);
        });
    }

    @GetMapping("/page")
    public Page<Article> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return MyBatis.springPage(articleService.selectPage(args, page, pageSize));
        });
    }

    @GetMapping("/{id:[\\d]}")
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

    @GetMapping("/view/{id:[\\d]}")
    public long view(@PathVariable Integer id) {
        return bufferService.updateViews(id, 1);
    }

    @PostMapping("/up/{id:[\\d]}")
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

    @PostMapping("/down/{id:[\\d]}")
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

    @GetMapping("/download-params/{id:[\\d]}")
    public String downloadParam(@PathVariable Integer id) {
        long time = System.currentTimeMillis();
        String secret = props.getDownloadSecret();
        String key = Utils.getDownloadKey(id, time, secret);
        return "time=" + time + "&key=" + key;
    }

    @PostMapping("/download/{id:[\\d]}")
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

    @GetMapping("/buffer/{id:[\\d]}")
    public ArticleBuffer buffer(@PathVariable Integer id) {
        ArticleBuffer buffer = bufferService.select(id);
        if (buffer == null) {
            throw new Http404Exception("ArticleBuffer not found. id=" + id);
        }
        return buffer;
    }
}
