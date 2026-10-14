package com.ujcms.cms.core.web.backendapi;

import com.fasterxml.jackson.annotation.JsonView;
import com.github.pagehelper.Page;
import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.AttachmentRefer;
import com.ujcms.cms.core.domain.BlockItem;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.Config;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.domain.generated.GeneratedArticle;
import com.ujcms.cms.core.domain.generated.GeneratedBlockItem;
import com.ujcms.cms.core.domain.generated.GeneratedChannel;
import com.ujcms.cms.core.domain.generated.GeneratedSite;
import com.ujcms.cms.core.domain.generated.GeneratedUser;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.BlockItemService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.service.SiteService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.AttachmentArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.UrlConstants;
import com.ujcms.cms.core.web.support.ValidUtils;
import com.ujcms.common.file.FileHandler;
import com.ujcms.common.file.WebFile;
import com.ujcms.common.web.PathResolver;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import org.jspecify.annotations.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;
import java.util.Set;

import static com.ujcms.cms.core.support.Constants.validPage;
import static com.ujcms.cms.core.support.Constants.validPageSize;
import static com.ujcms.common.db.MyBatis.springPage;
import static com.ujcms.common.query.QueryUtils.getQueryMap;
import static com.ujcms.common.web.Uploads.AUDIO_TYPE;
import static com.ujcms.common.web.Uploads.DOC_TYPE;
import static com.ujcms.common.web.Uploads.FILE_TYPE;
import static com.ujcms.common.web.Uploads.IMAGE_TYPE;
import static com.ujcms.common.web.Uploads.VIDEO_TYPE;

/**
 * 附件 Controller
 *
 * @author PONY
 */
@RestController("backendAttachmentController")
@RequestMapping(UrlConstants.BACKEND_API + "/core/attachment")
public class AttachmentController {
    private final AttachmentService service;
    private final ConfigService configService;
    private final ArticleService articleService;
    private final ChannelService channelService;
    private final SiteService siteService;
    private final UserService userService;
    private final BlockItemService blockItemService;
    private final PathResolver pathResolver;

    public AttachmentController(AttachmentService service, ConfigService configService,
                                ArticleService articleService, ChannelService channelService,
                                SiteService siteService, UserService userService,
                                BlockItemService blockItemService, PathResolver pathResolver) {
        this.service = service;
        this.configService = configService;
        this.articleService = articleService;
        this.channelService = channelService;
        this.siteService = siteService;
        this.userService = userService;
        this.blockItemService = blockItemService;
        this.pathResolver = pathResolver;
    }

    /**
     * 列表的查询条件、排序可以关联的字段（docs/spec/article-data-permission/plan.md D24）：附件列表按上传人的用户名排序
     */
    private static final Set<String> QUERY_JOINS = Set.of("user-username");

    /**
     * 列表。scope 取值：all（全部附件，默认）、shared（共享资源库，配合 categoryId）、my（我的资源库）。
     * keyword 按文件名模糊过滤，三种范围一致生效（all 范围亦可继续使用 Q_* 查询语法）。
     * 列表视图不含 referList（详情接口才返回引用），避免懒加载集合逐行查询。
     */
    @GetMapping
    @JsonView(Views.List.class)
    @PreAuthorize("hasAnyAuthority('attachment:list','*')")
    public Object list(Integer page, Integer pageSize,
                       @RequestParam(required = false) String type,
                       @RequestParam(required = false) String scope,
                       @RequestParam(required = false) Long categoryId,
                       @RequestParam(required = false) String keyword,
                       HttpServletRequest request) {
        Long siteId = Contexts.getCurrentSiteId();
        Long userId = Contexts.getCurrentUser().getId();
        // 类型过滤对三种范围（all/shared/my）一致生效：选择器按调用方可用类型收窄，避免在图片选择器里看到视频等
        List<String> extensions = resolveExtensions(type);
        Page<Attachment> result;
        if ("shared".equalsIgnoreCase(scope)) {
            result = service.selectSharedPage(siteId, categoryId, keyword, extensions,
                    validPage(page), validPageSize(pageSize));
            service.fillFavorited(result, userId);
        } else if ("my".equalsIgnoreCase(scope)) {
            result = service.selectFavoritePage(siteId, userId, categoryId, keyword, extensions,
                    validPage(page), validPageSize(pageSize));
            result.forEach(a -> a.setFavorited(true));
        } else {
            AttachmentArgs args = AttachmentArgs.of(getQueryMap(request.getQueryString(), QUERY_JOINS))
                    .siteId(siteId)
                    .keyword(keyword)
                    .extensions(extensions);
            result = service.selectPage(args, validPage(page), validPageSize(pageSize));
            service.fillFavorited(result, userId);
        }
        return springPage(result);
    }

