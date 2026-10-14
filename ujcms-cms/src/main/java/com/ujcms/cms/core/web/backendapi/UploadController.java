package com.ujcms.cms.core.web.backendapi;

import com.google.common.collect.ImmutableMap;
import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.image.ImageHandler;
import com.ujcms.common.image.Images;
import com.ujcms.common.image.ResizeMode;
import com.ujcms.common.ip.IpAddresses;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Uploads;
import com.ujcms.common.web.exception.Http400Exception;
import org.apache.commons.io.FileUtils;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.validator.routines.DomainValidator;
import org.jspecify.annotations.Nullable;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.web.util.UriComponentsBuilder;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import java.io.File;
import java.io.IOException;
import java.io.InputStream;
import java.net.HttpURLConnection;
import java.net.URL;
import java.nio.file.Files;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.domain.Config.Security.SSRF_WILDCARD;
import static com.ujcms.common.file.FilesEx.SLASH;
import static com.ujcms.common.web.Uploads.*;

/**
 * 上传 Controller
 *
 * @author PONY
 */
@RestController("backendUploadController")
@RequestMapping(UrlConstants.BACKEND_API)
public class UploadController extends AbstractUploadController {
    private static final Logger logger = LoggerFactory.getLogger(UploadController.class);

    private final ConfigService configService;

    public UploadController(AttachmentService attachmentService, PathResolver pathResolver, ImageHandler imageHandler,
                            ConfigService configService, Props props) {
        super(attachmentService, imageHandler, pathResolver, props);
        this.configService = configService;
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
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> imageUpload(Integer maxWidth, Integer maxHeight, String resizeMode, Boolean isWatermark,
                                           Integer thumbnailWidth, Integer thumbnailHeight, HttpServletRequest request)
            throws IOException {
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
            thumbnail(imageHandler, fileHandler, tempFile, Uploads.getThumbnailName(pathname),
                    extension, thumbnailWidth, thumbnailHeight);
        };
        return doUpload(request, upload.getImageLimitByte(), upload.getImageTypes(), IMAGE_TYPE, extraHandle);
    }

