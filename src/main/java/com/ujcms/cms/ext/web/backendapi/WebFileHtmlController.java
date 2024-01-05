package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.file.FilesEx;
import com.ujcms.commons.file.WebFile;
import com.ujcms.commons.web.PathResolver;
import com.ujcms.commons.web.Responses;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.validation.Valid;
import java.io.IOException;
import java.util.Arrays;
import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * @author PONY
 */
@RestController("backendWebFileHtmlController")
@RequestMapping(BACKEND_API + "/ext/web-file-html")
public class WebFileHtmlController extends AbstractWebFileController {
    public WebFileHtmlController(PathResolver pathResolver, Props props) {
        super(pathResolver, props);
    }

    @Override
    protected List<String> getExcludes() {
        return Arrays.asList(EXCLUDES_WEB_INF, EXCLUDES_META_INF, EXCLUDES_CP, EXCLUDES_TEMPLATES, EXCLUDES_UPLOADS);
    }

    @Override
    protected String getSubDir() {
        String staticBase = Contexts.getCurrentSite().getStaticBase();
        if (StringUtils.isBlank(staticBase)) {
            return FilesEx.SLASH;
        }
        return staticBase;
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('webFileHtml:list','*')")
    public List<WebFile> list(@RequestParam(defaultValue = "/") String parentId,
                              @RequestParam(defaultValue = "false") boolean isDir,
                              @RequestParam(required = false) String name,
                              @RequestParam(defaultValue = "name") String sort) {
        Site site = Contexts.getCurrentSite();
        return super.list(parentId, isDir, name, sort, site.getConfig().getHtmlStorage());
    }

    @GetMapping("show")
    @PreAuthorize("hasAnyAuthority('webFileHtml:show','*')")
    public WebFile show(String id) {
        Site site = Contexts.getCurrentSite();
        return super.show(id, site.getConfig().getHtmlStorage());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('webFileHtml:create','*')")
    @OperationLog(module = "webFileHtml", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> create(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.create(params, site.getConfig().getHtmlStorage());
    }

    @PostMapping("mkdir")
    @PreAuthorize("hasAnyAuthority('webFileHtml:mkdir','*')")
    @OperationLog(module = "webFileHtml", operation = "mkdir", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> mkdir(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.mkdir(params, site.getConfig().getHtmlStorage());
    }

    @PostMapping("copy")
    @PreAuthorize("hasAnyAuthority('webFileHtml:copy','*')")
    @OperationLog(module = "webFileHtml", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> copy(@RequestBody @Valid AbstractWebFileController.WebFileBatchParams params) {
        Site site = Contexts.getCurrentSite();
        return super.copy(params, site.getConfig().getHtmlStorage());
    }

    @PostMapping("move")
    @PreAuthorize("hasAnyAuthority('webFileHtml:move','*')")
    @OperationLog(module = "webFileHtml", operation = "move", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> move(@RequestBody @Valid AbstractWebFileController.WebFileBatchParams params) {
        Site site = Contexts.getCurrentSite();
        return super.move(params, site.getConfig().getHtmlStorage());
    }

    @PostMapping("upload")
    @PreAuthorize("hasAnyAuthority('webFileHtml:upload','*')")
    @OperationLog(module = "webFileHtml", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> upload(@RequestParam(value = "file") MultipartFile file, String parentId) {
        Site site = Contexts.getCurrentSite();
        return super.upload(file, parentId, site.getConfig().getHtmlStorage());
    }

    @PostMapping("upload-zip")
    @PreAuthorize("hasAnyAuthority('webFileHtml:uploadZip','*')")
    @OperationLog(module = "webFileHtml", operation = "uploadZip", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> uploadZip(@RequestParam(value = "file") MultipartFile file, String parentId) {
        Site site = Contexts.getCurrentSite();
        return super.uploadZip(file, parentId, site.getConfig().getHtmlStorage());
    }

    @PostMapping("download-zip")
    @PreAuthorize("hasAnyAuthority('webFileHtml:downloadZip','*')")
    public void downloadZip(@RequestBody @Valid WebFileDownloadParams params, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Site site = Contexts.getCurrentSite();
        super.downloadZip(params, site.getConfig().getHtmlStorage(), request, response);
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('webFileHtml:update','*')")
    @OperationLog(module = "webFileHtml", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> update(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.update(params, site.getConfig().getHtmlStorage());
    }

    @PutMapping("rename")
    @PreAuthorize("hasAnyAuthority('webFileHtml:update','*')")
    @OperationLog(module = "webFileHtml", operation = "rename", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> rename(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.rename(params, site.getConfig().getHtmlStorage());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('webFileHtml:delete','*')")
    @OperationLog(module = "webFileHtml", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Responses.Body> delete(@RequestBody List<String> ids) {
        Site site = Contexts.getCurrentSite();
        return super.delete(ids, site.getConfig().getHtmlStorage());
    }
}
