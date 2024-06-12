package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import javax.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * Tag Controller
 *
 * @author PONY
 */
@Controller("frontendTagController")
public class TagController {
    private final SiteResolver siteResolver;
    private final TagService service;

    public TagController(SiteResolver siteResolver, TagService service) {
        this.siteResolver = siteResolver;
        this.service = service;
    }

    private static final String TEMPLATE = "sys_tag";
    private static final String TEMPLATE_ITEM = "sys_tag_item";

    @GetMapping({"/tag", "/{subDir:[\\w-]+}/tag"})
    public String index(@PathVariable(required = false) String subDir, HttpServletRequest request) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(TEMPLATE);
    }

    @GetMapping({"/tag/{id}", "/{subDir:[\\w-]+}/tag/{id}"})
    public String show(@PathVariable(required = false) String subDir, @PathVariable Long id,
                       HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        Tag tag = service.select(id);
        if (tag == null) {
            throw new Http404Exception("Tag not found. ID=" + id);
        }
        modelMap.put("tag", tag);
        return site.assembleTemplate(TEMPLATE_ITEM);
    }

}
