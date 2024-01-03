package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.support.Constants;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.commons.web.Entities;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http403Exception;
import com.ujcms.commons.web.exception.Http404Exception;
import org.springframework.core.io.ResourceLoader;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import javax.servlet.http.HttpServletRequest;
import javax.validation.Valid;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

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
                           @Nullable Integer parentId, @Nullable Integer fullOrgId,
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
        } else {
            args.parentId(parentId);
        }
        return service.selectList(args, validOffset(offset), validLimit(limit));
    }

    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('site:show','*')")
    public Site show(@PathVariable Integer id) {
        Site bean = service.select(id);
        if (bean == null) {
            throw new Http404Exception("Site not found. ID = " + id);
        }
        return bean;
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('site:create','*')")
    @OperationLog(module = "site", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> create(@RequestBody @Valid Site bean) {
        Site site = new Site();
        Entities.copy(bean, site, "order");
        site.setHtml(bean.getHtml());
        site.setWatermark(bean.getWatermark());
        site.setMessageBoard(bean.getMessageBoard());
        // 数组不会拷贝
        site.setCopyData(bean.getCopyData());
        User user = Contexts.getCurrentUser();
        List<Integer> permissionSiteIds = service.listIdByOrgId(user.getOrgId());
        validateDataPermission(permissionSiteIds, user, site.getParentId());
        service.copy(site);
        return Responses.ok();
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('site:update','*')")
    @OperationLog(module = "site", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> update(@RequestBody @Valid Site bean) {
        Site site = service.select(bean.getId());
        if (site == null) {
            throw new Http400Exception(SITE_NOT_FOUND + bean.getId());
        }
        Integer origParentId = site.getParentId();
        Entities.copy(bean, site, "parentId", "order");
        User user = Contexts.getCurrentUser();
        List<Integer> permissionOrgIds = service.listIdByOrgId(user.getOrgId());
        if (Objects.equals(origParentId, site.getParentId())) {
            validateDataPermission(permissionOrgIds, user, site.getId());
        } else {
            validateDataPermission(permissionOrgIds, user, site.getId(), site.getParentId());
        }
        service.update(site, bean.getParentId(), null);
        return Responses.ok();
    }

    @PutMapping("order")
    @PreAuthorize("hasAnyAuthority('site:update','*')")
    @OperationLog(module = "site", operation = "updateOrder", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> updateOrder(@RequestBody Integer[] ids) {
        User user = Contexts.getCurrentUser();
        List<Integer> permissionOrgIds = service.listIdByOrgId(user.getOrgId());
        List<Site> list = new ArrayList<>();
        for (Integer id : ids) {
            Site bean = service.select(id);
            if (bean == null) {
                throw new Http400Exception(SITE_NOT_FOUND + id);
            }
            validateDataPermission(permissionOrgIds, user, bean.getId());
            list.add(bean);
        }
        service.updateOrder(list);
        return Responses.ok();
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('site:delete','*')")
    @OperationLog(module = "site", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Responses.Body> delete(@RequestBody List<Integer> ids) {
        User user = Contexts.getCurrentUser();
        List<Integer> permissionOrgIds = service.listIdByOrgId(user.getOrgId());
        for (Integer id : ids) {
            Site bean = service.select(id);
            if (bean == null) {
                throw new Http400Exception(SITE_NOT_FOUND + id);
            }
            validateDataPermission(permissionOrgIds, user, bean.getId());
            service.delete(bean);
        }
        return Responses.ok();
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
    public List<String> theme(@PathVariable Integer id) throws IOException {
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

    private void validateDataPermission(List<Integer> permissionSiteIds, User currentUser, Integer... siteIds) {
        if (currentUser.hasGlobalPermission()) {
            return;
        }
        for (Integer siteId : siteIds) {
            if (!permissionSiteIds.contains(siteId)) {
                throw new Http403Exception("Site data forbidden. ID: " + siteId);
            }
        }
    }

    private static final String SITE_NOT_FOUND = "Site not found. ID: ";
}