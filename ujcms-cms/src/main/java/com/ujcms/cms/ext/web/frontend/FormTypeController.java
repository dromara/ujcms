package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.SiteResolver;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;

/**
 * 前台表单类型 Controller
 *
 * @author PONY
 */
@Controller("frontendFormTypeController")
public class FormTypeController {
    private final SiteResolver siteResolver;

    public FormTypeController(SiteResolver siteResolver) {
        this.siteResolver = siteResolver;
    }
    private static final String TEMPLATE = "sys_form_type";

    @GetMapping({"/form-type", "/{subDir:[\\w-]+}/form-type"})
    public String list(@PathVariable(required = false) String subDir,
                       HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        return site.assembleTemplate(TEMPLATE);
    }
}
