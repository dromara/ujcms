package com.ujcms.cms.core.web.backendapi;

import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.commons.file.FileHandler;
import com.ujcms.commons.image.ImageHandler;
import com.ujcms.commons.image.Images;
import com.ujcms.commons.image.ResizeMode;
import com.ujcms.commons.web.PathResolver;
import com.ujcms.commons.web.Uploads;
import com.ujcms.commons.web.exception.Http400Exception;
import com.ujcms.commons.web.exception.LogicException;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.lang.Nullable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.multipart.MultipartHttpServletRequest;
import ws.schild.jave.EncoderException;

import javax.servlet.http.HttpServletRequest;
import java.io.File;
import java.io.IOException;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.UUID;

import static com.ujcms.commons.file.FilesEx.SLASH;
import static com.ujcms.commons.web.Uploads.*;

/**
 * 上传抽象类。包含前后台上传的共用方法。
 *
 * @author PONY
 */
public abstract class AbstractUploadController {
    protected final AttachmentService attachmentService;
    protected final ImageHandler imageHandler;
    protected final PathResolver pathResolver;
    protected final Props props;

    protected AbstractUploadController(AttachmentService attachmentService, ImageHandler imageHandler,
                                       PathResolver pathResolver, Props props) {
        this.attachmentService = attachmentService;
        this.imageHandler = imageHandler;
        this.pathResolver = pathResolver;
        this.props = props;
    }

