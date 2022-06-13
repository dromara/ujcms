package com.ujcms.cms.core.web.backendapi;

import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.Servlets;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.generator.LuceneGenerator;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import javax.servlet.http.HttpServletRequest;

/**
 * 生成器 Controller
 *
 * @author PONY
 */
@RestController("backendGeneratorController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/generator")
public class GeneratorController {
    private HtmlService htmlService;
    private HtmlGenerator htmlGenerator;
    private LuceneGenerator luceneGenerator;

    public GeneratorController(HtmlService htmlService, HtmlGenerator htmlGenerator, LuceneGenerator luceneGenerator) {
        this.htmlService = htmlService;
        this.htmlGenerator = htmlGenerator;
        this.luceneGenerator = luceneGenerator;
    }

    @PostMapping("fulltext-reindex-all")
    @RequiresPermissions("generator:fulltext:reindexAll")
    public ResponseEntity<Body> fulltextReindexAll(HttpServletRequest request) {
        Integer siteId = Contexts.getCurrentSiteId();
        Integer userId = Contexts.getCurrentUserId();
        String taskName = Servlets.getMessage(request, "task.fulltext.all");
        luceneGenerator.reindex(siteId, userId, taskName);
        return Responses.ok();
    }

    @PostMapping("fulltext-reindex-site")
    @RequiresPermissions("generator:fulltext:reindexSite")
    public ResponseEntity<Body> fulltextSiteReindex(HttpServletRequest request) {
        Integer siteId = Contexts.getCurrentSiteId();
        Integer userId = Contexts.getCurrentUserId();
        String taskName = Servlets.getMessage(request, "task.fulltext.site");
        luceneGenerator.reindex(siteId, userId, taskName, siteId);
        return Responses.ok();
    }

    @PostMapping("html-all")
    @RequiresPermissions("generator:html")
    public ResponseEntity<Body> updateAllHtml(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        String taskName = Servlets.getMessage(request, "task.html.all");
        htmlGenerator.updateAllHtml(site.getId(), userId, taskName, site);
        return Responses.ok();
    }

    @PostMapping("html-home")
    @RequiresPermissions("generator:html")
    public ResponseEntity<Body> updateHomeHtml() {
        Site site = Contexts.getCurrentSite();
        htmlService.updateHomeHtml(site);
        return Responses.ok();
    }

    @PostMapping("html-channel")
    @RequiresPermissions("generator:html")
    public ResponseEntity<Body> updateChannelHtml(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        String taskName = Servlets.getMessage(request, "task.html.channel");
        htmlGenerator.updateChannelHtml(site.getId(), userId, taskName, site);
        return Responses.ok();
    }

    @PostMapping("html-article")
    @RequiresPermissions("generator:html")
    public ResponseEntity<Body> updateArticleHtml(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        String taskName = Servlets.getMessage(request, "task.html.article");
        htmlGenerator.updateArticleHtml(site.getId(), userId, taskName, site);
        return Responses.ok();
    }

}