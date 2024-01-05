package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.file.*;
import com.ujcms.commons.web.PathResolver;
import com.ujcms.commons.web.Responses;
import com.ujcms.commons.web.Servlets;
import com.ujcms.commons.web.UrlBuilder;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.Http403Exception;
import io.swagger.v3.oas.annotations.media.Schema;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.OutputStream;
import java.util.List;

import static com.ujcms.commons.file.FilesEx.SLASH;
import static com.ujcms.commons.file.WebFile.TEXT_EXTENSIONS;

/**
 * @author PONY
 */
public abstract class AbstractWebFileController {
    private final PathResolver pathResolver;
    private final Props props;

    protected AbstractWebFileController(PathResolver pathResolver, Props props) {
        this.pathResolver = pathResolver;
        this.props = props;
    }

    /**
     * 获取需要排除的文件夹
     *
     * @return 需要排除的文件夹
     */
    protected abstract List<String> getExcludes();

    /**
     * 获取子目录。如 /1 /en
     *
     * @return 子目录
     */
    protected abstract String getSubDir();

    public static final String EXCLUDES_CP = "/cp";
    public static final String EXCLUDES_WEB_INF = "/WEB-INF";
    public static final String EXCLUDES_META_INF = "/META-INF";
    public static final String EXCLUDES_TEMPLATES = "/templates";
    public static final String EXCLUDES_UPLOADS = "/uploads";

    protected List<WebFile> list(String parentId, boolean isDir, String name, String sort, Config.Storage storage) {
        checkFilesManagementEnabled();
        checkId(parentId);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        WebFileFilter baseFilter = webFile -> !getExcludes().contains(webFile.getId())
                && (name == null || webFile.getName().contains(name));
        WebFileFilter filter = baseFilter;
        if (isDir) {
            filter = webFile -> webFile.isDirectory() && baseFilter.accept(webFile);
        }
        List<WebFile> list = fileHandler.listFiles(parentId, filter);
        String[] pair = sort.split("_");
        WebFile.sort(list, pair[0], pair.length > 1 ? pair[1] : "asc");
        return list;
    }

    protected WebFile show(String id, Config.Storage storage) {
        checkFilesManagementEnabled();
        checkId(id);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        return fileHandler.getWebFile(id);
    }

    protected ResponseEntity<Responses.Body> create(WebFileParams params, Config.Storage storage) {
        checkFilesManagementEnabled();
        checkId(params.parentId);
        checkName(params.name);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        String filename = UrlBuilder.of(params.parentId).appendPath(params.name).toString();
        fileHandler.store(filename, params.text);
        return Responses.ok();
    }

    protected ResponseEntity<Responses.Body> mkdir(WebFileParams params, Config.Storage storage) {
        checkFilesManagementEnabled();
        checkId(params.parentId);
        checkName(params.name);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        String dir = UrlBuilder.of(params.parentId).appendPath(params.name).toString();
        fileHandler.mkdir(dir);
        return Responses.ok();
    }

    protected ResponseEntity<Responses.Body> copy(WebFileBatchParams params, Config.Storage storage) {
        checkFilesManagementEnabled();
        checkId(params.dir, params.destDir);
        checkName(params.names);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        fileHandler.copy(params.dir, params.names, params.destDir);
        return Responses.ok();
    }

    protected ResponseEntity<Responses.Body> move(WebFileBatchParams params, Config.Storage storage) {
        checkFilesManagementEnabled();
        checkId(params.dir, params.destDir);
        checkName(params.names);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        fileHandler.moveTo(params.dir, params.names, params.destDir);
        return Responses.ok();
    }

    protected ResponseEntity<Responses.Body> upload(MultipartFile file, String parentId, Config.Storage storage) {
        checkFilesManagementEnabled();
        checkId(parentId);
        String name = file.getOriginalFilename();
        checkName(name);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        fileHandler.store(UrlBuilder.of(parentId).appendPath(name).toString(), file);
        return Responses.ok();
    }

    protected ResponseEntity<Responses.Body> uploadZip(MultipartFile file, String parentId, Config.Storage storage) {
        checkFilesManagementEnabled();
        String originalFilename = file.getOriginalFilename();
        if (!ZipUtils.decompressSupport(originalFilename)) {
            throw new Http400Exception("Upload is not a Zip file: " + originalFilename);
        }
        checkId(parentId);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        fileHandler.unzip(file, parentId, props.getFilesExtensionExcludes().toArray(new String[0]));
        return Responses.ok();
    }

