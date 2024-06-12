package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.Example;
import com.ujcms.cms.ext.service.ExampleService;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 示例 Frontend Controller
 *
 * @author PONY
 */
// @Controller("frontendExampleController")
public class ExampleController {
    private final SiteResolver siteResolver;
    private final ExampleService service;

    public ExampleController(SiteResolver siteResolver, ExampleService service) {
        this.siteResolver = siteResolver;
        this.service = service;
    }

    private static final String TEMPLATE = "sys_example";
    private static final String TEMPLATE_FORM = "sys_example_form";
    private static final String TEMPLATE_ITEM = "sys_example_item";

    @GetMapping({"/example", "/{subDir:[\\w-]+}/example"})
    public String index(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(TEMPLATE);
    }

    @GetMapping({"/example/create", "/{subDir:[\\w-]+}/example/create"})
    public String create(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(TEMPLATE_FORM);
    }

    @GetMapping({"/example/{id}", "/{subDir:[\\w-]+}/example/{id}"})
    public String show(@PathVariable(required = false) String subDir, @PathVariable Long id,
                       HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        Example example = service.select(id);
        if (example == null) {
            throw new Http404Exception("Example not found. ID=" + id);
        }
        modelMap.put("example", example);
        return site.assembleTemplate(TEMPLATE_ITEM);
    }
}
