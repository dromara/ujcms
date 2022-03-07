package com.ujcms.core.web.backendapi;

import com.ujcms.core.domain.Attachment;
import com.ujcms.core.domain.Global;
import com.ujcms.core.domain.Site;
import com.ofwise.util.web.exception.Http400Exception;
import com.ofwise.util.web.exception.LogicException;
import com.ujcms.core.service.AttachmentService;
import com.ujcms.core.service.GlobalService;
import com.ujcms.core.support.Contexts;
import com.ofwise.util.file.FileHandler;
import com.ofwise.util.file.FilesEx;
import com.ofwise.util.image.ImageHandler;
import com.ofwise.util.image.Images;
import com.ofwise.util.image.ResizeMode;
import com.ofwise.util.web.PathResolver;
import com.ofwise.util.web.Responses;
import com.ofwise.util.web.Uploads;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.core.support.UrlConstants.BACKEND_API;

/**
 * 上传 Controller
 *
 * @author PONY
 */
@RestController("backendUploadController")
@RequestMapping(BACKEND_API)
public class UploadController {
    private AttachmentService attachmentService;
    private GlobalService globalService;
    private ImageHandler imageHandler;
    private PathResolver pathResolver;

    public UploadController(AttachmentService attachmentService, GlobalService globalService,
                            ImageHandler imageHandler, PathResolver pathResolver) {
        this.attachmentService = attachmentService;
        this.globalService = globalService;
        this.imageHandler = imageHandler;
        this.pathResolver = pathResolver;
    }

    /**
     * 上传图片。缩略图宽度、高度同时存在时，才生成缩略图
     *
     * @param maxWidth        图片最大宽度
     * @param maxHeight       图片最大高度
     * @param isWatermark     是否加水印
     * @param thumbnailWidth  缩略图宽度
     * @param thumbnailHeight 缩略图高度
     */
    @PostMapping("image-upload")
    public Object imageUpload(Integer maxWidth, Integer maxHeight, String resizeMode, Boolean isWatermark,
                              Integer thumbnailWidth, Integer thumbnailHeight, HttpServletRequest request)
            throws IOException {
        Global.Upload upload = globalService.getUnique().getUpload();
        ExtraHandle extraHandle = (tempFile, extension, pathname, fileHandler) -> {
            // 图片压缩。全局设置大于一定像素的图片进行压缩。
            int imageMaxWidth = maxWidth != null ? maxWidth : upload.getImageMaxWidth();
            int imageMaxHeight = maxHeight != null ? maxHeight : upload.getImageMaxHeight();
            if (imageMaxWidth > 0 || imageMaxHeight > 0) {
                ResizeMode mode = resizeMode == null ? ResizeMode.normal : ResizeMode.valueOf(resizeMode);
                imageHandler.resize(tempFile.getAbsolutePath(), tempFile.getAbsolutePath(),
                        imageMaxWidth, imageMaxHeight, mode);
            }
            Site site = Contexts.getCurrentSite();
            // 图片水印
            Site.Watermark watermark = site.getWatermark();
            String overlay = watermark.getOverlay();
            boolean isNeedWatermark = site.getWatermark().isEnabled() && overlay != null
                    && (isWatermark != null && isWatermark);
            if (isNeedWatermark) {
                // 水印图片可能位于远程服务器，需要获取到本地
                Optional.ofNullable(fileHandler.getName(overlay)).map(fileHandler::getFile).ifPresent(overlayFile -> {
                    // 全局设置大于多少的图片加水印
                    imageHandler.watermark(tempFile.getAbsolutePath(), tempFile.getAbsolutePath(),
                            overlayFile.getAbsolutePath(), watermark.getPosition(), watermark.getDissolve(),
                            watermark.getMinWidth(), watermark.getMinHeight());
                    FileUtils.deleteQuietly(overlayFile);
                });
            }
            // 缩略图。图集需要缩略图，其他一般不需要。
            thumbnail(fileHandler, tempFile, pathname, extension, thumbnailWidth, thumbnailHeight);
        };
        return doUpload(request, upload.getImageLimitByte(), upload.getImageTypes(), IMAGE_TYPE, extraHandle);
    }

    @PostMapping("video-upload")
    public Object videoUpload(HttpServletRequest request) throws IOException {
        Global.Upload upload = globalService.getUnique().getUpload();
        return doUpload(request, upload.getVideoLimitByte(), upload.getVideoTypes(), VIDEO_TYPE, null);
    }

