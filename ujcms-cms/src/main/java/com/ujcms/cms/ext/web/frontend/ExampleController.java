package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.ext.domain.Example;
import com.ujcms.cms.ext.service.ExampleService;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * 示例 Frontend Controller
 *
 * @author PONY
 */
// @FrontendController("frontendExampleController")
public class ExampleController {
    private final ExampleService service;

    public ExampleController(ExampleService service) {
        this.service = service;
    }

    private static final String TEMPLATE = "sys_example";
    private static final String TEMPLATE_FORM = "sys_example_form";
    private static final String TEMPLATE_ITEM = "sys_example_item";

    @GetMapping("/example")
    public String index(Site site) {
        return site.assembleTemplate(TEMPLATE);
    }

    @GetMapping("/example/create")
    public String create(Site site) {
        return site.assembleTemplate(TEMPLATE_FORM);
    }

    @GetMapping("/example/{id}")
    public String show(Site site, @PathVariable Long id, Map<String, Object> modelMap) {
        Example example = service.select(id);
        if (example == null) {
            throw new Http404Exception("Example not found. ID=" + id);
        }
        modelMap.put("example", example);
        return site.assembleTemplate(TEMPLATE_ITEM);
    }
}