    /**
     * 上传视频。视频时长和截图由浏览器在上传时提取（见后台前端 BaseUpload 组件），随表单一起提交。
     *
     * @param duration 视频时长（秒）。由客户端提取，可空
     * @param image    视频截图。由客户端提取，可空
     */
    @PostMapping("video-upload")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> videoUpload(@RequestParam(required = false) Long duration,
                                           @RequestParam(required = false) MultipartFile image,
                                           HttpServletRequest request) throws IOException {
        Config.Upload upload = configService.getUnique().getUpload();
        if (image != null && !image.isEmpty()) {
            validateLimit(upload.getImageLimitByte(), image.getSize());
        }
        ExtraHandle extraHandle = (tempFile, baseName, extension, pathname, fileHandler, site, userId, result) -> {
            if (duration != null) {
                result.put("duration", duration);
            }
            if (image == null || image.isEmpty()) {
                return;
            }
            File imageFile = Files.createTempFile(null, ".tmp").toFile();
            try {
                image.transferTo(imageFile);
                // 使用图片实际格式作为后缀，并校验是否为图片
                String ext = Images.getFormatName(imageFile);
                if (ext == null) {
                    throw new Http400Exception("Image format type not supported.");
                }
                String path = site.getBasePath(SLASH + IMAGE_TYPE) + Uploads.getRandomFilename(ext);
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

    /**
     * 上传音频。音频时长由浏览器在上传时提取（见后台前端 BaseUpload 组件），随表单一起提交。
     *
     * @param duration 音频时长（秒）。由客户端提取，可空
     */
    @PostMapping("audio-upload")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> audioUpload(@RequestParam(required = false) Long duration,
                                           HttpServletRequest request) throws IOException {
        Config.Upload upload = configService.getUnique().getUpload();
        ExtraHandle extraHandle = duration == null ? null :
                (tempFile, baseName, extension, pathname, fileHandler, site, userId, result) ->
                        result.put("duration", duration);
        return doUpload(request, upload.getAudioLimitByte(), upload.getAudioTypes(), AUDIO_TYPE, extraHandle);
    }

    /**
     * Tinymce 编辑器上传多媒体时，视频和音频混合上传，使用同一个接口。这里要根据文件后缀名进行判断，区分上传的是视频还是音频。
     * 并且不需要做提取视频图片、获取音频时长等额外处理。
     */
    @PostMapping("media-upload")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> mediaUpload(HttpServletRequest request) throws IOException {
        Config.Upload upload = configService.getUnique().getUpload();
        MultipartFile multipart = getMultipart(request);
        String extension = FilenameUtils.getExtension(multipart.getOriginalFilename());
        String videoTypes = upload.getVideoTypes();
        String audioTypes = upload.getAudioTypes();
        if (Uploads.isValidType(videoTypes, extension)) {
            return doUpload(request, upload.getVideoLimitByte(), videoTypes, VIDEO_TYPE, null);
        } else if (Uploads.isValidType(audioTypes, extension)) {
            return doUpload(request, upload.getAudioLimitByte(), audioTypes, AUDIO_TYPE, null);
        } else {
            throw new Http400Exception(String.format("file extension not allowed: '%s'. allowed extension: '%s' or '%s",
                    extension, videoTypes, audioTypes));
        }
    }

    @PostMapping("doc-upload")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> docUpload(HttpServletRequest request) throws IOException {
        Config.Upload upload = configService.getUnique().getUpload();
        return doUpload(request, upload.getDocLimitByte(), upload.getDocTypes(), DOC_TYPE, null);
    }

    @PostMapping("file-upload")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> fileUpload(HttpServletRequest request) throws IOException {
        Config.Upload upload = configService.getUnique().getUpload();
        return doUpload(request, upload.getFileLimitByte(), upload.getFileTypes(), FILE_TYPE, null);
    }

    /**
     * 资源库/附件管理通用上传：按扩展名自动判定类型（图片/视频/音频/文档/其他文件），复用对应类型的限制、
     * 白名单与处理逻辑；并按需加入共享/我的资源库（可同时加入两者）。
     *
     * @param scope       库范围（单选，资源库各标签页定向上传）：shared 加入共享，my 加入我的，其它/空仅登记为附件
     * @param addToMy     是否加入我的资源库（与 scope 并存，支持同时加入两个库）
     * @param addToShared 是否加入共享资源库（与 scope 并存，支持同时加入两个库）
     * @param categoryId  资源库分类ID（共享分类/个人分类；空或 0 归入“未分类”；同时加入两库时两者共用）
     */
    @PostMapping("management-upload")
    @PreAuthorize("hasAnyAuthority('attachment:create','*')")
    public Map<String, Object> managementUpload(@RequestParam(required = false) String scope,
                                                @RequestParam(required = false) Boolean addToMy,
                                                @RequestParam(required = false) Boolean addToShared,
                                                @RequestParam(required = false) Long categoryId,
                                                HttpServletRequest request) throws IOException {
        Config.Upload upload = configService.getUnique().getUpload();
        String extension = FilenameUtils.getExtension(getMultipart(request).getOriginalFilename());
        Map<String, Object> result;
        if (Uploads.isValidType(upload.getImageTypes(), extension)) {
            result = imageUpload(null, null, null, null, null, null, request);
        } else if (Uploads.isValidType(upload.getVideoTypes(), extension)) {
            result = videoUpload(null, null, request);
        } else if (Uploads.isValidType(upload.getAudioTypes(), extension)) {
            result = audioUpload(null, request);
        } else if (Uploads.isValidType(upload.getDocTypes(), extension)) {
            result = docUpload(request);
        } else {
            // 兜底为“其他文件”，仍受 fileTypes 白名单约束（不在白名单则 doUpload 抛 400）
            result = fileUpload(request);
        }
        Attachment attachment = attachmentService.findByUrl((String) result.get("url"));
        if (attachment != null) {
            // scope 为旧的单选语义；addToMy/addToShared 支持同时加入两个库（互不排斥）
            boolean toShared = "shared".equalsIgnoreCase(scope) || Boolean.TRUE.equals(addToShared);
            boolean toMy = "my".equalsIgnoreCase(scope) || Boolean.TRUE.equals(addToMy);
            if (toShared) {
                attachmentService.share(attachment.getId(), categoryId);
            }
            if (toMy) {
                attachmentService.categorizeFavorite(attachment.getId(), Contexts.getCurrentUser().getId(), categoryId);
            }
        }
        return result;
    }

    @PostMapping("avatar-upload")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> avatarUpload(HttpServletRequest request) throws IOException {
        Config config = configService.getUnique();
        Config.Upload upload = config.getUpload();
        return doUpload(request, upload.getImageLimitByte(), upload.getImageTypes(), AVATAR_TYPE, null);
    }

    @PostMapping("avatar-crop")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> avatarCrop(@RequestBody CropParams params) throws IOException {
        Config config = configService.getUnique();
        return doAvatarCrop(config, params);
    }

    @PostMapping("image-crop")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public Map<String, Object> imageCrop(@RequestBody CropParams params) throws IOException {
        Site site = Contexts.getCurrentSite();
        User user = Contexts.getCurrentUser();
        FileHandler fileHandler = site.getConfig().getUploadStorage().getFileHandler(pathResolver);
        String src = fileHandler.getName(params.getUrl());
        if (src == null) {
            throw new Http400Exception("external url not support: " + params.getUrl());
        }
        // 图片裁剪。图片任意裁剪，生成新图片。
        String extension = FilenameUtils.getExtension(src);
        String pathname = site.getBasePath(SLASH + IMAGE_TYPE) + Uploads.getRandomFilename(extension);
        String url = fileHandler.getDisplayPrefix() + pathname;
        File file = fileHandler.getFile(src);
        if (file == null) {
            throw new Http400Exception("file not found: " + src);
        }
        File tempFile = Files.createTempFile(null, "." + extension).toFile();
        try {
            // 裁剪
            crop(fileHandler, file, tempFile, pathname, params);
            // 缩略图。图集需要缩略图，其他一般不需要。
            thumbnail(imageHandler, fileHandler, tempFile, Uploads.getThumbnailName(pathname), extension,
                    params.getThumbnailWidth(), params.getThumbnailHeight());
            String name = src.substring(src.lastIndexOf("/") + 1);
            attachmentService.insert(new Attachment(site.getId(), user.getId(),
                    name, pathname, url, tempFile.length()));
            Map<String, Object> result = new HashMap<>(4);
            result.put("url", url);
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

    /**
     * 抓取远程图片
     */
    @PostMapping(value = "image-fetch", consumes = "text/plain")
    @PreAuthorize("hasAnyAuthority('backend','*')")
    public ResponseEntity<Responses.Body> imageFetch(@RequestBody String source) {
        Config config = configService.getUnique();
        FileHandler fileHandler = config.getUploadStorage().getFileHandler(pathResolver);
        Site site = Contexts.getCurrentSite();
        Config.Security security = config.getSecurity();
        List<String> whiteList = security.getSsrfList();
        try {
            URL src = new URL(source);
            String rejected = checkFetchUrl(src, whiteList);
            if (rejected != null) {
                return Responses.failure(rejected);
            }
            String protocol = src.getProtocol();
            String host = src.getHost();
            // 禁止访问内网：解析域名全部地址，任一命中即拒绝（IPv4 与 IPv6 内网段、唯一本地地址等均覆盖）；
            // 无法解析时抛出，落入下方 catch 一并拒绝。此检查必须在白名单之后、且不受白名单豁免
            if (IpAddresses.isInternalHost(host)) {
                return Responses.failure("Block access to local network: " + host);
            }
            // 使用UriComponentsBuilder重新构建url，防止攻击
            src = UriComponentsBuilder.newInstance().scheme(protocol).host(host)
                    .path(src.getPath()).query(src.getQuery()).build().toUri().toURL();
            HttpURLConnection conn = (HttpURLConnection) src.openConnection();
            conn.setInstanceFollowRedirects(false);
            // responseCode 必须为 200
            int responseCode = conn.getResponseCode();
            if (responseCode != HttpServletResponse.SC_OK) {
                return Responses.failure("Response code error: " + responseCode);
            }
            // contentType 只支持 image/jpeg image/gif image/png image/bmp image/webp
            String contentType = conn.getContentType();
            String extension = Images.getImageExtension(contentType);
            if (extension == null) {
                return Responses.failure("Only supports image contentType: " + contentType);
            }
            // 获得存储路径和显示路径
            String pathname = site.getBasePath(SLASH + IMAGE_TYPE) + Uploads.getRandomFilename(extension);
            String url = fileHandler.getDisplayPrefix() + pathname;
            try (InputStream is = conn.getInputStream()) {
                fileHandler.store(pathname, is);
            }
            return Responses.ok(ImmutableMap.of("src", source, "url", url));
        } catch (Exception e) {
            logger.error("Image cache error!", e);
            return Responses.failure("Image cache error!");
        }
    }

    /**
     * 远程图片地址校验（不做 DNS 解析）：仅 http/https、仅默认端口、域名合法（IP 字面量不合法）且在 SSRF 白名单内。
     * 内网地址判定见 {@link IpAddresses#isInternalHost}，由调用处在本校验通过后执行。
     *
     * @return 拒绝原因；通过返回 {@code null}
     */
    @Nullable
    static String checkFetchUrl(URL src, List<String> whiteList) {
        // 只支持 http 和 https 协议
        String protocol = src.getProtocol();
        String httpProtocol = "http";
        String httpsProtocol = "https";
        if (!httpProtocol.equals(protocol) && !httpsProtocol.equals(protocol)) {
            return "Only supports http and https protocols: " + protocol;
        }
        // 只允许默认端口
        int port = src.getPort();
        if (port != -1) {
            return "Only default port is allowed: " + port;
        }
        String host = src.getHost();
        // 域名是否合法
        if (!DomainValidator.getInstance(true).isValid(host)) {
            return "Host is not valid: " + host;
        }
        // 是否在白名单内
        for (String domain : whiteList) {
            // 允许使用顶级域名
            if (domain.equals(SSRF_WILDCARD) || host.equals(domain) || host.endsWith("." + domain)) {
                return null;
            }
        }
        return "Host not in whitelist: " + host;
    }
}