    protected void downloadZip(WebFileDownloadParams params, Config.Storage storage,
                               HttpServletRequest request, HttpServletResponse response) throws IOException {
        checkFilesManagementEnabled();
        checkId(params.dir);
        checkName(params.names);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        Servlets.setAttachmentHeader(request, response, "downloads.zip");
        try (OutputStream out = response.getOutputStream()) {
            fileHandler.zip(params.dir, params.names, out);
        }
    }

    protected ResponseEntity<Responses.Body> update(WebFileParams params, Config.Storage storage) {
        checkFilesManagementEnabled();
        String name = FilenameUtils.getName(params.id);
        checkId(params.id);
        checkName(name, params.name);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        // 是否文本文件
        if (StringUtils.equalsAny(FilenameUtils.getExtension(params.id), TEXT_EXTENSIONS.toArray(new String[0]))) {
            fileHandler.store(params.id, params.text);
        }
        // 是否修改文件名
        if (!StringUtils.equals(name, params.name)) {
            fileHandler.rename(params.id, params.name);
        }
        return Responses.ok();
    }

    protected ResponseEntity<Responses.Body> rename(WebFileParams params, Config.Storage storage) {
        checkFilesManagementEnabled();
        String name = FilenameUtils.getName(params.id);
        checkId(params.id);
        checkName(name, params.name);
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        // 是否修改文件名
        if (!StringUtils.equals(name, params.name)) {
            fileHandler.rename(params.id, params.name);
        }
        return Responses.ok();
    }

    protected ResponseEntity<Responses.Body> delete(List<String> ids, Config.Storage storage) {
        checkFilesManagementEnabled();
        checkId(ids.toArray(new String[0]));
        FileHandler fileHandler = storage.getFileHandler(pathResolver);
        for (String id : ids) {
            fileHandler.deleteDirectory(id);
        }
        return Responses.ok();
    }

    private void checkFilesManagementEnabled() {
        if (!props.isFilesManagementEnabled()) {
            throw new Http403Exception("Files management not enabled!");
        }
    }

    private void checkId(String... ids) {
        for (String id : ids) {
            boolean globalPermission = Contexts.getCurrentUser().hasGlobalPermission();
            String[] illegals = new String[]{"./", "..", "\\", "//", "~"};
            String[] illegalsEnds = new String[]{".", " "};
            if (StringUtils.containsAny(id, illegals) || StringUtils.endsWithAny(id, illegalsEnds)) {
                throw new Http400Exception("Web file illegal id: " + id);
            }
            if (FilesEx.containsDir(id, getExcludes().toArray(new String[0]))) {
                throw new Http403Exception("Web file forbidden: " + id);
            }
            boolean isSubDir = id.equals(getSubDir()) || id.startsWith(getSubDir() + SLASH);
            if (!globalPermission && !isSubDir) {
                throw new Http403Exception("Web file sub dir forbidden: " + id);
            }
        }
    }

    private void checkName(String... names) {
        for (String name : names) {
            String[] illegals = new String[]{"\\", "/"};
            String[] illegalsEnds = new String[]{".", " "};
            if (StringUtils.isBlank(name) ||
                    StringUtils.containsAny(name, illegals) || StringUtils.endsWithAny(name, illegalsEnds)) {
                throw new Http400Exception("Web file illegal name: " + name);
            }
            if (props.getFilesExtensionExcludes().contains(FilenameUtils.getExtension(name).toLowerCase())) {
                throw new Http400Exception("Web file illegal extension: " + name);
            }
        }
    }

    @Schema(name = "AbstractWebFileController.WebFileParams", description = "Web文件参数")
    public static class WebFileParams {
        private String id;
        private String parentId;
        private String name;
        private String text;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        public String getParentId() {
            return parentId;
        }

        public void setParentId(String parentId) {
            this.parentId = parentId;
        }

        public String getName() {
            return name;
        }

        public void setName(String name) {
            this.name = name;
        }

        public String getText() {
            return text;
        }

        public void setText(String text) {
            this.text = text;
        }
    }

    @Schema(name = "AbstractWebFileController.WebFileBatchParams", description = "Web文件批量参数")
    public static class WebFileBatchParams {
        private String dir;
        private String[] names;
        private String destDir;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public String[] getNames() {
            return names;
        }

        public void setNames(String[] names) {
            this.names = names;
        }

        public String getDestDir() {
            return destDir;
        }

        public void setDestDir(String destDir) {
            this.destDir = destDir;
        }
    }

    @Schema(name = "AbstractWebFileController.WebFileDownloadParams", description = "Web文件下载参数")
    public static class WebFileDownloadParams {
        private String dir;
        private String[] names;

        public String getDir() {
            return dir;
        }

        public void setDir(String dir) {
            this.dir = dir;
        }

        public String[] getNames() {
            return names;
        }

        public void setNames(String[] names) {
            this.names = names;
        }
    }
}
