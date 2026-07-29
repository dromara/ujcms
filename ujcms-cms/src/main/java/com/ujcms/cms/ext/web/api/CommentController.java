package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Attachment;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.AttachmentService;
import com.ujcms.cms.core.service.ConfigService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.support.Props;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.core.web.support.SiteResolver;
import com.ujcms.cms.ext.domain.Comment;
import com.ujcms.cms.ext.service.CommentService;
import com.ujcms.cms.ext.service.args.CommentArgs;
import com.ujcms.cms.ext.web.directive.CommentListDirective;
import com.ujcms.common.captcha.CaptchaTokenService;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Entities;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Servlets;
import com.ujcms.common.web.Uploads;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http401Exception;
import com.ujcms.common.web.exception.Http403Exception;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import org.apache.commons.io.FilenameUtils;
import org.apache.commons.lang3.StringUtils;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.*;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.function.BiFunction;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;
import static com.ujcms.common.query.QueryUtils.QUERY_PREFIX;

/**
 * 评论 Controller
 *
 * @author PONY
 */
@Tag(name = "评论接口")
@RestController
@RequestMapping({API + "/comment", FRONTEND_API + "/comment"})
public class CommentController {
    private final CaptchaTokenService captchaTokenService;
    private final ArticleService articleService;
    private final AttachmentService attachmentService;
    private final ConfigService configService;
    private final CommentService service;
    private final SiteResolver siteResolver;
    private final Props props;

    public CommentController(CaptchaTokenService captchaTokenService, ArticleService articleService,
                             AttachmentService attachmentService, ConfigService configService,
                             CommentService service, SiteResolver siteResolver, Props props) {
        this.captchaTokenService = captchaTokenService;
        this.articleService = articleService;
        this.attachmentService = attachmentService;
        this.configService = configService;
        this.service = service;
        this.siteResolver = siteResolver;
        this.props = props;
    }

    private <T> T query(HttpServletRequest request, BiFunction<CommentArgs, Map<String, String>, T> handle) {
        Site site = siteResolver.resolve(request);
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        CommentArgs args = CommentArgs.of(QueryUtils.getQueryMap(params, QUERY_PREFIX));
        CommentListDirective.assemble(args, params, site.getId());
        // 前台接口只允许获取已审核数据
        args.status(Collections.singleton(Comment.STATUS_AUDITED));
        return handle.apply(args, params);
    }

