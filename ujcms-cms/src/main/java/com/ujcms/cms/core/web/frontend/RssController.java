package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.FrontendController;
import org.springframework.web.bind.annotation.GetMapping;

import jakarta.servlet.http.HttpServletResponse;

/**
 * 前台 RSS Controller
 *
 * @author PONY
 */
@FrontendController("frontendRssController")
public class RssController {
    private static final String TEMPLATE = "sys_rss";

    @GetMapping("/rss")
    public String rss(Site site, HttpServletResponse response) {
        response.setContentType("application/xml;charset=UTF-8");
        return site.assembleTemplate(TEMPLATE);
    }
}
