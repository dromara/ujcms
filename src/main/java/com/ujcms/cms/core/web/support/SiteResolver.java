package com.ujcms.cms.core.web.support;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.commons.web.exception.Http404Exception;
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
    private static final Logger logger = LoggerFactory.getLogger(SiteResolver.class);
    private final SiteService siteService;
    private final ConfigService configService;

    public SiteResolver(SiteService siteService, ConfigService configService) {
        this.siteService = siteService;
        this.configService = configService;
    }

    public Site resolve(HttpServletRequest request, @Nullable String subDir) {
        if (StringUtils.isNotBlank(subDir)) {
            Site site = Optional.ofNullable(siteService.findBySubDir(subDir)).orElseThrow(() -> {
                logger.warn("Site sub-dir not exist: {}", request.getRequestURL());
                return new Http404Exception("error.siteSubDirNotExist", subDir);
            });
            Contexts.setCurrentSite(site);
            if (site.isDisabled()) {
                throw new SiteDisabledException("error.siteDisabled", site.getName(), String.valueOf(site.getId()));
            }
            return site;
        }
        return resolve(request);
    }

    public Site resolve(HttpServletRequest request, @Nullable Long siteId) {
        if (siteId != null) {
            Site site = Optional.ofNullable(siteService.select(siteId)).orElseThrow(() -> {
                logger.warn("Site id not exist: {}", request.getRequestURL());
                return new Http404Exception("error.siteIdNotExist", String.valueOf(siteId));
            });
            Contexts.setCurrentSite(site);
            if (site.isDisabled()) {
                throw new SiteDisabledException("error.siteDisabled", site.getName(), String.valueOf(site.getId()));
            }
            return site;
        }
        return resolve(request);
    }

    public Site resolve(HttpServletRequest request) {
        String domain = request.getServerName();
        Site site = siteService.findByDomain(domain);
        if (site == null) {
            Long defaultSiteId = configService.getUnique().getDefaultSiteId();
            site = Optional.ofNullable(siteService.select(defaultSiteId))
                    .orElseThrow(() -> new IllegalStateException("Default site not found: " + defaultSiteId));
        }
        Contexts.setCurrentSite(site);
        if (site.isDisabled()) {
            throw new SiteDisabledException("error.siteDisabled", site.getName(), String.valueOf(site.getId()));
        }
        return site;
    }
}