    @Operation(summary = "评论列表_CommentList")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "articleId", description = "文章ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "userId", description = "用户ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "parentId", description = "上级评论ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isRecommended", description = "是否推荐。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点评论。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Comment> list(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int offset = Directives.getOffset(params);
            int limit = Directives.getLimit(params);
            return service.selectList(args, offset, limit);
        });
    }

    @Operation(summary = "评论分页_CommentPage")
    @Parameter(in = ParameterIn.QUERY, name = "siteId", description = "站点ID。默认为当前站点",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "articleId", description = "文章ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "userId", description = "用户ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "parentId", description = "上级评论ID",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "isRecommended", description = "是否推荐。如：`true` `false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "isAllSite", description = "是否获取所有站点评论。如：`true` `false`，默认`false`",
            schema = @Schema(type = "boolean"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Comment> page(HttpServletRequest request) {
        return query(request, (args, params) -> {
            int page = Directives.getPage(params);
            int pageSize = Directives.getPageSize(params);
            return springPage(service.selectPage(args, page, pageSize));
        });
    }

    @Operation(summary = "我的评论列表_MyCommentList。当前登录用户的评论（全部状态），未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "status", description = "状态过滤(0:已审核,1:未审核,2:已屏蔽)。不传获取全部状态",
            schema = @Schema(type = "integer", format = "int16"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/my")
    public List<Comment> myList(@RequestParam(required = false) Short status, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // userId 强制为当前用户，查询参数完全由服务端构造，绝不采信请求中的 Q_ / userId，防止越权读取他人评论
        CommentArgs args = CommentArgs.of().userId(user.getId());
        if (status != null) {
            args.status(Collections.singleton(status));
        }
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(args, offset, limit);
    }

    @Operation(summary = "我的评论分页_MyCommentPage。当前登录用户的评论（全部状态），未登录返回 401。"
            + "totalElements 可用于仪表盘统计")
    @Parameter(in = ParameterIn.QUERY, name = "status", description = "状态过滤(0:已审核,1:未审核,2:已屏蔽)。不传获取全部状态",
            schema = @Schema(type = "integer", format = "int16"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/my/page")
    public Page<Comment> myPage(@RequestParam(required = false) Short status, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        CommentArgs args = CommentArgs.of().userId(user.getId());
        if (status != null) {
            args.status(Collections.singleton(status));
        }
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "获取评论对象")
    @ApiResponses(value = {@ApiResponse(description = "评论对象")})
    @GetMapping("/{id:\\d+}")
    public Comment show(@Parameter(description = "评论ID") @PathVariable Long id) {
        Comment bean = service.select(id);
        if (bean == null || bean.getStatus() != Comment.STATUS_AUDITED) {
            throw new Http404Exception(Comment.NOT_FOUND + id);
        }
        if (!bean.getSite().getComment().isEnabled()) {
            throw new Http403Exception("Comment is not enabled.");
        }
        return bean;
    }

    @Operation(summary = "提交评论")
    @PostMapping
    public ResponseEntity<Responses.Body> create(
            @io.swagger.v3.oas.annotations.parameters.RequestBody(description = "评论对象。" +
                    "不可用属性：\"siteId\", \"userId\", \"created\", \"ip\", \"recommended\", \"status\"")
            @RequestBody @Valid CommentParams params,
            HttpServletRequest request) {
        Article article = articleService.select(params.getArticleId());
        if (article == null) {
            throw new Http404Exception(Article.NOT_FOUND + params.getArticleId());
        }
        Site.Comment settings = article.getSite().getComment();
        if (!settings.isEnabled()) {
            throw new Http403Exception("Comment is not enabled.");
        }
        if (!Boolean.TRUE.equals(article.getAllowComment())) {
            throw new Http403Exception("Comment is not allowed.");
        }
        User user = Contexts.findCurrentUser();
        Long userId;
        if (user == null) {
            if (settings.isLoginRequired()) {
                throw new Http401Exception("Comment login required.");
            }
            userId = User.ANONYMOUS_ID;
        } else {
            userId = user.getId();
        }
        if (!captchaTokenService.validateCaptcha(params.captchaToken, params.captcha)) {
            return Responses.failure(request, "error.captchaIncorrect");
        }
        if (StringUtils.isBlank(params.getText())) {
            throw new Http400Exception("Comment text is required.");
        }
        Comment comment = new Comment();
        // 图片列表不参与拷贝，只能通过下方校验后显式设置
        Entities.copy(params, comment, "siteId", "userId", "created", "ip", "recommended", "status",
                "images", "imagesJson");
        comment.setSiteId(article.getSiteId());
        Long parentId = comment.getParentId();
        if (parentId != null) {
            Comment parent = service.select(parentId);
            if (parent == null || !parent.getArticleId().equals(article.getId())) {
                throw new Http400Exception("Parent comment invalid. ID: " + parentId);
            }
            // 只支持一级回复，回复的回复归入顶级评论
            if (parent.getParentId() != null) {
                comment.setParentId(parent.getParentId());
            }
        }
        if (!params.getImages().isEmpty()) {
            comment.setImages(validateImages(params.getImages(), settings, user));
        }
        comment.setStatus(settings.isAuditRequired() ? Comment.STATUS_UNREVIEWED : Comment.STATUS_AUDITED);
        String ip = Servlets.getRemoteAddr(request, props.getIpProxyDepth());
        service.insert(comment, userId, ip);
        return Responses.ok();
    }

    @Operation(summary = "删除我的评论_MyCommentDelete。仅能删除本人评论，否则返回 404（不泄露评论是否存在）；"
            + "级联删除其下的回复、维护文章评论数、清理图片附件引用。未登录返回 401")
    @DeleteMapping("/{id:\\d+}")
    public ResponseEntity<Responses.Body> remove(@Parameter(description = "评论ID") @PathVariable Long id) {
        User user = Contexts.getCurrentUser();
        Comment bean = service.select(id);
        // 不存在或非本人评论一律返回 404，避免泄露评论是否存在
        if (bean == null || !bean.getUserId().equals(user.getId())) {
            throw new Http404Exception(Comment.NOT_FOUND + id);
        }
        // 复用唯一维护计数的删除路径：级联删除回复、addArticleComments(-count)、清理附件引用
        service.delete(id);
        return Responses.ok();
    }

    /**
     * 校验评论图片。图片URL必须是当前用户自己上传的图片附件，数量不得超过站点设置及硬上限
     */
    private List<String> validateImages(List<String> images, Site.Comment settings, @Nullable User user) {
        if (!settings.isImageEnabled()) {
            throw new Http403Exception("Comment image is not enabled.");
        }
        // 匿名评论不允许携带图片，图片归属校验必须有归属人
        if (user == null) {
            throw new Http403Exception("Comment image login required.");
        }
        List<String> list = images.stream().map(String::trim).filter(StringUtils::isNotEmpty).distinct().toList();
        int maxCount = Math.min(settings.getImageMaxCount(), IMAGES_LIMIT);
        if (list.size() > maxCount) {
            throw new Http400Exception("Comment images cannot exceed " + maxCount);
        }
        String imageTypes = configService.getUnique().getUpload().getImageTypes();
        for (String url : list) {
            Attachment attachment = attachmentService.findByUrl(url);
            if (attachment == null || !attachment.getUserId().equals(user.getId())) {
                throw new Http400Exception("Comment image invalid: " + url);
            }
            if (!Uploads.isValidType(imageTypes, FilenameUtils.getExtension(attachment.getPath()))) {
                throw new Http400Exception("Comment image type not allowed: " + url);
            }
        }
        return list;
    }

    /**
     * 单条评论图片数量硬上限，站点设置不得突破
     */
    private static final int IMAGES_LIMIT = 9;

    @Schema(name = "CommentController.CommentParams", description = "评论参数")
    public static class CommentParams extends Comment {
        private static final long serialVersionUID = 1L;
        @Schema(description = "验证码")
        public String captcha;
        @Schema(description = "验证码TOKEN")
        public String captchaToken;
    }
}
