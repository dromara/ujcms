package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.core.io.ResourceLoader;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.support.Constants.validLimit;
import static com.ujcms.cms.core.support.Constants.validOffset;
import static com.ujcms.commons.query.QueryUtils.getQueryMap;

/**
 * 站点 Controller
 *
 * @author PONY
 */
@RestController("backendSiteController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/site")
public class SiteController {
    private final SiteService service;
    private final ResourceLoader resourceLoader;

    public SiteController(SiteService service, ResourceLoader resourceLoader) {
        this.service = service;
        this.resourceLoader = resourceLoader;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('site:list','*')")
    public List<Site> list(@RequestParam(defaultValue = "false") boolean current,
                           @RequestParam(defaultValue = "false") boolean currentOrg,
                           @RequestParam(defaultValue = "true") boolean isIncludeChildren,
                           @RequestParam(defaultValue = "false") boolean isIncludeSelf,
                           @Nullable Long parentId, @Nullable Long fullOrgId,
                           @Nullable Integer offset, @Nullable Integer limit, HttpServletRequest request) {
        Site currentSite = Contexts.getCurrentSite();
        if (current && parentId == null) {
            parentId = currentSite.getId();
        }
        if (currentOrg && fullOrgId == null) {
            fullOrgId = currentSite.getOrgId();
        }
        SiteArgs args = SiteArgs.of(getQueryMap(request.getQueryString()));
        args.fullOrgId(fullOrgId);
        if (isIncludeChildren) {
            args.ancestorId(parentId);
        } else if (parentId == null) {
            args.parentIdIsNull();
        } else if (isIncludeSelf) {
            args.parentIdAndSelf(parentId);
        } else {
            args.parentId(parentId);
        }
        return service.selectList(args, validOffset(offset), validLimit(limit));
    }

    @GetMapping("current")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Site current() {
        return Contexts.getCurrentSite();
    }

    @GetMapping("/theme")
    @PreAuthorize("hasAnyAuthority('site:theme','*')")
    public List<String> currentTheme() throws IOException {
        return getThemeList(Contexts.getCurrentSite());
    }

    @GetMapping("{id}/theme")
    @PreAuthorize("hasAnyAuthority('site:theme','*')")
    public List<String> theme(@PathVariable Long id) throws IOException {
        Site site = service.select(id);
        if (site == null) {
            throw new Http404Exception("Site not found. ID = " + id);
        }
        return getThemeList(site);
    }

    private List<String> getThemeList(Site site) throws IOException {
        List<String> themeList = new ArrayList<>();
        String globalShare = "/" + Constants.TEMPLATE_SHARE;
        String storagePath = site.getConfig().getTemplateStorage().getPath();
        appendTheme(themeList, storagePath, globalShare);
        String templateBase = site.getBasePath("");
        appendTheme(themeList, storagePath, templateBase);
        return themeList;
    }

    private void appendTheme(List<String> themeList, String path, String base) throws IOException {
        File file = resourceLoader.getResource(path + base).getFile();
        if (!file.exists()) {
            return;
        }
        File[] themeFiles = file.listFiles(File::isDirectory);
        if (themeFiles == null) {
            return;
        }
        for (File themeFile : themeFiles) {
            themeList.add(base + "/" + themeFile.getName());
        }
    }
}