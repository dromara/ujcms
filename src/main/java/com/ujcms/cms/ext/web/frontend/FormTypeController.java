package com.ujcms.cms.ext.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.Form;
import com.ujcms.cms.ext.domain.FormType;
import com.ujcms.cms.ext.service.FormService;
import com.ujcms.cms.ext.service.FormTypeService;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.apache.commons.lang3.StringUtils;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

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
