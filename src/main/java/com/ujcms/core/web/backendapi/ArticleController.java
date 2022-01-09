package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.Site;
import com.ujcms.core.domain.User;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.service.UserService;
import com.ujcms.core.support.Contexts;
import com.ofwise.util.db.MyBatis;
import com.ofwise.util.query.QueryUtils;
import com.ofwise.util.web.Entities;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
import org.apache.shiro.authz.annotation.RequiresPermissions;
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
    private ArticleService service;
    private UserService userService;

    public ArticleController(ArticleService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    @GetMapping
    @RequiresPermissions("article:list")
    public Object list(Integer channelId, Integer page, Integer pageSize, HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Map<String, Object> queryMap = QueryUtils.getQueryMap(request.getQueryString());
        queryMap.put("EQ_siteId_Int", String.valueOf(site.getId()));
        return MyBatis.toPage(service.selectPage(queryMap, null, channelId, validPage(page), validPageSize(pageSize)));
    }

    @GetMapping("{id}")
    @RequiresPermissions("article:show")
    public Object show(@PathVariable int id) {
        Article bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Article not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @RequiresPermissions("article:create")
    public ResponseEntity<Body> create(@RequestBody Article bean) {
        bean.setSiteId(1);
        Integer userId = Contexts.getCurrentUserId();
        User user = userService.select(userId);
        if (user == null) {
            return Responses.notFound("User not found. ID = " + userId);
        }
        Article article = Entities.copy(bean, new Article());
        service.insert(article, article.getExt(), userId, user.getOrgId(),
                bean.getCustomList(), bean.getImageList(), bean.getFileList());
        return Responses.ok();
    }

    @PutMapping
    @RequiresPermissions("article:update")
    public ResponseEntity<Body> update(@RequestBody Article bean) {
        Integer userId = Contexts.getCurrentUserId();
        Article article = service.select(bean.getId());
        if (article == null) {
            return Responses.notFound("Article not found. ID = " + bean.getId());
        }
        Entities.copy(bean, article, "userId");
        service.update(article, article.getExt(), userId,
                bean.getCustomList(), bean.getImageList(), bean.getFileList());
        return Responses.ok();
    }

    @DeleteMapping
    @RequiresPermissions("article:delete")
    public ResponseEntity<Body> delete(@RequestBody List<Integer> ids) {
        service.delete(ids);
        return Responses.ok();
    }
}