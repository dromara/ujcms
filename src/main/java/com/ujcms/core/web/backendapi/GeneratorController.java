package com.ujcms.core.web.backendapi;

import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Responses.Body;
import com.ujcms.core.service.ArticleService;
import com.ujcms.core.support.Contexts;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 生成器 Controller
 *
 * @author PONY
 */
@RestController("backendGeneratorController")
@RequestMapping(BACKEND_API + "/core/generator")
public class GeneratorController {
    private ArticleService service;

    public GeneratorController(ArticleService service) {
        this.service = service;
    }

    @PostMapping("fulltext-reindex-all")
    @RequiresPermissions("generator:fulltext:reindexAll")
    public ResponseEntity<Body> fulltextReindexAll() {
        service.reindex();
        return Responses.ok();
    }

    @PostMapping("fulltext-reindex-site")
    @RequiresPermissions("generator:fulltext:reindexSite")
    public ResponseEntity<Body> fulltextSiteReindex() {
        service.reindex(Contexts.getCurrentSiteId());
        return Responses.ok();
    }
}