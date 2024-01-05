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
@RestController("backendWebFileUploadController")
@RequestMapping(BACKEND_API + "/ext/web-file-upload")
public class WebFileUploadController extends AbstractWebFileController {
    public WebFileUploadController(PathResolver pathResolver, Props props) {
        super(pathResolver, props);
    }

    @Override
    protected List<String> getExcludes() {
        return Arrays.asList(EXCLUDES_WEB_INF, EXCLUDES_CP, EXCLUDES_TEMPLATES);
    }

    @Override
    protected String getSubDir() {
        return FilesEx.SLASH + Contexts.getCurrentSite().getId();
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('webFileUpload:list','*')")
    public List<WebFile> list(@RequestParam(defaultValue = "/") String parentId,
                              @RequestParam(defaultValue = "false") boolean isDir,
                              @RequestParam(required = false) String name,
                              @RequestParam(defaultValue = "name") String sort) {
        Site site = Contexts.getCurrentSite();
        return super.list(parentId, isDir, name, sort, site.getConfig().getUploadStorage());
    }

    @GetMapping("show")
    @PreAuthorize("hasAnyAuthority('webFileUpload:show','*')")
    public WebFile show(String id) {
        Site site = Contexts.getCurrentSite();
        return super.show(id, site.getConfig().getUploadStorage());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('webFileUpload:create','*')")
    @OperationLog(module = "webFileUpload", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> create(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.create(params, site.getConfig().getUploadStorage());
    }

    @PostMapping("mkdir")
    @PreAuthorize("hasAnyAuthority('webFileUpload:mkdir','*')")
    @OperationLog(module = "webFileUpload", operation = "mkdir", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> mkdir(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.mkdir(params, site.getConfig().getUploadStorage());
    }

    @PostMapping("copy")
    @PreAuthorize("hasAnyAuthority('webFileUpload:copy','*')")
    @OperationLog(module = "webFileUpload", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> copy(@RequestBody @Valid AbstractWebFileController.WebFileBatchParams params) {
        Site site = Contexts.getCurrentSite();
        return super.copy(params, site.getConfig().getUploadStorage());
    }

    @PostMapping("move")
    @PreAuthorize("hasAnyAuthority('webFileUpload:move','*')")
    @OperationLog(module = "webFileUpload", operation = "move", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> move(@RequestBody @Valid AbstractWebFileController.WebFileBatchParams params) {
        Site site = Contexts.getCurrentSite();
        return super.move(params, site.getConfig().getUploadStorage());
    }

    @PostMapping("upload")
    @PreAuthorize("hasAnyAuthority('webFileUpload:upload','*')")
    @OperationLog(module = "webFileUpload", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> upload(@RequestParam(value = "file") MultipartFile file, String parentId) {
        Site site = Contexts.getCurrentSite();
        return super.upload(file, parentId, site.getConfig().getUploadStorage());
    }

    @PostMapping("upload-zip")
    @PreAuthorize("hasAnyAuthority('webFileUpload:uploadZip','*')")
    @OperationLog(module = "webFileUpload", operation = "uploadZip", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> uploadZip(@RequestParam(value = "file") MultipartFile file, String parentId) {
        Site site = Contexts.getCurrentSite();
        return super.uploadZip(file, parentId, site.getConfig().getUploadStorage());
    }

    @PostMapping("download-zip")
    @PreAuthorize("hasAnyAuthority('webFileUpload:downloadZip','*')")
    public void downloadZip(@RequestBody @Valid WebFileDownloadParams params, HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        Site site = Contexts.getCurrentSite();
        super.downloadZip(params, site.getConfig().getUploadStorage(), request, response);
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('webFileUpload:update','*')")
    @OperationLog(module = "webFileUpload", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> update(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.update(params, site.getConfig().getUploadStorage());
    }

    @PutMapping("rename")
    @PreAuthorize("hasAnyAuthority('webFileUpload:update','*')")
    @OperationLog(module = "webFileUpload", operation = "rename", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> rename(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.rename(params, site.getConfig().getUploadStorage());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('webFileUpload:delete','*')")
    @OperationLog(module = "webFileUpload", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Responses.Body> delete(@RequestBody List<String> ids) {
        Site site = Contexts.getCurrentSite();
        return super.delete(ids, site.getConfig().getUploadStorage());
    }
}
