package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.Tag;
import com.ujcms.cms.core.service.TagService;
import com.ujcms.cms.core.web.support.FrontendController;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import java.util.Map;

/**
 * Tag Controller
 *
 * @author PONY
 */
@FrontendController("frontendTagController")
public class TagController {
    private final TagService service;

    public TagController(TagService service) {
        this.service = service;
    }

    private static final String TEMPLATE = "sys_tag";
    private static final String TEMPLATE_ITEM = "sys_tag_item";

    @GetMapping("/tag")
    public String index(Site site) {
        return site.assembleTemplate(TEMPLATE);
    }

    @GetMapping("/tag/{id}")
    public String show(Site site, @PathVariable Long id, Map<String, Object> modelMap) {
        Tag tag = service.select(id);
        if (tag == null) {
            throw new Http404Exception("Tag not found. ID=" + id);
        }
        modelMap.put("tag", tag);
        return site.assembleTemplate(TEMPLATE_ITEM);
    }

}
