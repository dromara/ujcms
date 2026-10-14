package com.ujcms.cms.core.web.backendapi;

import com.fasterxml.jackson.annotation.JsonView;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.ujcms.cms.core.component.ThemeMetadata;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.args.SiteArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http404Exception;
import org.springframework.core.io.ResourceLoader;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import static com.ujcms.cms.core.support.Constants.validLimit;
import static com.ujcms.cms.core.support.Constants.validOffset;
import static com.ujcms.common.query.QueryUtils.getQueryMap;

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
    private final ObjectMapper objectMapper;

    public SiteController(SiteService service, ResourceLoader resourceLoader, ObjectMapper objectMapper) {
        this.service = service;
        this.resourceLoader = resourceLoader;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('site:list','*')")
    @JsonView(Views.List.class)
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

    /**
     * 站点可选主题（裸主题名，R2）：列出 {@code templates/{siteId}/} 下的主题目录名。
     * 主题是每站点实例（D3），全局 share 目录只承载模板包含片段，不再作为可激活主题列出；
     * 纯数据包（{@code kind: data-only}，§8）不可激活，同样不列——站点表单是激活写入面，
     * 与 {@code ThemeController} 的激活 400 口径一致（A5）。
     */
    private List<String> getThemeList(Site site) throws IOException {
        List<String> themeList = new ArrayList<>();
        String storagePath = site.getConfig().getTemplateStorage().getPath();
        File file = resourceLoader.getResource(storagePath + site.getBasePath("")).getFile();
        if (!file.exists()) {
            return themeList;
        }
        File[] themeFiles = file.listFiles(File::isDirectory);
        if (themeFiles == null) {
            return themeList;
        }
        for (File themeFile : themeFiles) {
            if (isDataOnlyTheme(themeFile)) {
                continue;
            }
            themeList.add(themeFile.getName());
        }
        return themeList;
    }

    private boolean isDataOnlyTheme(File themeDir) {
        File manifest = new File(themeDir, "theme.manifest.json");
        if (!manifest.isFile()) {
            return false;
        }
        try {
            return ThemeMetadata.KIND_DATA_ONLY.equals(
                    objectMapper.readTree(manifest).path("kind").asText(null));
        } catch (IOException e) {
            // 清单不可读按普通主题对待（宽容读取；安装门槛才从严）
            return false;
        }
    }
}