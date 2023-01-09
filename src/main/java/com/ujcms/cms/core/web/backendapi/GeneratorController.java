package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.generator.HtmlGenerator;
import com.ujcms.cms.core.generator.HtmlService;
import com.ujcms.cms.core.generator.LuceneGenerator;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Responses.Body;
import com.ujcms.util.web.Servlets;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
    private final HtmlService htmlService;
    private final HtmlGenerator htmlGenerator;
    private final LuceneGenerator luceneGenerator;

    public GeneratorController(HtmlService htmlService, HtmlGenerator htmlGenerator, LuceneGenerator luceneGenerator) {
        this.htmlService = htmlService;
        this.htmlGenerator = htmlGenerator;
        this.luceneGenerator = luceneGenerator;
    }

    @PostMapping("fulltext-reindex-all")
    @PreAuthorize("hasAnyAuthority('generator:fulltext:reindexAll','*')")
    @OperationLog(module = "fulltext", operation = "reindexAll", type = OperationType.UPDATE)
    public ResponseEntity<Body> fulltextReindexAll(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        String taskName = Servlets.getMessage(request, "task.fulltext.all");
        luceneGenerator.reindex(site.getId(), user.getId(), taskName);
        return Responses.ok();
    }

    @PostMapping("fulltext-reindex-site")
    @PreAuthorize("hasAnyAuthority('generator:fulltext:reindexSite','*')")
    @OperationLog(module = "fulltext", operation = "reindexSite", type = OperationType.UPDATE)
    public ResponseEntity<Body> fulltextSiteReindex(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        String taskName = Servlets.getMessage(request, "task.fulltext.site");
        luceneGenerator.reindex(site.getId(), user.getId(), taskName, site.getId());
        return Responses.ok();
    }

    @PostMapping("html-all")
    @PreAuthorize("hasAnyAuthority('generator:html','*')")
    @OperationLog(module = "html", operation = "updateAll", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateAllHtml(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        String taskName = Servlets.getMessage(request, "task.html.all");
        htmlGenerator.updateAllHtml(site.getId(), user.getId(), taskName, site);
        return Responses.ok();
    }

    @PostMapping("html-home")
    @PreAuthorize("hasAnyAuthority('generator:html','*')")
    @OperationLog(module = "html", operation = "updateHome", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateHomeHtml() {
        Site site = Contexts.getCurrentSite();
        htmlService.updateHomeHtml(site);
        return Responses.ok();
    }

    @PostMapping("html-channel")
    @PreAuthorize("hasAnyAuthority('generator:html','*')")
    @OperationLog(module = "html", operation = "updateChannel", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateChannelHtml(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        String taskName = Servlets.getMessage(request, "task.html.channel");
        htmlGenerator.updateChannelHtml(site.getId(), user.getId(), taskName, site);
        return Responses.ok();
    }

    @PostMapping("html-article")
    @PreAuthorize("hasAnyAuthority('generator:html','*')")
    @OperationLog(module = "html", operation = "updateArticle", type = OperationType.UPDATE)
    public ResponseEntity<Body> updateArticleHtml(HttpServletRequest request) {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        String taskName = Servlets.getMessage(request, "task.html.article");
        htmlGenerator.updateArticleHtml(site.getId(), user.getId(), taskName, site);
        return Responses.ok();
    }

}