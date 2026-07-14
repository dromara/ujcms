package com.ujcms.cms.core.web.api;

import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.web.backendapi.AbstractUploadController;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.common.image.ImageHandler;
import com.ujcms.common.image.ResizeMode;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.exception.Http403Exception;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.web.Uploads.AVATAR_TYPE;
import static com.ujcms.common.web.Uploads.IMAGE_TYPE;

/**
 * 上传 Controller
 *
 * @author PONY
 */
@Tag(name = "上传接口")
@RestController
@RequestMapping({API + "/upload", FRONTEND_API + "/upload"})
public class UploadController extends AbstractUploadController {
    private final ConfigService configService;
    private final SiteService siteService;
    private final SiteResolver siteResolver;

    public UploadController(AttachmentService attachmentService, ImageHandler imageHandler, PathResolver pathResolver,
                            ConfigService configService, SiteService siteService, SiteResolver siteResolver,
                            Props props) {
        super(attachmentService, imageHandler, pathResolver, props);
        this.configService = configService;
        this.siteService = siteService;
        this.siteResolver = siteResolver;
    }

    /**
     * 会员上传评论图片。仅使用全局上传设置，不接受前台传入的压缩、水印等参数
     */
    @PostMapping("image-upload")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> imageUpload(HttpServletRequest request) throws IOException {
        Site site = siteResolver.resolve(request);
        Contexts.setCurrentSite(site);
        // 站点未开启评论图片则不允许上传，避免累积无用附件
        if (!site.getComment().isImageEnabled()) {
            throw new Http403Exception("Comment image is not enabled.");
        }
        Config.Upload upload = configService.getUnique().getUpload();
        ExtraHandle extraHandle = (tempFile, baseName, extension, pathname, fileHandler, s, userId, result) -> {
            int imageMaxWidth = upload.getImageMaxWidth();
            int imageMaxHeight = upload.getImageMaxHeight();
            if (imageMaxWidth > 0 || imageMaxHeight > 0) {
                imageHandler.resize(tempFile.getAbsolutePath(), tempFile.getAbsolutePath(),
                        imageMaxWidth, imageMaxHeight, ResizeMode.normal);
            }
        };
        return doUpload(request, upload.getImageLimitByte(), upload.getImageTypes(), IMAGE_TYPE, extraHandle);
    }

    @PostMapping("avatar-upload")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> avatarUpload(HttpServletRequest request) throws IOException {
        Config config = configService.getUnique();
        Site site = getDefaultSite(config.getDefaultSiteId());
        Contexts.setCurrentSite(site);
        Config.Upload upload = config.getUpload();
        return doUpload(request, upload.getImageLimitByte(), upload.getImageTypes(), AVATAR_TYPE, null);
    }

    @PostMapping("avatar-crop")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> avatarCrop(@RequestBody CropParams params) throws IOException {
        Config config = configService.getUnique();
        Site site = getDefaultSite(config.getDefaultSiteId());
        Contexts.setCurrentSite(site);
        return doAvatarCrop(config, params);
    }

    private Site getDefaultSite(Long defaultSiteId) {
        return Optional.ofNullable(siteService.select(defaultSiteId)).orElseThrow(() ->
                new IllegalStateException("default site not found. ID: " + defaultSiteId));
    }

}
