package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.ArticleBuffer;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ArticleBufferService;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Favorite;
import com.ujcms.cms.ext.domain.FavoriteGroup;
import com.ujcms.cms.ext.service.FavoriteGroupService;
import com.ujcms.cms.ext.service.FavoriteService;
import com.ujcms.cms.ext.service.args.FavoriteArgs;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.responses.ApiResponse;
import io.swagger.v3.oas.annotations.responses.ApiResponses;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import jakarta.servlet.http.HttpServletRequest;
import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;
import java.util.Optional;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 收藏 Controller
 *
 * @author PONY
 */
@Tag(name = "收藏接口")
@RestController
@RequestMapping({API + "/favorite", FRONTEND_API + "/favorite"})
public class FavoriteController {
    private final ArticleService articleService;
    private final ArticleBufferService bufferService;
    private final FavoriteService service;
    private final FavoriteGroupService groupService;

    public FavoriteController(ArticleService articleService, ArticleBufferService bufferService,
                              FavoriteService service, FavoriteGroupService groupService) {
        this.articleService = articleService;
        this.bufferService = bufferService;
        this.service = service;
        this.groupService = groupService;
    }

    /**
     * 组装当前用户的收藏查询条件。groupId 为 {@code null} 查询全部，为 {@code 0} 查询未分组
     */
    private static FavoriteArgs assembleArgs(Long userId, @Nullable Long groupId) {
        FavoriteArgs args = FavoriteArgs.of().userId(userId);
        if (groupId != null) {
            if (groupId == 0L) {
                args.groupIdIsNull();
            } else {
                args.groupId(groupId);
            }
        }
        return args;
    }

    /**
     * 校验收藏夹属于当前用户，并返回收藏夹ID。收藏夹不存在或不属于当前用户，抛出 404 异常
     */
    @Nullable
    private Long validateGroupId(@Nullable Long groupId, Long userId) {
        if (groupId == null) {
            return null;
        }
        FavoriteGroup group = groupService.select(groupId);
        if (group == null || !group.getUserId().equals(userId)) {
            throw new Http404Exception(FavoriteGroup.NOT_FOUND + groupId);
        }
        return groupId;
    }

    @Operation(summary = "收藏列表_FavoriteList。获取当前登录用户的收藏，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "groupId", description = "收藏夹ID。不传获取全部，`0` 获取未分组",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Favorite> list(@RequestParam(required = false) Long groupId, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FavoriteArgs args = assembleArgs(user.getId(), groupId);
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(args, offset, limit);
    }

    @Operation(summary = "收藏分页_FavoritePage。获取当前登录用户的收藏，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "groupId", description = "收藏夹ID。不传获取全部，`0` 获取未分组",
            schema = @Schema(type = "integer", format = "int64"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Favorite> page(@RequestParam(required = false) Long groupId, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FavoriteArgs args = assembleArgs(user.getId(), groupId);
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "是否已收藏文章。未登录用户返回 false，不返回 401")
    @GetMapping("/exists")
    public boolean exists(@Parameter(description = "文章ID") @RequestParam Long articleId) {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            return false;
        }
        return service.findByArticleIdAndUserId(articleId, user.getId()) != null;
    }

    @Operation(summary = "收藏文章。未登录返回 401。重复收藏不报错，不重复计数")
    @ApiResponses(value = {@ApiResponse(description = "文章总收藏次数")})
    @PostMapping
    public int create(@RequestBody @Valid FavoriteParams params) {
        User user = Contexts.getCurrentUser();
        Article article = articleService.select(params.getArticleId());
        if (article == null) {
            throw new Http404Exception(Article.NOT_FOUND + params.getArticleId());
        }
        if (service.findByArticleIdAndUserId(article.getId(), user.getId()) == null) {
            Favorite bean = new Favorite();
            bean.setSiteId(article.getSiteId());
            bean.setArticleId(article.getId());
            bean.setGroupId(validateGroupId(params.getGroupId(), user.getId()));
            try {
                service.insert(bean, user.getId());
            } catch (DuplicateKeyException e) {
                // 并发重复收藏。唯一索引兜底，事务已回滚，视为已收藏即可
            }
        }
        return getFavorites(article.getId());
    }

    @Operation(summary = "移动收藏至收藏夹。未登录返回 401。只能移动当前登录用户自己的收藏")
    @PutMapping
    public ResponseEntity<Responses.Body> move(@RequestBody @Valid FavoriteParams params) {
        User user = Contexts.getCurrentUser();
        Favorite bean = service.findByArticleIdAndUserId(params.getArticleId(), user.getId());
        if (bean == null) {
            throw new Http404Exception(Favorite.NOT_FOUND + "articleId=" + params.getArticleId());
        }
        service.updateGroupId(bean.getId(), validateGroupId(params.getGroupId(), user.getId()));
        return Responses.ok();
    }

    @Operation(summary = "取消收藏文章。未登录返回 401。未收藏时不报错")
    @ApiResponses(value = {@ApiResponse(description = "文章总收藏次数")})
    @DeleteMapping
    public int remove(@Parameter(description = "文章ID") @RequestParam Long articleId) {
        User user = Contexts.getCurrentUser();
        Favorite bean = service.findByArticleIdAndUserId(articleId, user.getId());
        if (bean != null) {
            service.delete(bean.getId());
        }
        return getFavorites(articleId);
    }

    private int getFavorites(Long articleId) {
        return Optional.ofNullable(bufferService.select(articleId)).map(ArticleBuffer::getFavorites).orElse(0);
    }

    @Schema(name = "FavoriteController.FavoriteParams", description = "收藏参数")
    public static class FavoriteParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 文章ID
         */
        @NotNull
        @Schema(description = "文章ID")
        private Long articleId = 0L;

        /**
         * 收藏夹ID。不传或传 {@code null} 为不属于任何收藏夹
         */
        @Nullable
        @Schema(description = "收藏夹ID。不传为不属于任何收藏夹")
        private Long groupId;

        public Long getArticleId() {
            return articleId;
        }

        public void setArticleId(Long articleId) {
            this.articleId = articleId;
        }

        @Nullable
        public Long getGroupId() {
            return groupId;
        }

        public void setGroupId(@Nullable Long groupId) {
            this.groupId = groupId;
        }
    }
}