    /**
     * 将分类名（image/video/audio/media/doc/file）翻译为 {@code Config.Upload} 中配置的扩展名清单。
     * 其中 "media" 不是真实上传类型，而是图片选择器（如 TinyMCE 媒体对话框）使用的「视频+音频」合并过滤组。
     * 未指定或为 "all" 时返回 null，表示不做类型过滤。
     */
    @Nullable
    private List<String> resolveExtensions(@Nullable String type) {
        if (type == null || type.isBlank() || "all".equalsIgnoreCase(type)) {
            return null;
        }
        Config.Upload upload = configService.getUnique().getUpload();
        String types = switch (type.toLowerCase()) {
            case IMAGE_TYPE -> upload.getImageTypes();
            case VIDEO_TYPE -> upload.getVideoTypes();
            case AUDIO_TYPE -> upload.getAudioTypes();
            case "media" -> {
                String video = upload.getVideoTypes();
                String audio = upload.getAudioTypes();
                if (video == null || video.isBlank()) {
                    yield audio;
                }
                if (audio == null || audio.isBlank()) {
                    yield video;
                }
                yield video + "," + audio;
            }
            case DOC_TYPE -> upload.getDocTypes();
            case FILE_TYPE -> upload.getFileTypes();
            default -> null;
        };
        if (types == null || types.isBlank()) {
            return Collections.emptyList();
        }
        return Arrays.stream(types.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
    }

    /**
     * 详情：含引用列表（referList），并为常见引用类型解析出显示名称（referTitle），便于操作员看懂“谁在用这个文件”。
     */
    @GetMapping("{id}")
    @PreAuthorize("hasAnyAuthority('attachment:show','*')")
    public Object show(@PathVariable Long id) {
        Attachment bean = service.select(id);
        if (bean == null) {
            return Responses.notFound("Attachment not found. ID = " + id);
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        service.fillFavorited(List.of(bean), Contexts.getCurrentUser().getId());
        bean.getReferList().forEach(refer -> refer.setReferTitle(resolveReferTitle(refer)));
        return bean;
    }

    /**
     * 解析引用对象的显示名称。未知类型或对象已不存在时返回 null（前端退化为“类型 #ID”）。
     */
    @Nullable
    private String resolveReferTitle(AttachmentRefer refer) {
        Long referId = refer.getReferId();
        switch (refer.getReferType()) {
            case GeneratedArticle.TABLE_NAME -> {
                Article article = articleService.select(referId);
                return article != null ? article.getTitle() : null;
            }
            case GeneratedChannel.TABLE_NAME -> {
                Channel channel = channelService.select(referId);
                return channel != null ? channel.getName() : null;
            }
            case GeneratedSite.TABLE_NAME -> {
                Site site = siteService.select(referId);
                return site != null ? site.getName() : null;
            }
            case GeneratedUser.TABLE_NAME -> {
                User user = userService.select(referId);
                return user != null ? user.getUsername() : null;
            }
            case GeneratedBlockItem.TABLE_NAME -> {
                BlockItem item = blockItemService.select(referId);
                return item != null ? item.getTitle() : null;
            }
            default -> {
                return null;
            }
        }
    }

    @DeleteMapping
    @PreAuthorize("hasAnyAuthority('attachment:delete','*')")
    @OperationLog(module = "attachment", operation = "delete", type = OperationType.DELETE)
    public ResponseEntity<Body> delete(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        for (Long id : ids) {
            Attachment bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.delete(bean);
        }
        return Responses.ok();
    }

    @DeleteMapping("unused")
    @PreAuthorize("hasAnyAuthority('attachment:delete','*')")
    @OperationLog(module = "attachment", operation = "deleteUnused", type = OperationType.DELETE)
    public int deleteUnused() {
        return service.deleteUnused(Contexts.getCurrentSiteId());
    }

    @PutMapping("share")
    @PreAuthorize("hasAnyAuthority('resource:share','*')")
    @OperationLog(module = "attachment", operation = "share", type = OperationType.UPDATE)
    public ResponseEntity<Body> share(@RequestBody ShareParams params) {
        Attachment bean = service.select(params.getId());
        if (bean == null) {
            throw new Http404Exception("Attachment not found. ID = " + params.getId());
        }
        ValidUtils.dataInSite(bean.getSiteId(), Contexts.getCurrentSiteId());
        service.share(params.getId(), params.getCategoryId());
        return Responses.ok();
    }

    @PutMapping("unshare")
    @PreAuthorize("hasAnyAuthority('resource:share','*')")
    @OperationLog(module = "attachment", operation = "unshare", type = OperationType.UPDATE)
    public ResponseEntity<Body> unshare(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        for (Long id : ids) {
            Attachment bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.unshare(id);
        }
        return Responses.ok();
    }

    @PutMapping("favorite")
    @PreAuthorize("hasAnyAuthority('resource:favorite','*')")
    @OperationLog(module = "attachment", operation = "favorite", type = OperationType.UPDATE)
    public ResponseEntity<Body> favorite(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        Long userId = Contexts.getCurrentUser().getId();
        for (Long id : ids) {
            Attachment bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.favorite(id, userId);
        }
        return Responses.ok();
    }

    @PutMapping("unfavorite")
    @PreAuthorize("hasAnyAuthority('resource:favorite','*')")
    @OperationLog(module = "attachment", operation = "unfavorite", type = OperationType.UPDATE)
    public ResponseEntity<Body> unfavorite(@RequestBody List<Long> ids) {
        Long siteId = Contexts.getCurrentSiteId();
        Long userId = Contexts.getCurrentUser().getId();
        for (Long id : ids) {
            Attachment bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.unfavorite(id, userId);
        }
        return Responses.ok();
    }

    /**
     * 在给定 url 集合中，返回已登记为附件的 url。用于“服务器文件”导入时过滤掉已存在的文件。
     */
    @PostMapping("existing-urls")
    @PreAuthorize("hasAnyAuthority('resource:import','*')")
    public List<String> existingUrls(@RequestBody List<String> urls) {
        return service.findExistingUrls(urls);
    }

    /**
     * 设置/移动我的资源库收藏的所属分类。{@code categoryId} 为空或 0 表示“未分类”。未收藏的附件会自动收藏。
     */
    @PutMapping("favorite-category")
    @PreAuthorize("hasAnyAuthority('resource:favorite','*')")
    @OperationLog(module = "attachment", operation = "favoriteCategory", type = OperationType.UPDATE)
    public ResponseEntity<Body> favoriteCategory(@RequestBody FavoriteCategoryParams params) {
        Long siteId = Contexts.getCurrentSiteId();
        Long userId = Contexts.getCurrentUser().getId();
        for (Long id : params.getIds()) {
            Attachment bean = service.select(id);
            if (bean == null) {
                continue;
            }
            ValidUtils.dataInSite(bean.getSiteId(), siteId);
            service.categorizeFavorite(id, userId, params.getCategoryId());
        }
        return Responses.ok();
    }

    /**
     * 将服务器文件（FTP / 上传管理）登记为附件，并可选择加入共享或我的资源库。
     */
    @PostMapping("import-web-file")
    @PreAuthorize("hasAnyAuthority('resource:import','*')")
    @OperationLog(module = "attachment", operation = "importWebFile", type = OperationType.CREATE)
    public Object importWebFile(@RequestBody ImportParams params) {
        Site site = Contexts.getCurrentSite();
        Long siteId = site.getId();
        Long userId = Contexts.getCurrentUser().getId();
        String webFileId = params.getId();
        // 站点隔离：仅允许导入当前站点目录（/{siteId}）下的文件
        String prefix = "/" + siteId;
        if (webFileId == null || !(webFileId.equals(prefix) || webFileId.startsWith(prefix + "/"))) {
            throw new Http400Exception("web file not in current site dir: " + webFileId);
        }
        FileHandler fileHandler = site.getConfig().getUploadStorage().getFileHandler(pathResolver);
        WebFile webFile = fileHandler.getWebFile(webFileId);
        if (webFile == null || webFile.isDirectory()) {
            throw new Http400Exception("web file not found or is a directory: " + webFileId);
        }
        Attachment attachment = service.importFromWebFile(webFile, siteId, userId);
        if ("shared".equalsIgnoreCase(params.getScope())) {
            service.share(attachment.getId(), params.getCategoryId());
        } else if ("my".equalsIgnoreCase(params.getScope())) {
            service.favorite(attachment.getId(), userId);
        }
        return attachment;
    }

    public static class ShareParams {
        private Long id;
        @Nullable
        private Long categoryId;

        public Long getId() {
            return id;
        }

        public void setId(Long id) {
            this.id = id;
        }

        @Nullable
        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(@Nullable Long categoryId) {
            this.categoryId = categoryId;
        }
    }

    public static class FavoriteCategoryParams {
        private List<Long> ids = Collections.emptyList();
        @Nullable
        private Long categoryId;

        public List<Long> getIds() {
            return ids;
        }

        public void setIds(List<Long> ids) {
            this.ids = ids;
        }

        @Nullable
        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(@Nullable Long categoryId) {
            this.categoryId = categoryId;
        }
    }

    public static class ImportParams {
        private String id;
        @Nullable
        private String scope;
        @Nullable
        private Long categoryId;

        public String getId() {
            return id;
        }

        public void setId(String id) {
            this.id = id;
        }

        @Nullable
        public String getScope() {
            return scope;
        }

        public void setScope(@Nullable String scope) {
            this.scope = scope;
        }

        @Nullable
        public Long getCategoryId() {
            return categoryId;
        }

        public void setCategoryId(@Nullable Long categoryId) {
            this.categoryId = categoryId;
        }
    }
}
