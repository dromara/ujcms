package com.ujcms.cms.core.web.frontend;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.web.support.FrontendController;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;

/**
 * App Controller — serves XHR-fragment templates accessed via {@code /app/<name>}.
 *
 * <p>{@code /app/user} resolves to {@code <theme>/templates/app/user.html}.
 *
 * @author PONY
 */
@FrontendController("frontendAppController")
public class AppController {
    @GetMapping("/app/{name:[\\w-]+}")
    public String app(Site site, @PathVariable String name) {
        return site.assembleTemplate("app/" + name);
    }
}
