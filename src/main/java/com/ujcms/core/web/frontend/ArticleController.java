package com.ujcms.core.web.frontend;

import com.ujcms.core.domain.Article;
import com.ujcms.core.domain.Site;
import com.ujcms.core.exception.Http404Exception;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.web.support.SiteResolver;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

import static com.ujcms.core.support.UrlConstants.ARTICLE;

/**
 * 前台文章 Controller
 *
 * @author PONY
 */
@Controller("frontendArticleController")
public class ArticleController {
    private ArticleService articleService;
    private SiteResolver siteResolver;

    public ArticleController(ArticleService articleService, SiteResolver siteResolver) {
        this.articleService = articleService;
        this.siteResolver = siteResolver;
    }

    @GetMapping({ARTICLE + "/{id}", ARTICLE + "/{id}/{alias:[\\w-]+}",
            "/{subDir:[\\w-]+}" + ARTICLE + "/{id}", "/{subDir:[\\w-]+}" + ARTICLE + "/{id}/{alias:[\\w-]+}"})
    public String channel(@PathVariable int id, @PathVariable(required = false) String subDir, @PathVariable(required = false) String alias,
                          HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        Article article = articleService.select(id);
        if (article == null) {
            throw new Http404Exception("Article not found. id=" + id);
        }
        if (site.getId() != article.getSiteId()) {
            throw new Http404Exception("Article not in site. site id=" + site.getId() + ", article siteId=" + article.getSiteId());
        }
        if (!StringUtils.equals(article.getExt().getAlias(), alias)) {
            return "redirect:" + article.getUrl();
        }
        modelMap.put("article", article);
        modelMap.put("channel", article.getChannel());
        String template = article.getTemplate();
        if (template == null) {
            throw new Http404Exception("Article template not set. id=" + id);
        }
        return template;
    }
}