    @PostMapping("doc-upload")
    public Object docUpload(HttpServletRequest request) throws IOException {
        Global.Upload upload = globalService.getUnique().getUpload();
        return doUpload(request, upload.getDocLimitByte(), upload.getDocTypes(), DOC_TYPE, null);
    }

    @PostMapping("file-upload")
    public Object fileUpload(HttpServletRequest request) throws IOException {
        Global.Upload upload = globalService.getUnique().getUpload();
        return doUpload(request, upload.getFileLimitByte(), upload.getFileTypes(), FILE_TYPE, null);
    }

    public Object doUpload(HttpServletRequest request, long limitByte, String types, String type,
                           @Nullable ExtraHandle extraHandle) throws IOException {
        MultipartFile multipart = getMultipart(request);
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        // 检查文件大小
        long length = multipart.getSize();
        validateLimit(length, limitByte);
        // 检查文件后缀
        String extension = FilenameUtils.getExtension(multipart.getOriginalFilename());
        validateType(extension, types);

        FileHandler fileHandler = Contexts.getCurrentSite().getStorage().getFileHandler(pathResolver);
        File tempFile = FilesEx.getTempFile(extension);
        String name = multipart.getOriginalFilename();
        try {
            multipart.transferTo(tempFile);
            if (IMAGE_TYPE.equalsIgnoreCase(type)) {
                // 使用图片格式作为后缀。有时候支持透明格式的图片使用了错误的后缀(如png图片使用jpg后缀)，会导致透明部分变成黑色。
                extension = Images.getFormatName(tempFile);
                if (extension == null) {
                    throw new Http400Exception("Image format type not supported.");
                }
                File newFile = new File(FilenameUtils.removeExtension(tempFile.getName()) + "." + extension);
                if (tempFile.renameTo(newFile)) {
                    tempFile = newFile;
                }
            }
            // 获得存储路径和显示路径
            String pathname = site.getBasePath("/" + type) + Uploads.getFilename(extension);
            String url = fileHandler.getDisplayPrefix() + pathname;
            // 执行额外的处理
            if (extraHandle != null) {
                extraHandle.handle(tempFile, extension, pathname, fileHandler);
            }
            fileHandler.store(pathname, tempFile);
            attachmentService.insert(new Attachment(site.getId(), userId, name, pathname, url, length));
            Map<String, Object> result = new HashMap<>(8);
            result.put("name", name);
            result.put("url", url);
            result.put("size", length);
            return result;
        } finally {
            if (tempFile.exists()) {
                FileUtils.deleteQuietly(tempFile);
            }
        }
    }

    @PostMapping("image-crop")
    public Object imageCrop(@RequestBody CropParam params) throws IOException {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        FileHandler fileHandler = site.getStorage().getFileHandler(pathResolver);
        String src = fileHandler.getName(params.getUrl());
        if (src == null) {
            return Responses.badRequest("external url not support: " + params.getUrl());
        }
        // 图片裁剪。图片任意裁剪，生成新图片。
        String extension = FilenameUtils.getExtension(src);
        String pathname = site.getBasePath("/" + IMAGE_TYPE) + Uploads.getFilename(extension);
        String destUrl = fileHandler.getDisplayPrefix() + pathname;
        File file = fileHandler.getFile(src);
        if (file == null) {
            return Responses.badRequest("file not found: $src");
        }
        File tempFile = FilesEx.getTempFile(extension);
        try {
            // 未裁剪成功，则直接拷贝原图
            if (!imageHandler.crop(file.getAbsolutePath(), tempFile.getAbsolutePath(),
                    params.x, params.y, params.width, params.height)) {
                FileUtils.copyFile(file, tempFile);
            }
            imageHandler.resize(tempFile.getAbsolutePath(), tempFile.getAbsolutePath(),
                    params.maxWidth, params.maxHeight, ResizeMode.normal);
            fileHandler.store(pathname, tempFile);
            // 缩略图。图集需要缩略图，其他一般不需要。
            thumbnail(fileHandler, tempFile, pathname, extension,
                    params.getThumbnailWidth(), params.getThumbnailHeight());
            String name = src.substring(src.lastIndexOf("/") + 1);
            attachmentService.insert(new Attachment(site.getId(), userId, name, pathname, destUrl, tempFile.length()));
            Map<String, Object> result = new HashMap<>(4);
            result.put("url", destUrl);
            return result;
        } finally {
            if (file.exists()) {
                FileUtils.deleteQuietly(file);
            }
            if (tempFile.exists()) {
                FileUtils.deleteQuietly(tempFile);
            }
        }
    }

