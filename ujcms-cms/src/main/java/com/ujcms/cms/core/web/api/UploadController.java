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
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http403Exception;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.IOException;
import java.time.Duration;
import java.time.OffsetDateTime;
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
    /**
     * 会员上传配额的计数窗口（docs/spec/member-contribute/plan.md D10），与 {@code ContributeService} 的投稿计数同窗
     */
    public static final Duration MEMBER_UPLOAD_QUOTA_WINDOW = Duration.ofHours(24);

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
     * 会员上传评论图片。只用全局上传设置里的会员上传限制，不接受前台传入的压缩、水印等参数
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
        return doMemberImageUpload(request);
    }

    /**
     * 会员上传投稿图片（封面与正文插图，docs/spec/member-contribute/plan.md）。同评论图片：只用全局上传设置里的会员上传限制，
     * 站点未允许投稿图片则不接受
     */
    @PostMapping("contribute-image-upload")
    @PreAuthorize("isAuthenticated()")
    public Map<String, Object> contributeImageUpload(HttpServletRequest request) throws IOException {
        Site site = siteResolver.resolve(request);
        Contexts.setCurrentSite(site);
        Site.Contribute contribute = site.getContribute();
        if (!contribute.isEnabled() || !contribute.isImageEnabled()) {
            throw new Http403Exception("Contribute image is not enabled.");
        }
        return doMemberImageUpload(request);
    }

    /**
     * 会员图片上传的共用门禁与处理（docs/spec/member-contribute/plan.md D10）：按人按 24 小时的张数配额
     * （{@code Config.Upload.memberImageDailyLimit}，评论与投稿合计——图片在内容之前就落盘，不能等内容侧的配额兜底），
     * 单张大小取会员上限与“图片最大长度”中较小者（{@link Config.Upload#getMemberImageLimitByte()}），再按全局设置压缩
     */
    private Map<String, Object> doMemberImageUpload(HttpServletRequest request) throws IOException {
        Config.Upload upload = configService.getUnique().getUpload();
        int dailyLimit = upload.getMemberImageDailyLimit();
        if (dailyLimit > 0) {
            OffsetDateTime since = OffsetDateTime.now().minus(MEMBER_UPLOAD_QUOTA_WINDOW);
            if (attachmentService.countByUserCreated(Contexts.getCurrentUser().getId(), since) >= dailyLimit) {
                throw new Http400Exception("error.upload.memberImageDailyLimit", String.valueOf(dailyLimit));
            }
        }
        ExtraHandle extraHandle = (tempFile, baseName, extension, pathname, fileHandler, s, userId, result) -> {
            int imageMaxWidth = upload.getImageMaxWidth();
            int imageMaxHeight = upload.getImageMaxHeight();
            if (imageMaxWidth > 0 || imageMaxHeight > 0) {
                imageHandler.resize(tempFile.getAbsolutePath(), tempFile.getAbsolutePath(),
                        imageMaxWidth, imageMaxHeight, ResizeMode.normal);
            }
        };
        return doUpload(request, upload.getMemberImageLimitByte(), upload.getImageTypes(), IMAGE_TYPE, extraHandle);
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