    protected Map<String, Object> doUpload(HttpServletRequest request, long limitByte, String types, String type,
                                           @Nullable ExtraHandle extraHandle) throws IOException, EncoderException {
        MultipartFile multipart = getMultipart(request);
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        // 检查文件大小
        long length = multipart.getSize();
        validateLimit(limitByte, length);
        // 检查文件后缀
        String extension = FilenameUtils.getExtension(multipart.getOriginalFilename());
        if (props.getUploadsExtensionExcludes().contains(StringUtils.lowerCase(extension))) {
            throw new Http400Exception(String.format("file extension not allowed: '%s'. blacklist: '%s'",
                    extension, props.getUploadsExtensionBlacklist()));
        }
        validateType(types, extension);

        FileHandler fileHandler = Contexts.getCurrentSite().getConfig().getUploadStorage().getFileHandler(pathResolver);
        File tempFile = Files.createTempFile(null, "." + extension).toFile();
        String name = Optional.ofNullable(multipart.getOriginalFilename()).orElse("");
        try {
            Map<String, Object> result = new HashMap<>(8);
            multipart.transferTo(tempFile);
            if (IMAGE_TYPE.equalsIgnoreCase(type) || AVATAR_TYPE.equalsIgnoreCase(type)) {
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
            String pathname;
            if (AVATAR_TYPE.equalsIgnoreCase(type)) {
                pathname = SLASH + AVATAR_TYPE + SLASH + user.getId() + SLASH +
                        StringUtils.remove(UUID.randomUUID().toString(), '-') + "." + extension;
            } else {
                pathname = site.getBasePath(SLASH + type) + Uploads.getRandomFilename(extension);
            }
            String url = fileHandler.getDisplayPrefix() + pathname;
            // 执行额外的处理
            if (extraHandle != null) {
                String baseName = FilenameUtils.getBaseName(name);
                extraHandle.handle(tempFile, baseName, extension, pathname, fileHandler, site, user.getId(), result);
            }
            fileHandler.store(pathname, tempFile);
            attachmentService.insert(new Attachment(site.getId(), user.getId(), name, pathname, url, length));
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

    protected Map<String, Object> doAvatarCrop(Config config, CropParams params) throws IOException {
        Config.Register register = config.getRegister();
        User user = Contexts.getCurrentUser();
        FileHandler fileHandler = config.getUploadStorage().getFileHandler(pathResolver);
        String src = fileHandler.getName(params.url);
        String prefix = "/" + AVATAR_TYPE + "/" + user.getId() + "/";
        if (src == null || !src.startsWith(prefix)) {
            throw new Http400Exception("invalid avatar url: " + params.url);
        }
        File file = fileHandler.getFile(src);
        if (file == null) {
            throw new Http400Exception("file not found: " + src);
        }
        String extension = FilenameUtils.getExtension(src);
        String name = src.substring(src.lastIndexOf("/") + 1);
        File tempFile = null;
        try {
            // 复制图片
            String filename = prefix + StringUtils.remove(UUID.randomUUID().toString(), '-') + "." + extension;
            String url = fileHandler.getDisplayPrefix() + filename;
            fileHandler.store(filename, file);
            attachmentService.insert(new Attachment(Contexts.getCurrentSiteId(), user.getId(),
                    name, filename, url, file.length()));
            // 图片裁剪。图片任意裁剪，生成新图片。
            String largeFilename = filename + Config.Register.AVATAR_LARGE + extension;
            String largeUrl = fileHandler.getDisplayPrefix() + largeFilename;
            tempFile = Files.createTempFile(null, "." + extension).toFile();
            // 裁剪
            crop(fileHandler, file, tempFile, largeFilename, params);
            attachmentService.insert(new Attachment(Contexts.getCurrentSiteId(), user.getId(),
                    name, largeFilename, largeUrl, tempFile.length()));
            // 中头像
            String mediumFilename = filename + Config.Register.AVATAR_MEDIUM + extension;
            String mediumUrl = fileHandler.getDisplayPrefix() + mediumFilename;
            long mediumLength = thumbnail(imageHandler, fileHandler, tempFile, mediumFilename, extension,
                    register.getMediumAvatarSize(), register.getMediumAvatarSize());
            attachmentService.insert(new Attachment(Contexts.getCurrentSiteId(), user.getId(),
                    name, mediumFilename, mediumUrl, mediumLength));
            // 小头像
            String smallFilename = filename + Config.Register.AVATAR_SMALL + extension;
            String smallUrl = fileHandler.getDisplayPrefix() + smallFilename;
            long smallLength = thumbnail(imageHandler, fileHandler, tempFile, smallFilename, extension,
                    register.getSmallAvatarSize(), register.getSmallAvatarSize());
            attachmentService.insert(new Attachment(Contexts.getCurrentSiteId(), user.getId(),
                    name, smallFilename, smallUrl, smallLength));
            Map<String, Object> result = new HashMap<>(4);
            result.put("url", url);
            return result;
        } finally {
            if (file.exists()) {
                FileUtils.deleteQuietly(file);
            }
            if (tempFile != null && tempFile.exists()) {
                FileUtils.deleteQuietly(tempFile);
            }
        }
    }

    protected void crop(FileHandler fileHandler, File file, File tempFile, String pathname, CropParams params)
            throws IOException {
        // 未裁剪成功，则直接拷贝原图
        if (!imageHandler.crop(file.getAbsolutePath(), tempFile.getAbsolutePath(),
                params.x, params.y, params.width, params.height)) {
            FileUtils.copyFile(file, tempFile);
        }
        imageHandler.resize(tempFile.getAbsolutePath(), tempFile.getAbsolutePath(),
                params.maxWidth, params.maxHeight, ResizeMode.normal);
        fileHandler.store(pathname, tempFile);
    }

    public static long thumbnail(ImageHandler imageHandler, FileHandler fileHandler, File file, String filename,
                                 String extension, Integer thumbnailWidth, Integer thumbnailHeight) throws IOException {
        // 缩略图。图集需要缩略图，其他一般不需要。
        if (thumbnailWidth == null || thumbnailHeight == null) {
            return 0;
        }
        File thumbnailFile = Files.createTempFile(null, "." + extension).toFile();
        try {
            if (!imageHandler.resize(file.getAbsolutePath(), thumbnailFile.getAbsolutePath(),
                    thumbnailWidth, thumbnailHeight, ResizeMode.normal)) {
                FileUtils.copyFile(file, thumbnailFile);
            }
            fileHandler.store(filename, thumbnailFile);
            return thumbnailFile.length();
        } finally {
            FileUtils.deleteQuietly(thumbnailFile);
        }
    }

    protected MultipartFile getMultipart(HttpServletRequest request) {
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

    protected interface ExtraHandle {
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
         * @throws IOException      IO异常
         * @throws EncoderException Encoding异常
         */
        void handle(File tempFile, String baseName, String extension, String pathname, FileHandler fileHandler,
                    Site site, Long userId, Map<String, Object> result) throws IOException, EncoderException;
    }

    public static class CropParams {
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
}