    private static class CropParam {
        private String url;
        private int x;
        private int y;
        private int width;
        private int height;
        private int maxWidth;
        private int maxHeight;
        private Integer thumbnailWidth;
        private Integer thumbnailHeight;

        public String getUrl() {
            return url;
        }

        public void setUrl(String url) {
            this.url = url;
        }

        public int getX() {
            return x;
        }

        public void setX(int x) {
            this.x = x;
        }

        public int getY() {
            return y;
        }

        public void setY(int y) {
            this.y = y;
        }

        public int getWidth() {
            return width;
        }

        public void setWidth(int width) {
            this.width = width;
        }

        public int getHeight() {
            return height;
        }

        public void setHeight(int height) {
            this.height = height;
        }

        public int getMaxWidth() {
            return maxWidth;
        }

        public void setMaxWidth(int maxWidth) {
            this.maxWidth = maxWidth;
        }

        public int getMaxHeight() {
            return maxHeight;
        }

        public void setMaxHeight(int maxHeight) {
            this.maxHeight = maxHeight;
        }

        public Integer getThumbnailWidth() {
            return thumbnailWidth;
        }

        public void setThumbnailWidth(Integer thumbnailWidth) {
            this.thumbnailWidth = thumbnailWidth;
        }

        public Integer getThumbnailHeight() {
            return thumbnailHeight;
        }

        public void setThumbnailHeight(Integer thumbnailHeight) {
            this.thumbnailHeight = thumbnailHeight;
        }
    }

    private MultipartFile getMultipart(HttpServletRequest request) {
        if (!(request instanceof MultipartHttpServletRequest)) {
            throw new LogicException("MultipartHttpServletRequest is required");
        }
        MultipartHttpServletRequest multiRequest = (MultipartHttpServletRequest) request;
        Map<String, MultipartFile> fileMap = multiRequest.getFileMap();
        if (fileMap.isEmpty()) {
            throw new LogicException("Upload file not found");
        }
        return fileMap.entrySet().iterator().next().getValue();
    }

    private void validateLimit(long fileSize, long limit) {
        if (limit != 0 && fileSize > limit) {
            throw new Http400Exception(String.format("file size is too large: %s, must less than %s.",
                    fileSize, limit));
        }
    }

    private void validateType(String extension, String types) {
        if (!Global.Upload.isValidType(types, extension)) {
            throw new Http400Exception(String.format("file extension not allowed: '%s'. allowed extension: '%s'",
                    extension, types));
        }
    }

    private void thumbnail(FileHandler fileHandler, File file, String pathname, String extension,
                           Integer thumbnailWidth, Integer thumbnailHeight) throws IOException {
        File thumbnailFile = FilesEx.getTempFile(extension);
        try {
            // 缩略图。图集需要缩略图，其他一般不需要。
            if (thumbnailWidth != null && thumbnailHeight != null) {
                if (!imageHandler.resize(file.getAbsolutePath(), thumbnailFile.getAbsolutePath(),
                        thumbnailWidth, thumbnailHeight, ResizeMode.normal)) {
                    FileUtils.copyFile(file, thumbnailFile);
                }
            }
            if (thumbnailFile.exists()) {
                fileHandler.store(Uploads.getThumbnailName(pathname), thumbnailFile);
            }
        } finally {
            if (thumbnailFile.exists()) {
                FileUtils.deleteQuietly(thumbnailFile);
            }
        }
    }

    public static final String IMAGE_TYPE = "image";
    public static final String VIDEO_TYPE = "video";
    public static final String DOC_TYPE = "doc";
    public static final String FILE_TYPE = "file";

    private interface ExtraHandle {
        /**
         * 处理文件
         *
         * @param tempFile    上传文件转存的临时文件
         * @param extension   上传文件的扩展名
         * @param pathname    存储文件名
         * @param fileHandler 文件处理对象
         * @throws IOException IO异常
         */
        void handle(File tempFile, String extension, String pathname, FileHandler fileHandler) throws IOException;
    }
}
