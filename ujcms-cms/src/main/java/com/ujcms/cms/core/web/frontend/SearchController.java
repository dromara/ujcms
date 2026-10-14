package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.FrontendController;
import org.springframework.web.bind.annotation.GetMapping;

/**
 * 前台搜索 Controller
 *
 * @author PONY
 */
@FrontendController("frontendSearchController")
public class SearchController {
    private static final String TEMPLATE = "sys_search";

    @GetMapping("/search")
    public String search(Site site) {
        return site.assembleTemplate(TEMPLATE);
    }
}
