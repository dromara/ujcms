package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.TemplateHistory;
import com.ujcms.cms.core.service.TemplateHistoryService;
import com.ujcms.cms.core.service.TemplateHistoryService.CapturedFile;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.file.FilesEx;
import com.ujcms.common.file.WebFile;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.UrlBuilder;
import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.Strings;
import org.jspecify.annotations.Nullable;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.multipart.MultipartFile;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.OffsetDateTime;
import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 模板文件管理器。写路径同时捕获模板编辑历史（template-history / design-review P3-6）：
 * 保存/新建/单文件上传/删除按文件捕获，改名/移动/复制/ZIP 上传不捕获（内容无损或批量部署操作，
 * 其结果由下次保存的回填补记）；主题 ZIP 安装/更新不经本控制器（staged-swap 自带回滚）。
 * 捕获在既有权限与 {@code checkId} 校验<b>之后</b>（super 调用先行校验路径），历史端点复用
 * 同一套校验——恢复即编辑（{@code webFileTemplate:update}），不放宽任何能力。
 *
 * @author PONY
 */
@RestController("backendWebFileTemplateController")
@RequestMapping(BACKEND_API + "/ext/web-file-template")
public class WebFileTemplateController extends AbstractWebFileController {
    private final PathResolver pathResolver;
    private final TemplateHistoryService historyService;

    public WebFileTemplateController(PathResolver pathResolver, Props props,
                                     TemplateHistoryService historyService) {
        super(pathResolver, props);
        this.pathResolver = pathResolver;
        this.historyService = historyService;
    }

    @Override
    protected List<String> getExcludes() {
        return Arrays.asList(EXCLUDES_WEB_INF, EXCLUDES_CP, EXCLUDES_UPLOADS);
    }

    @Override
    protected String getSubDir() {
        return FilesEx.SLASH + Contexts.getCurrentSite().getId();
    }

