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

import javax.servlet.http.HttpServletRequest;
import java.util.Map;
import java.util.Optional;

/**
 * 前台表单 Controller
 *
 * @author PONY
 */
@Controller("frontendFormController")
public class FormController {
    private final FormTypeService typeService;
    private final FormService service;
    private final SiteResolver siteResolver;

    public FormController(FormTypeService typeService, FormService service, SiteResolver siteResolver) {
        this.typeService = typeService;
        this.service = service;
        this.siteResolver = siteResolver;
    }

    @GetMapping({"/form-list/{id}", "/{subDir:[\\w-]+}/form-list/{id}"})
    public String list(@PathVariable Long id, @PathVariable(required = false) String subDir,
                       HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        FormType formType = Optional.ofNullable(typeService.select(id))
                .orElseThrow(() -> new Http404Exception("FormType not found. ID: " + id));
        if (Boolean.FALSE.equals(formType.getViewable())) {
            throw new Http400Exception("FormType is not viewable. ID: " + formType.getId());
        }
        String listTemplate = Optional.ofNullable(formType.getListTemplate()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new Http404Exception("FormType listTemplate is null. ID: " + id));
        modelMap.put("formType", formType);
        return site.assembleTemplate(listTemplate);
    }

    @GetMapping({"/form-item/{id}", "/{subDir:[\\w-]+}/form-item/{id}"})
    public String item(@PathVariable Long id, @PathVariable(required = false) String subDir,
                       HttpServletRequest request, Map<String, Object> modelMap) {
        Site site = siteResolver.resolve(request, subDir);
        Form form = Optional.ofNullable(service.select(id))
                .orElseThrow(() -> new Http404Exception("Form not found. ID: " + id));
        FormType formType = form.getType();
        if (Boolean.FALSE.equals(formType.getViewable())) {
            throw new Http400Exception("FormType is not viewable. ID: " + formType.getId());
        }
        String listTemplate = Optional.ofNullable(formType.getItemTemplate()).filter(StringUtils::isNotBlank)
                .orElseThrow(() -> new Http404Exception("FormType itemTemplate is null. ID: " + formType.getId()));
        modelMap.put("formType", formType);
        modelMap.put("form", form);
        return site.assembleTemplate(listTemplate);
    }
}
