package com.ujcms.cms.core.web.api;

import com.ujcms.util.query.OffsetLimitRequest;
import com.ujcms.util.query.QueryUtils;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.lucene.ArticleLucene;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.web.directive.EsArticleListDirective;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import java.util.List;
import java.util.Map;

/**
 * 文章前台(全文检索) 接口
 *
 * @author PONY
 */
@RestController
@RequestMapping(UrlConstants.API + "/article/es")
public class EsArticleController {
    private ArticleLucene articleLucene;
    private ConfigService configService;

    public EsArticleController(ArticleLucene articleLucene, ConfigService configService) {
        this.articleLucene = articleLucene;
        this.configService = configService;
    }

    private Page<EsArticle> query(Map<String, String> params, Pageable pageable) {
        Integer defaultSiteId = configService.getUnique().getDefaultSiteId();
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