    private FileHandler templateFileHandler() {
        return Contexts.getCurrentSite().getConfig().getTemplateStorage().getFileHandler(pathResolver);
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
        // 新建也可能静默覆盖同名文件：先读被替换状态，成功写入后回填 + 追加（create 对任意扩展名存文本）
        String filename = UrlBuilder.of(params.getParentId()).appendPath(params.getName()).toString();
        FileHandler fileHandler = templateFileHandler();
        String pre = historyService.readEligibleText(fileHandler, filename);
        ResponseEntity<Responses.Body> response = super.create(params, site.getConfig().getTemplateStorage());
        historyService.recordWrite(filename, pre, params.getText() != null ? params.getText() : "",
                TemplateHistory.SOURCE_SAVE, Contexts.getCurrentUsername(), null);
        return response;
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
    @OperationLog(module = "webFileTemplate", operation = "copy", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> copy(@RequestBody @Valid AbstractWebFileController.WebFileBatchParams params) {
        Site site = Contexts.getCurrentSite();
        return super.copy(params, site.getConfig().getTemplateStorage());
    }

    @PostMapping("move")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:move','*')")
    @OperationLog(module = "webFileTemplate", operation = "move", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> move(@RequestBody @Valid AbstractWebFileController.WebFileBatchParams params) {
        Site site = Contexts.getCurrentSite();
        return super.move(params, site.getConfig().getTemplateStorage());
    }

    @PostMapping("upload")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:upload','*')")
    @OperationLog(module = "webFileTemplate", operation = "upload", type = OperationType.CREATE)
    public ResponseEntity<Responses.Body> upload(@RequestParam(value = "file") MultipartFile file, String parentId) {
        Site site = Contexts.getCurrentSite();
        // 上传覆盖在线模板与坏保存同类：新内容须在 super 消费 multipart 前读取（transferTo 可能移走临时文件）
        String filename = UrlBuilder.of(parentId).appendPath(file.getOriginalFilename()).toString();
        FileHandler fileHandler = templateFileHandler();
        String newText = null;
        if (historyService.isEligiblePath(filename) && file.getSize() <= TemplateHistoryService.MAX_CONTENT_BYTES) {
            try {
                newText = new String(file.getBytes(), StandardCharsets.UTF_8);
            } catch (IOException e) {
                throw new IllegalStateException("Failed to read uploaded file: " + filename, e);
            }
        }
        String pre = historyService.readEligibleText(fileHandler, filename);
        ResponseEntity<Responses.Body> response = super.upload(file, parentId, site.getConfig().getTemplateStorage());
        // newText 为 null（二进制/超限）时仅回填被覆盖状态
        historyService.recordWrite(filename, pre, newText, TemplateHistory.SOURCE_UPLOAD,
                Contexts.getCurrentUsername(), null);
        return response;
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
        FileHandler fileHandler = templateFileHandler();
        String pre = historyService.readEligibleText(fileHandler, params.getId());
        ResponseEntity<Responses.Body> response = super.update(params, site.getConfig().getTemplateStorage());
        // 镜像 super.update 的存文本条件（仅 TEXT_EXTENSIONS 存文本；改名部分不捕获——内容无损，
        // 历史键着旧路径，新路径由下次保存回填）
        if (Strings.CS.equalsAny(FilenameUtils.getExtension(params.getId()),
                WebFile.TEXT_EXTENSIONS.toArray(new String[0]))) {
            historyService.recordWrite(params.getId(), pre, params.getText() != null ? params.getText() : "",
                    TemplateHistory.SOURCE_SAVE, Contexts.getCurrentUsername(), null);
        }
        return response;
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
        // 删除是仅存的数据丢失路径：删除前捕获被销毁的文本内容，成功后逐文件追加 delete 条目
        FileHandler fileHandler = templateFileHandler();
        List<CapturedFile> captured = new ArrayList<>();
        for (String id : ids) {
            captured.addAll(historyService.collectForDelete(fileHandler, id));
        }
        ResponseEntity<Responses.Body> response = super.delete(ids, site.getConfig().getTemplateStorage());
        historyService.recordDeletes(captured, Contexts.getCurrentUsername());
        return response;
    }

    /**
     * 模板编辑历史（template-history §9）：{@code id}（单文件精确路径）与 {@code dir}（目录子树，
     * 已删除文件的发现面）二选一；新→旧，不含内容（单条内容经 {@code history/{historyId}} 获取）。
     * 路径校验同文件操作（站点子树规则）。
     */
    @GetMapping("history")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:show','*')")
    public List<HistoryEntryResult> history(@RequestParam(required = false) @Nullable String id,
                                            @RequestParam(required = false) @Nullable String dir) {
        checkFilesManagementEnabled();
        if ((id == null) == (dir == null)) {
            throw new Http400Exception("id 与 dir 参数二选一");
        }
        List<TemplateHistory> list;
        if (id != null) {
            checkId(id);
            list = historyService.listByPath(id);
        } else {
            checkId(dir);
            list = historyService.listByDir(dir);
        }
        return list.stream().map(HistoryEntryResult::of).toList();
    }

    /**
     * 单条历史详情（含文本内容，只读检视用；路径归属校验同文件操作）。
     */
    @GetMapping("history/{historyId}")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:show','*')")
    public HistoryDetailResult historyDetail(@PathVariable Long historyId) {
        checkFilesManagementEnabled();
        TemplateHistory entry = historyService.get(historyId);
        checkId(entry.getPath());
        return new HistoryDetailResult(HistoryEntryResult.of(entry), entry.getContent());
    }

    /**
     * 恢复历史条目（template-history §7）：恢复 = 一次标准写入（先回填被替换状态，再落盘并追加
     * {@code restore} 溯源条目）；已删除文件重建。恢复即编辑——权限同 {@code webFileTemplate:update}
     * （模板编辑在 T1 之后即代码执行权限，恢复不放宽任何能力）。~5s 模板缓存延迟内生效；
     * 静态页须重新生成才可见。
     */
    @PostMapping("history/restore")
    @PreAuthorize("hasAnyAuthority('webFileTemplate:update','*')")
    @OperationLog(module = "webFileTemplate", operation = "restoreHistory", type = OperationType.UPDATE)
    public ResponseEntity<Responses.Body> restoreHistory(@RequestBody @Valid RestoreParams params) {
        checkFilesManagementEnabled();
        long historyId;
        try {
            historyId = Long.parseLong(params.historyId());
        } catch (NumberFormatException e) {
            throw new Http400Exception("历史ID非法: " + params.historyId());
        }
        TemplateHistory entry = historyService.get(historyId);
        checkId(entry.getPath());
        historyService.restore(templateFileHandler(), entry, Contexts.getCurrentUsername());
        return Responses.ok();
    }

    /**
     * 恢复参数
     *
     * @param historyId 历史ID（雪花ID字符串，免 JS 精度丢失）
     */
    public record RestoreParams(String historyId) {
    }

    /**
     * 历史条目（列表——不含文本内容）
     *
     * @param id           历史ID（雪花ID字符串，免 JS 精度丢失）
     * @param path         存储相对路径
     * @param name         文件名（路径末段，列表展示用）
     * @param length       内容字节数
     * @param source       来源（save / upload / backfill / delete / restore）
     * @param restoredFrom 恢复来源历史ID（仅 source=restore）
     * @param actor        操作人用户名
     * @param created      捕获时间
     */
    public record HistoryEntryResult(String id, String path, String name, int length, String source,
                                     @Nullable String restoredFrom, @Nullable String actor,
                                     OffsetDateTime created) {
        public static HistoryEntryResult of(TemplateHistory entry) {
            return new HistoryEntryResult(String.valueOf(entry.getId()), entry.getPath(),
                    FilenameUtils.getName(entry.getPath()),
                    entry.getLength() != null ? entry.getLength() : 0, entry.getSource(),
                    entry.getRestoredFrom() != null ? String.valueOf(entry.getRestoredFrom()) : null,
                    entry.getActor(), entry.getCreated());
        }
    }

    /**
     * 历史条目详情（含文本内容，只读检视用）
     */
    public record HistoryDetailResult(HistoryEntryResult entry, String content) {
    }
}
