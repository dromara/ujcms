package com.ujcms.cms.core.web.backendapi;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.*;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.lucene.domain.EsArticle;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ErrorWordService;
import com.ujcms.cms.core.service.SensitiveWordService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.service.args.ErrorWordArgs;
import com.ujcms.cms.core.service.args.SensitiveWordArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.commons.db.order.MoveOrderParams;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Responses.Body;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.Views;
import com.ujcms.commons.web.exception.Http400Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.io.Serializable;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

import static com.ujcms.cms.core.domain.Article.STATUS_DRAFT;
import static com.ujcms.cms.core.domain.Article.STATUS_PUBLISHED;
import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.commons.db.MyBatis.springPage;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 文章 Controller
 *
 * @author PONY
 */
@RestController("backendArticleController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/article")
public class ArticleController {
    private final HtmlGenerator generator;
    private final ArticleService service;
    private final ErrorWordService errorWordService;
    private final SensitiveWordService sensitiveWordService;

    public ArticleController(HtmlGenerator generator, ArticleService service,
                             ErrorWordService errorWordService, SensitiveWordService sensitiveWordService) {
        this.generator = generator;
        this.service = service;
        this.errorWordService = errorWordService;
        this.sensitiveWordService = sensitiveWordService;
    }

    @GetMapping
    @JsonView(Views.List.class)
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public Page<Article> list(Integer subChannelId, Integer page, Integer pageSize, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        ArticleArgs args = ArticleArgs.of(getQueryMap(request.getQueryString()));
        dataPermission(args, user);
        args.siteId(site.getId());
        args.channelAncestorId(subChannelId);
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("reject-count")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public long rejectCount() {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        ArticleArgs args = ArticleArgs.of();
        dataPermission(args, user);
        args.siteId(site.getId());
        args.status(Collections.singleton(Article.STATUS_REJECTED));
        return service.count(args);
    }

    private void dataPermission(ArticleArgs args, User user) {
        // 数据范围
        short dataScope = user.getDataScope();
        if (dataScope == Role.DATA_SCOPE_ORG) {
            args.orgAncestorId(user.getOrgId());
        } else if (dataScope == Role.DATA_SCOPE_SELF) {
            args.userId(user.getId());
        }
        // 文章数据权限
        if (!user.hasAllArticlePermission()) {
            args.inRoleIds(user.fetchRoleIds());
        }
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('article:show','*')")
    public Article show(@PathVariable Integer id) {
        Article bean = service.select(id);
        if (bean == null) {
            throw new Http400Exception(ARTICLE_NOT_FOUND + id);
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('article:create','*')")
    @OperationLog(module = "article", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Body> create(@RequestBody @Valid ArticleParams bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Article article = new Article();
        Entities.copy(bean, article, EXCLUDE_FIELDS);
        article.setSiteId(site.getId());
        // 如果不为草稿，就设置为已发布。不能为其它状态。
        article.adjustStatus(bean.getStatus() == STATUS_DRAFT ? STATUS_DRAFT : STATUS_PUBLISHED);
        validateBean(article.getExt());
        service.insert(article, article.getExt(), user.getId(), user.getOrgId(),
                bean.getTagNames(), bean.getCustoms());
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(article.getSiteId(), user.getId(), taskName,
                    Collections.singletonList(article), null);
        }
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('article:update','*')")
    @OperationLog(module = "article", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Body> update(@RequestBody @Valid ArticleParams bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Article article = service.select(bean.getId());
        if (article == null) {
            return Responses.notFound(ARTICLE_NOT_FOUND + bean.getId());
        }
        ValidUtils.dataInSite(article.getSiteId(), site.getId());
        Integer origChannelId = article.getChannelId();
        Entities.copy(bean, article, EXCLUDE_FIELDS);
        validateBean(article.getExt());
        article.adjustStatus();
        service.update(article, article.getExt(), user.getId(), bean.getTagNames(), bean.getCustoms());
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(article.getSiteId(), user.getId(), taskName,
                    Collections.singletonList(article), origChannelId);
        }
        return Responses.ok();
    }

    @PostMapping("update-order")
    @PreAuthorize("hasAnyAuthority('article:updateOrder','*')")
    @OperationLog(module = "article", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Body> moveOrder(@RequestBody @Valid MoveOrderParams params, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        Article fromArticle = service.select(params.getFromId());
        Article toArticle = service.select(params.getToId());
        if (fromArticle == null) {
            throw new Http400Exception(ARTICLE_NOT_FOUND + params.getFromId());
        }
        if (toArticle == null) {
            throw new Http400Exception(ARTICLE_NOT_FOUND + params.getToId());
        }
        ValidUtils.dataInSite(fromArticle.getSiteId(), site.getId());
        ValidUtils.dataInSite(toArticle.getSiteId(), site.getId());
        service.moveOrder(fromArticle.getId(), toArticle.getId());
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName,
                    Arrays.asList(fromArticle, toArticle), null);
        }
        return Responses.ok();
    }

    public static class ArticleParams extends Article {
        private static final long serialVersionUID = 1L;

        private List<String> tagNames = Collections.emptyList();

        @NonNull
        @Override
        public List<String> getTagNames() {
            return tagNames;
        }

        public void setTagNames(List<String> tagNames) {
            this.tagNames = tagNames;
        }
    }

    public static class ArticleStickyParams implements Serializable {
        private static final long serialVersionUID = 1;
        @NotEmpty
        private List<Integer> ids;
        @Min(0)
        @Max(999)
        private short sticky;
        private OffsetDateTime stickyDate;

        public List<Integer> getIds() {
            return ids;
        }

        public void setIds(List<Integer> ids) {
            this.ids = ids;
        }

        public short getSticky() {
            return sticky;
        }

        public void setSticky(short sticky) {
            this.sticky = sticky;
        }

        public OffsetDateTime getStickyDate() {
            return stickyDate;
        }

        public void setStickyDate(OffsetDateTime stickyDate) {
            this.stickyDate = stickyDate;
        }
    }

    @PutMapping("/sticky")
    @PreAuthorize("hasAnyAuthority('article:sticky','*')")
    @OperationLog(module = "article", operation = "sticky", type = OperationType.UPDATE)
    public ResponseEntity<Body> sticky(@RequestBody @Valid ArticleStickyParams params, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = new ArrayList<>(params.ids.size());
        for (Integer id : params.ids) {
            Article bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            articles.add(bean);
            bean.setSticky(params.sticky);
            bean.setStickyDate(params.stickyDate);
            service.update(bean);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    @PutMapping("/submit")
    @PreAuthorize("hasAnyAuthority('article:submit','*')")
    @OperationLog(module = "article", operation = "submit", type = OperationType.UPDATE)
    public ResponseEntity<Body> submit(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            Article bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            articles.add(bean);
            service.submit(bean, user.getId());
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    @PutMapping("/archive")
    @PreAuthorize("hasAnyAuthority('article:archive','*')")
    @OperationLog(module = "article", operation = "archive", type = OperationType.UPDATE)
    public ResponseEntity<Body> archive(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            Article bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            articles.add(bean);
            service.archive(bean);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    @PutMapping("/offline")
    @PreAuthorize("hasAnyAuthority('article:offline','*')")
    @OperationLog(module = "article", operation = "offline", type = OperationType.UPDATE)
    public ResponseEntity<Body> offline(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            Article bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            articles.add(bean);
            service.offline(bean, user);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    @PutMapping("/delete")
    @PreAuthorize("hasAnyAuthority('article:delete','*')")
    @OperationLog(module = "article", operation = "delete", type = OperationType.UPDATE)
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            Article bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            articles.add(bean);
            service.delete(bean, user);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('article:completelyDelete','*')")
    @OperationLog(module = "article", operation = "completelyDelete", type = OperationType.DELETE)
    public ResponseEntity<Body> completelyDelete(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        List<Article> articles = new ArrayList<>(ids.size());
        for (Integer id : ids) {
            Article bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            articles.add(bean);
            service.completelyDelete(bean, user);
        }
        if (site.getHtml().isEnabledAndAuto()) {
            String taskName = Servlets.getMessage(request, TASK_HTML_ARTICLE_RELATED);
            generator.updateArticleRelatedHtml(site.getId(), user.getId(), taskName, articles, null);
        }
        return Responses.ok();
    }

    @GetMapping("title-similarity")
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public List<EsArticle> titleSimilarity(double similarity, String title, Integer excludeId) {
        Site site = Contexts.getCurrentSite();
        return service.titleSimilarity(similarity, title, site.getId(), excludeId);
    }

    private void validateBean(ArticleExt ext) {
        String text = ext.getText();
        if (StringUtils.isNotBlank(text)) {
            for (ErrorWord bean : errorWordService.selectList(ErrorWordArgs.of().enabled(true))) {
                if (text.contains(bean.getName())) {
                    throw new Http400Exception("error word: " + bean.getName());
                }
            }
            for (SensitiveWord bean : sensitiveWordService.selectList(SensitiveWordArgs.of().enabled(true))) {
                if (text.contains(bean.getName())) {
                    throw new Http400Exception("sensitive word: " + bean.getName());
                }
            }
        }
    }

    private static final String[] EXCLUDE_FIELDS = {"siteId", "srcId", "orgId", "userId", "modifiedUserId",
            "withImage", "sticky", "inputType", "type", "status", "created", "modified", "stickyDate",
            "processInstanceId", "rejectReason", "baiduPush", "staticFile", "mobileStaticFile"};

    private static final String ARTICLE_NOT_FOUND = "Article not found. ID = ";
    private static final String TASK_HTML_ARTICLE_RELATED = "task.html.articleRelated";
}
