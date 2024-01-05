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
@RestController("backendWebFileTemplateController")
@RequestMapping(BACKEND_API + "/ext/web-file-template")
public class WebFileTemplateController extends AbstractWebFileController {
    public WebFileTemplateController(PathResolver pathResolver, Props props) {
        super(pathResolver, props);
    }

    @Override
    protected List<String> getExcludes() {
        return Arrays.asList(EXCLUDES_WEB_INF, EXCLUDES_CP, EXCLUDES_UPLOADS);
    }

    @Override
    protected String getSubDir() {
        return FilesEx.SLASH + Contexts.getCurrentSite().getId();
    }

    @GetMapping
    @PreAuthorize("hasAnyAuthority('webFileTemplate:list','*')")
    public List<WebFile> list(@RequestParam(defaultValue = "/") String parentId,
                              @RequestParam(defaultValue = "false") boolean isDir,
                              @RequestParam(required = false) String name,
                              @RequestParam(defaultValue = "name") String sort) {
        Site site = Contexts.getCurrentSite();
        return super.list(parentId, isDir, name, sort, site.getConfig().getTemplateStorage());
    }

    @GetMapping("show")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:show','*')")
    public WebFile show(String id) {
        Site site = Contexts.getCurrentSite();
        return super.show(id, site.getConfig().getTemplateStorage());
    }

    @PostMapping
    @PreAuthorize("hasAnyAuthority('webFileTemplate:create','*')")
    @OperationLog(module = "webFileTemplate", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> create(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.create(params, site.getConfig().getTemplateStorage());
    }

    @PostMapping("mkdir")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:mkdir','*')")
    @OperationLog(module = "webFileTemplate", operation = "mkdir", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> mkdir(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.mkdir(params, site.getConfig().getTemplateStorage());
    }

    @PostMapping("copy")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:copy','*')")
    @OperationLog(module = "webFileTemplate", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> copy(@RequestBody @Valid AbstractWebFileController.WebFileBatchParams params) {
        Site site = Contexts.getCurrentSite();
        return super.copy(params, site.getConfig().getTemplateStorage());
    }

    @PostMapping("move")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:move','*')")
    @OperationLog(module = "webFileTemplate", operation = "move", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> move(@RequestBody @Valid AbstractWebFileController.WebFileBatchParams params) {
        Site site = Contexts.getCurrentSite();
        return super.move(params, site.getConfig().getTemplateStorage());
    }

    @PostMapping("upload")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:upload','*')")
    @OperationLog(module = "webFileTemplate", operation = "create", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> upload(@RequestParam(value = "file") MultipartFile file, String parentId) {
        Site site = Contexts.getCurrentSite();
        return super.upload(file, parentId, site.getConfig().getTemplateStorage());
    }

    @PostMapping("upload-zip")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:uploadZip','*')")
    @OperationLog(module = "webFileTemplate", operation = "uploadZip", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> uploadZip(@RequestParam(value = "file") MultipartFile file, String parentId) {
        Site site = Contexts.getCurrentSite();
        return super.uploadZip(file, parentId, site.getConfig().getTemplateStorage());
    }

    @PostMapping("download-zip")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:downloadZip','*')")
    public void downloadZip(@RequestBody WebFileDownloadParams params,
                            HttpServletRequest request, HttpServletResponse response) throws IOException {
        Site site = Contexts.getCurrentSite();
        super.downloadZip(params, site.getConfig().getTemplateStorage(), request, response);
    }

    @PutMapping
    @PreAuthorize("hasAnyAuthority('webFileTemplate:update','*')")
    @OperationLog(module = "webFileTemplate", operation = "update", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> update(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.update(params, site.getConfig().getTemplateStorage());
    }

    @PutMapping("rename")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:update','*')")
    @OperationLog(module = "webFileTemplate", operation = "rename", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> rename(@RequestBody @Valid WebFileParams params) {
        Site site = Contexts.getCurrentSite();
        return super.rename(params, site.getConfig().getTemplateStorage());
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('webFileTemplate:delete','*')")
    @OperationLog(module = "webFileTemplate", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Responses.Body> delete(@RequestBody List<String> ids) {
        Site site = Contexts.getCurrentSite();
        return super.delete(ids, site.getConfig().getTemplateStorage());
    }
}
