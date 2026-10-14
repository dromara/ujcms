package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Frontends;
import com.ujcms.cms.core.web.support.FrontendController;
import org.springframework.web.bind.annotation.GetMapping;

import java.util.Map;

/**
 * 前台首页 Controller
 *
 * @author PONY
 */
@FrontendController("frontendHomepageController")
public class HomepageController {
    @GetMapping("/")
    public String home(Site site, Map<String, Object> modelMap) {
        modelMap.put(Frontends.PAGE_SIZE, site.getPageSize());
        modelMap.put("isHome", true);
        // 渲染上下文钩子（页面构成 §10.2）：插槽标签按未组装模板名解析页面清单
        modelMap.put(Frontends.TEMPLATE_NAME, site.getTemplateName());
        return site.getTemplate();
    }
}
