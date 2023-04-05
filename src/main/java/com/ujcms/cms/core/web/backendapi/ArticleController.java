package com.ujcms.cms.core.web.backendapi;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Role;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.args.ArticleArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.util.web.Entities;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.Servlets;
import com.ujcms.util.web.Views;
import com.ujcms.util.web.exception.Http400Exception;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import javax.validation.constraints.Max;
import javax.validation.constraints.Min;
import javax.validation.constraints.NotEmpty;
import java.util.Collections;
import java.util.List;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.util.db.MyBatis.springPage;
import static com.ujcms.util.query.QueryUtils.getQueryMap;

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

    public ArticleController(HtmlGenerator generator, ArticleService service) {
        this.generator = generator;
        this.service = service;
    }

    @GetMapping
    @JsonView(Views.List.class)
    @PreAuthorize("hasAnyAuthority('article:list','*')")
    public Page<Article> list(Integer subChannelId, Integer page, Integer pageSize, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        ArticleArgs args = ArticleArgs.of(getQueryMap(request.getQueryString()));
        // 数据范围
        short dataScope = user.getDataScope();
        if (dataScope == Role.DATA_SCOPE_ORG) {
            args.subOrgId(user.getOrgId());
        } else if (dataScope == Role.DATA_SCOPE_SELF) {
            args.userId(user.getId());
        }
        // 文章数据权限
        if (!user.hasAllArticlePermission()) {
            args.inRoleIds(user.fetchRoleIds());
        }
        args.siteId(Contexts.getCurrentSiteId()).subChannelId(subChannelId);
        return springPage(service.selectPage(args, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('article:show','*')")
    public Article show(@PathVariable Integer id) {
        Article bean = service.select(id);
        if (bean == null) {
            throw new Http400Exception("Article not found. ID = " + id);
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
        Entities.copy(bean, article, "siteId", "srcId", "orgId", "userId", "modifiedUserId",
                "withImage", "sticky", "inputType", "type");
        article.setSiteId(site.getId());
        // 如果不为草稿，就设置为已发布。不能为其它状态。
        if (article.getStatus() != Article.STATUS_DRAFT) {
            article.setStatus(Article.STATUS_PUBLISHED);
        }
        service.insert(article, article.getExt(), user.getId(), user.getOrgId(),
                bean.tagNames, bean.getCustoms());
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.articleRelated");
            generator.updateArticleRelatedHtml(article.getSiteId(), user.getId(), taskName, article.getId(), null);
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
            return Responses.notFound("Article not found. ID = " + bean.getId());
        }
        ValidUtils.dataInSite(article.getSiteId(), site.getId());
        Integer origChannelId = article.getChannelId();
        Entities.copy(bean, article, "siteId", "srcId", "orgId", "userId", "modifiedUserId",
                "withImage", "sticky", "inputType", "type", "status");
        service.update(article, article.getExt(), user.getId(),
                bean.tagNames, bean.getCustoms());
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.articleRelated");
            generator.updateArticleRelatedHtml(article.getSiteId(), user.getId(), taskName,
                    article.getId(), origChannelId);
        }
        return Responses.ok();
    }

    public static class ArticleParams extends Article {
        private static final long serialVersionUID = 1L;

        public List<String> tagNames = Collections.emptyList();
    }

    public static class ArticleStickyParams {
        @NotEmpty
        public List<Integer> ids;
        @Min(0)
        @Max(999)
        public short sticky;
    }

    @PutMapping("/sticky")
    @PreAuthorize("hasAnyAuthority('article:sticky','*')")
    @OperationLog(module = "article", operation = "sticky", type = OperationType.UPDATE)
    public ResponseEntity<Body> sticky(@RequestBody @Valid ArticleStickyParams params) {
        Integer siteId = Contexts.getCurrentSiteId();
        params.ids.forEach(id -> {
            Article bean = service.select(id);
            if (bean == null) {
                return;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            bean.setSticky(params.sticky);
            service.update(bean);
        });
        return Responses.ok();
    }

    @PutMapping("/submit")
    @PreAuthorize("hasAnyAuthority('article:submit','*')")
    @OperationLog(module = "article", operation = "submit", type = OperationType.UPDATE)
    public ResponseEntity<Body> submit(@RequestBody List<Integer> ids) {
        Integer siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        ids.forEach(id -> {
            Article bean = service.select(id);
            if (bean == null) {
                return;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.submit(bean, user.getId());
        });
        return Responses.ok();
    }

    @PutMapping("/archive")
    @PreAuthorize("hasAnyAuthority('article:archive','*')")
    @OperationLog(module = "article", operation = "archive", type = OperationType.UPDATE)
    public ResponseEntity<Body> archive(@RequestBody List<Integer> ids) {
        Integer siteId = Contexts.getCurrentSiteId();
        ids.forEach(id -> {
            Article bean = service.select(id);
            if (bean == null) {
                return;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.archive(bean);
        });
        return Responses.ok();
    }

    @PutMapping("/offline")
    @PreAuthorize("hasAnyAuthority('article:offline','*')")
    @OperationLog(module = "article", operation = "offline", type = OperationType.UPDATE)
    public ResponseEntity<Body> offline(@RequestBody List<Integer> ids) {
        Integer siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        ids.forEach(id -> {
            Article bean = service.select(id);
            if (bean == null) {
                return;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.offline(bean, user);
        });
        return Responses.ok();
    }

    @PutMapping("/delete")
    @PreAuthorize("hasAnyAuthority('article:delete','*')")
    @OperationLog(module = "article", operation = "delete", type = OperationType.UPDATE)
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        Integer siteId = Contexts.getCurrentSiteId();
        User user = Contexts.getCurrentUser();
        ids.forEach(id -> {
            Article bean = service.select(id);
            if (bean == null) {
                return;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.delete(bean, user);
        });
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('article:completelyDelete','*')")
    @OperationLog(module = "article", operation = "completelyDelete", type = OperationType.DELETE)
    public ResponseEntity<Body> completelyDelete(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        ids.forEach(id -> {
            Article bean = service.select(id);
            if (bean == null) {
                return;
            }
            ValidUtils.dataInSite(bean.getSiteId(), site.getId());
            service.completelyDelete(bean, user);
        });
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.all");
            generator.updateAllHtml(site.getId(), user.getId(), taskName, site);
        }
        return Responses.ok();
    }
}
