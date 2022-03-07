package com.ujcms.core.web.support;

import com.ujcms.core.domain.Site;
import com.ofwise.util.web.exception.Http404Exception;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.service.SiteQueryService;
import com.ujcms.core.support.Contexts;
import org.apache.commons.lang3.StringUtils;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.lang.Nullable;

import javax.servlet.http.HttpServletRequest;
import java.util.Optional;

/**
 * 站点解析器
 *
 * @author PONY
 */
public class SiteResolver {
    private static Logger logger = LoggerFactory.getLogger(SiteResolver.class);
    private SiteQueryService siteService;
    private GlobalService globalService;

    public SiteResolver(SiteQueryService siteService, GlobalService globalService) {
        this.siteService = siteService;
        this.globalService = globalService;
    }

    public Site resolve(HttpServletRequest request, @Nullable String subDir) {
        if (StringUtils.isNotBlank(subDir)) {
            Site site = Optional.ofNullable(siteService.findBySubDir(subDir))
                    .orElseThrow(() -> {
                        logger.warn("Site sub-dir not exist: " + request.getRequestURL());
                        return new Http404Exception("error.siteSubDirNotExist", subDir);
                    });
            Contexts.setCurrentSite(site);
            return site;
        }
        return resolve(request);
    }

    public Site resolve(HttpServletRequest request) {
        String domain = request.getServerName();
        Site site = siteService.findByDomain(domain);
        if (site == null) {
            int defaultSiteId = globalService.getUnique().getDefaultSiteId();
            site = Optional.ofNullable(siteService.select(defaultSiteId))
                    .orElseThrow(() -> new IllegalStateException("Default site not found: " + defaultSiteId));
        }
        Contexts.setCurrentSite(site);
        return site;
    }
}
