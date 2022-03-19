package com.ujcms.core.web.backendapi;

import com.ofwise.util.db.MyBatis;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Entities;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
import com.ofwise.util.web.Servlets;
import com.ofwise.util.web.exception.Http400Exception;
import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.User;
import com.ujcms.core.generator.HtmlGenerator;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.service.UserService;
import com.ujcms.core.support.Contexts;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
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
import java.util.List;
import java.util.Map;

import static com.ujcms.core.support.Constants.validPage;
import static com.ujcms.core.support.Constants.validPageSize;
import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 文章 Controller
 *
 * @author PONY
 */
@RestController("backendArticleController")
@RequestMapping(BACKEND_API + "/core/article")
public class ArticleController {
    private HtmlGenerator generator;
    private ArticleService service;
    private UserService userService;

    public ArticleController(HtmlGenerator generator, ArticleService service, UserService userService) {
        this.generator = generator;
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @RequiresPermissions("article:list")
    public Page<Article> list(Integer channelId, Integer page, Integer pageSize, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        queryMap.put("EQ_siteId_Int", String.valueOf(site.getId()));
        return MyBatis.toPage(service.selectPage(queryMap, null, channelId, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @RequiresPermissions("article:show")
    public Article show(@PathVariable Integer id) {
        Article bean = service.select(id);
        if (bean == null) {
            throw new Http400Exception("Article not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("article:create")
    public ResponseEntity<Body> create(@RequestBody @Valid Article bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        User user = userService.select(userId);
        if (user == null) {
            return Responses.notFound("User not found. ID = " + userId);
        }
        Article article = Entities.copy(bean, new Article());
        article.setSiteId(site.getId());
        service.insert(article, article.getExt(), userId, user.getOrgId(),
                bean.getCustomList(), bean.getImageList(), bean.getFileList());
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.articleRelated");
            generator.updateArticleRelatedHtml(article.getSiteId(), userId, taskName, article.getId(), null);
        }
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("article:update")
    public ResponseEntity<Body> update(@RequestBody @Valid Article bean, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        Article article = service.select(bean.getId());
        if (article == null) {
            return Responses.notFound("Article not found. ID = " + bean.getId());
        }
        Integer origChannelId = article.getChannelId();
        Entities.copy(bean, article, "userId");
        service.update(article, article.getExt(), userId,
                bean.getCustomList(), bean.getImageList(), bean.getFileList());
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.articleRelated");
            generator.updateArticleRelatedHtml(article.getSiteId(), userId, taskName, article.getId(), origChannelId);
        }
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("article:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        service.delete(ids);
        if (site.getHtml().isAuto()) {
            String taskName = Servlets.getMessage(request, "task.html.all");
            generator.updateAllHtml(site.getId(), userId, taskName, site);
        }
        return Responses.ok();
    }
}
