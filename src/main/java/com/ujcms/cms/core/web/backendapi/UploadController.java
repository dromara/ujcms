package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.util.file.FileHandler;
import com.ujcms.util.image.ImageHandler;
import com.ujcms.util.image.Images;
import com.ujcms.util.image.ResizeMode;
import com.ujcms.util.misc.MediaUtils;
import com.ujcms.util.web.PathResolver;
import com.ujcms.util.web.Responses;
import com.ujcms.util.web.Uploads;
import com.ujcms.util.web.exception.Http400Exception;
import com.ujcms.util.web.exception.LogicException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.shiro.authz.annotation.RequiresPermissions;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ws.schild.jave.EncoderException;
import ws.schild.jave.MultimediaObject;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.util.web.Uploads.*;

/**
 * 上传 Controller
 *
 * @author PONY
 */
@RestController("backendUploadController")
@RequestMapping(UrlConstants.BACKEND_API)
public class UploadController {
    private AttachmentService attachmentService;
    private ConfigService configService;
    private ImageHandler imageHandler;
    private PathResolver pathResolver;

    public UploadController(AttachmentService attachmentService, ConfigService configService,
                            ImageHandler imageHandler, PathResolver pathResolver) {
        this.attachmentService = attachmentService;
        this.configService = configService;
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
    @RequiresPermissions("backend")
    public Object imageUpload(Integer maxWidth, Integer maxHeight, String resizeMode, Boolean isWatermark,
                              Integer thumbnailWidth, Integer thumbnailHeight, HttpServletRequest request)
            throws IOException, EncoderException {
        Config.Upload upload = configService.getUnique().getUpload();
        ExtraHandle extraHandle = (tempFile, baseName, extension, pathname, fileHandler, site, userId, result) -> {
            // 图片压缩。全局设置大于一定像素的图片进行压缩。
            int imageMaxWidth = maxWidth != null ? maxWidth : upload.getImageMaxWidth();
            int imageMaxHeight = maxHeight != null ? maxHeight : upload.getImageMaxHeight();
            if (imageMaxWidth > 0 || imageMaxHeight > 0) {
                ResizeMode mode = resizeMode == null ? ResizeMode.normal : ResizeMode.valueOf(resizeMode);
                imageHandler.resize(tempFile.getAbsolutePath(), tempFile.getAbsolutePath(),
                        imageMaxWidth, imageMaxHeight, mode);
            }
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
    @RequiresPermissions("backend")
    public Object videoUpload(HttpServletRequest request) throws IOException, EncoderException {
        Config.Upload upload = configService.getUnique().getUpload();
        ExtraHandle extraHandle = (tempFile, baseName, extension, pathname, fileHandler, site, userId, result) -> {
            MultimediaObject multimediaObject = new MultimediaObject(tempFile);
            long duration = MediaUtils.getDuration(multimediaObject);
            result.put("duration", duration / 1000);
            String ext = "jpg";
            File imageFile = Files.createTempFile(null, "." + ext).toFile();
            try {
                MediaUtils.renderOneImage(multimediaObject, duration, imageFile);
                String path = site.getBasePath("/" + IMAGE_TYPE) + Uploads.getRandomFilename(ext);
                String url = fileHandler.getDisplayPrefix() + path;
                fileHandler.store(path, imageFile);
                attachmentService.insert(new Attachment(site.getId(), userId, baseName + "." + ext,
                        path, url, imageFile.length()));
                result.put("image", url);
            } finally {
                FileUtils.deleteQuietly(imageFile);
            }
        };
        return doUpload(request, upload.getVideoLimitByte(), upload.getVideoTypes(), VIDEO_TYPE, extraHandle);
    }

    @PostMapping("audio-upload")
    @RequiresPermissions("backend")
    public Object audioUpload(HttpServletRequest request) throws IOException, EncoderException {
        Config.Upload upload = configService.getUnique().getUpload();
        ExtraHandle extraHandle = (tempFile, baseName, extension, pathname, fileHandler, site, userId, result) ->
                result.put("duration", MediaUtils.getDuration(new MultimediaObject(tempFile)) / 1000);
        return doUpload(request, upload.getAudioLimitByte(), upload.getAudioTypes(), AUDIO_TYPE, extraHandle);
    }

    @PostMapping("doc-upload")
    @RequiresPermissions("backend")
    public Object docUpload(HttpServletRequest request) throws IOException, EncoderException {
        Config.Upload upload = configService.getUnique().getUpload();
        return doUpload(request, upload.getDocLimitByte(), upload.getDocTypes(), DOC_TYPE, null);
    }

    @PostMapping("file-upload")
    @RequiresPermissions("backend")
    public Object fileUpload(HttpServletRequest request) throws IOException, EncoderException {
        Config.Upload upload = configService.getUnique().getUpload();
        return doUpload(request, upload.getFileLimitByte(), upload.getFileTypes(), FILE_TYPE, null);
    }

    private Object doUpload(HttpServletRequest request, long limitByte, String types, String type,
                            @Nullable ExtraHandle extraHandle) throws IOException, EncoderException {
        MultipartFile multipart = getMultipart(request);
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        // 检查文件大小
        long length = multipart.getSize();
        validateLimit(length, limitByte);
        // 检查文件后缀
        String extension = FilenameUtils.getExtension(multipart.getOriginalFilename());
        validateType(extension, types);

        FileHandler fileHandler = Contexts.getCurrentSite().getConfig().getUploadStorage().getFileHandler(pathResolver);
        File tempFile = Files.createTempFile(null, "." + extension).toFile();
        String name = multipart.getOriginalFilename();
        try {
            Map<String, Object> result = new HashMap<>(8);
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
            String pathname = site.getBasePath("/" + type) + Uploads.getRandomFilename(extension);
            String url = fileHandler.getDisplayPrefix() + pathname;
            // 执行额外的处理
            if (extraHandle != null) {
                String baseName = FilenameUtils.getBaseName(name);
                extraHandle.handle(tempFile, baseName, extension, pathname, fileHandler, site, userId, result);
            }
            fileHandler.store(pathname, tempFile);
            attachmentService.insert(new Attachment(site.getId(), userId, name, pathname, url, length));
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
    @RequiresPermissions("upload:image")
    public Object imageCrop(@RequestBody CropParam params) throws IOException {
        Site site = Contexts.getCurrentSite();
        Integer userId = Contexts.getCurrentUserId();
        FileHandler fileHandler = site.getConfig().getUploadStorage().getFileHandler(pathResolver);
        String src = fileHandler.getName(params.url);
        if (src == null) {
            return Responses.badRequest("external url not support: " + params.url);
        }
        // 图片裁剪。图片任意裁剪，生成新图片。
        String extension = FilenameUtils.getExtension(src);
        String pathname = site.getBasePath("/" + IMAGE_TYPE) + Uploads.getRandomFilename(extension);
        String destUrl = fileHandler.getDisplayPrefix() + pathname;
        File file = fileHandler.getFile(src);
        if (file == null) {
            return Responses.badRequest("file not found: $src");
        }
        File tempFile = Files.createTempFile(null, "." + extension).toFile();
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
                    params.thumbnailWidth, params.thumbnailHeight);
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

    public static class CropParam {
        public String url;
        public int x;
        public int y;
        public int width;
        public int height;
        public int maxWidth;
        public int maxHeight;
        public Integer thumbnailWidth;
        public Integer thumbnailHeight;
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

    private void thumbnail(FileHandler fileHandler, File file, String pathname, String extension,
                           Integer thumbnailWidth, Integer thumbnailHeight) throws IOException {
        // 缩略图。图集需要缩略图，其他一般不需要。
        if (thumbnailWidth == null || thumbnailHeight == null) {
            return;
        }
        File thumbnailFile = Files.createTempFile(null, "." + extension).toFile();
        try {
            if (!imageHandler.resize(file.getAbsolutePath(), thumbnailFile.getAbsolutePath(),
                    thumbnailWidth, thumbnailHeight, ResizeMode.normal)) {
                FileUtils.copyFile(file, thumbnailFile);
            }
            fileHandler.store(Uploads.getThumbnailName(pathname), thumbnailFile);
        } finally {
            FileUtils.deleteQuietly(thumbnailFile);
        }
    }

    private interface ExtraHandle {
        /**
         * 处理文件
         *
         * @param tempFile    上传文件转存的临时文件
         * @param baseName    上传文件的原基础名称（不含扩展名）
         * @param extension   上传文件的扩展名
         * @param pathname    存储文件名
         * @param fileHandler 文件处理对象
         * @param site        站点
         * @param userId      用户ID
         * @param result      返回前台的数据
         * @throws IOException IO异常
         */
        void handle(File tempFile, String baseName, String extension, String pathname, FileHandler fileHandler,
                    Site site, Integer userId, Map<String, Object> result) throws IOException, EncoderException;
    }
}
