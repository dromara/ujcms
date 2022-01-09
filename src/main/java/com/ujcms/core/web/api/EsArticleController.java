package com.ujcms.core.web.api;

import com.ofwise.util.query.OffsetLimitRequest;
import com.ofwise.util.query.QueryUtils;
import com.ujcms.core.lucene.ArticleLucene;
import com.ujcms.core.lucene.domain.EsArticle;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.web.directive.EsArticleListDirective;
import com.ujcms.core.web.support.Directives;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.API;

/**
 * 文章前台(全文检索) 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(API + "/article/es")
public class EsArticleController {
    private ArticleLucene articleLucene;
    private GlobalService globalService;

    public EsArticleController(ArticleLucene articleLucene, GlobalService globalService) {
        this.articleLucene = articleLucene;
        this.globalService = globalService;
    }

    private Page<EsArticle> query(Map<String, String> params, Pageable pageable) {
        Integer defaultSiteId = globalService.getUnique().getDefaultSiteId();
        return EsArticleListDirective.query(params, defaultSiteId, pageable, articleLucene);
    }

    @GetMapping
    public List<EsArticle> list(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        Page<EsArticle> pagedList = query(params, OffsetLimitRequest.of(offset, limit));
        return pagedList.getContent();
    }

    @GetMapping("/page")
    public Page<EsArticle> page(HttpServletRequest request) {
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // spring-data 的 page 从 0 开始
        int page = Directives.getPage(params) - 1;
        int pageSize = Directives.getPageSize(params);
        return query(params, PageRequest.of(page, pageSize));
    }
}
