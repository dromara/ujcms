package com.ujcms.cms.ext.web.api;

import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.ArticleService;
import com.ujcms.cms.core.service.ChannelService;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.core.web.support.Directives;
import com.ujcms.cms.ext.domain.Follow;
import com.ujcms.cms.ext.service.FollowService;
import com.ujcms.cms.ext.service.args.FollowArgs;
import com.ujcms.common.query.QueryUtils;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Views;
import com.ujcms.common.web.exception.Http400Exception;
import com.ujcms.common.web.exception.Http404Exception;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.enums.ParameterIn;
import io.swagger.v3.oas.annotations.media.Schema;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.data.domain.Page;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;
import java.util.List;
import java.util.Map;

import static com.ujcms.cms.core.support.UrlConstants.API;
import static com.ujcms.cms.core.support.UrlConstants.FRONTEND_API;
import static com.ujcms.common.db.MyBatis.springPage;

/**
 * 关注 Controller
 *
 * @author PONY
 */
@Tag(name = "关注接口")
@RestController
@RequestMapping({API + "/follow", FRONTEND_API + "/follow"})
public class FollowController {
    private final ArticleService articleService;
    private final ChannelService channelService;
    private final UserService userService;
    private final FollowService service;

    public FollowController(ArticleService articleService, ChannelService channelService,
                            UserService userService, FollowService service) {
        this.articleService = articleService;
        this.channelService = channelService;
        this.userService = userService;
        this.service = service;
    }

    /**
     * 校验文章ID、栏目ID、被关注用户ID三个参数有且仅有一个
     */
    private static void validateTargetParams(@Nullable Long articleId, @Nullable Long channelId,
                                             @Nullable Long toUserId) {
        int count = 0;
        if (articleId != null) {
            count++;
        }
        if (channelId != null) {
            count++;
        }
        if (toUserId != null) {
            count++;
        }
        if (count != 1) {
            throw new Http400Exception("Exactly one of 'articleId', 'channelId' and 'toUserId' is required");
        }
    }

    @Operation(summary = "关注列表_FollowList。获取当前登录用户的关注，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型(1:文章,2:栏目,3:用户)。不传获取全部",
            schema = @Schema(type = "integer", format = "int16"))
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping
    public List<Follow> list(@RequestParam(required = false) Short type, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // userId 强制为当前用户，不接受任意查询参数
        FollowArgs args = FollowArgs.of().userId(user.getId()).type(type);
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(args, offset, limit);
    }

    @Operation(summary = "关注分页_FollowPage。获取当前登录用户的关注，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "type", description = "类型(1:文章,2:栏目,3:用户)。不传获取全部",
            schema = @Schema(type = "integer", format = "int16"))
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/page")
    public Page<Follow> page(@RequestParam(required = false) Short type, HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FollowArgs args = FollowArgs.of().userId(user.getId()).type(type);
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "粉丝分页_FollowerPage。获取关注当前登录用户的粉丝（type=3），行携带 user（粉丝）属性，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "page", description = "第几页",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "pageSize", description = "每页多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/followers/page")
    public Page<Follow> followerPage(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        // toUserId 强制为当前用户，反向查询关注我的行
        FollowArgs args = FollowArgs.of().toUserId(user.getId()).type(Follow.TYPE_USER);
        int page = Directives.getPage(params);
        int pageSize = Directives.getPageSize(params);
        return springPage(service.selectPage(args, page, pageSize));
    }

    @Operation(summary = "粉丝列表_FollowerList。获取关注当前登录用户的粉丝（type=3），行携带 user（粉丝）属性，未登录返回 401")
    @Parameter(in = ParameterIn.QUERY, name = "offset", description = "从第几条数据开始获取。默认为0，即从第一条开始获取",
            schema = @Schema(type = "integer", format = "int32"))
    @Parameter(in = ParameterIn.QUERY, name = "limit", description = "共获取多少条数据。最大不能超过1000",
            schema = @Schema(type = "integer", format = "int32"))
    @JsonView(Views.List.class)
    @GetMapping("/followers")
    public List<Follow> followers(HttpServletRequest request) {
        User user = Contexts.getCurrentUser();
        Map<String, String> params = QueryUtils.getParams(request.getQueryString());
        FollowArgs args = FollowArgs.of().toUserId(user.getId()).type(Follow.TYPE_USER);
        int offset = Directives.getOffset(params);
        int limit = Directives.getLimit(params);
        return service.selectList(args, offset, limit);
    }

    @Operation(summary = "是否已关注文章或栏目。文章ID和栏目ID有且仅有一个，否则返回 400。未登录用户返回 false，不返回 401")
    @GetMapping("/exists")
    public boolean exists(@Parameter(description = "文章ID") @RequestParam(required = false) Long articleId,
                          @Parameter(description = "栏目ID") @RequestParam(required = false) Long channelId) {
        validateTargetParams(articleId, channelId, null);
        User user = Contexts.findCurrentUser();
        if (user == null) {
            return false;
        }
        if (articleId != null) {
            return service.findByArticleIdAndUserId(articleId, user.getId()) != null;
        }
        return service.findByChannelIdAndUserId(channelId, user.getId()) != null;
    }

    @Operation(summary = "与指定用户的关注关系。返回 none/following/self。未登录用户返回 none，不返回 401")
    @GetMapping("/relation")
    public String relation(@Parameter(description = "对方用户ID") @RequestParam Long toUserId) {
        User user = Contexts.findCurrentUser();
        if (user == null) {
            return FollowService.RELATION_NONE;
        }
        return service.relation(user.getId(), toUserId);
    }

    @Operation(summary = "指定用户的粉丝数和关注数。匿名可访问，用于公开主页展示")
    @GetMapping("/user-stat")
    public UserStat userStat(@Parameter(description = "用户ID") @RequestParam Long userId) {
        return new UserStat(service.countFollowers(userId), service.countFollowing(userId));
    }

    @Operation(summary = "关注文章、栏目或用户。三个目标有且仅有一个，否则返回 400。目标不存在返回 404。"
            + "关注用户时不能为本人或匿名用户(400)。未登录返回 401。重复关注不报错")
    @PostMapping
    public ResponseEntity<Responses.Body> create(@RequestBody @Valid FollowParams params) {
        User user = Contexts.getCurrentUser();
        validateTargetParams(params.getArticleId(), params.getChannelId(), params.getToUserId());
        Follow bean = new Follow();
        if (params.getArticleId() != null) {
            Article article = articleService.select(params.getArticleId());
            if (article == null) {
                throw new Http404Exception(Article.NOT_FOUND + params.getArticleId());
            }
            if (service.findByArticleIdAndUserId(article.getId(), user.getId()) != null) {
                return Responses.ok();
            }
            bean.setType(Follow.TYPE_ARTICLE);
            bean.setArticleId(article.getId());
            // 站点ID取自关注目标，而非当前请求解析的站点
            bean.setSiteId(article.getSiteId());
        } else if (params.getChannelId() != null) {
            Channel channel = channelService.select(params.getChannelId());
            if (channel == null) {
                throw new Http404Exception(Channel.NOT_FOUND + params.getChannelId());
            }
            if (service.findByChannelIdAndUserId(channel.getId(), user.getId()) != null) {
                return Responses.ok();
            }
            bean.setType(Follow.TYPE_CHANNEL);
            bean.setChannelId(channel.getId());
            bean.setSiteId(channel.getSiteId());
        } else {
            Long toUserId = params.getToUserId();
            if (User.ANONYMOUS_ID.equals(toUserId)) {
                throw new Http400Exception("Cannot follow anonymous user");
            }
            if (user.getId().equals(toUserId)) {
                throw new Http400Exception("Cannot follow yourself");
            }
            User toUser = userService.select(toUserId);
            if (toUser == null) {
                throw new Http404Exception(User.NOT_FOUND + toUserId);
            }
            if (service.findByToUserIdAndUserId(toUserId, user.getId()) != null) {
                return Responses.ok();
            }
            bean.setType(Follow.TYPE_USER);
            bean.setToUserId(toUserId);
            // 用户关注与站点无关，siteId 保持 null
        }
        try {
            service.insert(bean, user.getId());
        } catch (DuplicateKeyException e) {
            // 并发重复关注。唯一索引兜底，事务已回滚，视为已关注即可
        }
        return Responses.ok();
    }

    @Operation(summary = "取消关注文章、栏目或用户。三个目标有且仅有一个，否则返回 400。未登录返回 401。未关注时不报错")
    @DeleteMapping
    public ResponseEntity<Responses.Body> remove(
            @Parameter(description = "文章ID") @RequestParam(required = false) Long articleId,
            @Parameter(description = "栏目ID") @RequestParam(required = false) Long channelId,
            @Parameter(description = "被关注用户ID") @RequestParam(required = false) Long toUserId) {
        User user = Contexts.getCurrentUser();
        validateTargetParams(articleId, channelId, toUserId);
        Follow bean;
        if (articleId != null) {
            bean = service.findByArticleIdAndUserId(articleId, user.getId());
        } else if (channelId != null) {
            bean = service.findByChannelIdAndUserId(channelId, user.getId());
        } else {
            bean = service.findByToUserIdAndUserId(toUserId, user.getId());
        }
        if (bean != null) {
            service.delete(bean.getId());
        }
        return Responses.ok();
    }

    @Schema(name = "FollowController.FollowParams", description = "关注参数")
    public static class FollowParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 文章ID。与栏目ID、被关注用户ID有且仅有一个
         */
        @Nullable
        @Schema(description = "文章ID。与栏目ID、被关注用户ID有且仅有一个")
        private Long articleId;

        /**
         * 栏目ID。与文章ID、被关注用户ID有且仅有一个
         */
        @Nullable
        @Schema(description = "栏目ID。与文章ID、被关注用户ID有且仅有一个")
        private Long channelId;

        /**
         * 被关注用户ID。与文章ID、栏目ID有且仅有一个
         */
        @Nullable
        @Schema(description = "被关注用户ID。与文章ID、栏目ID有且仅有一个")
        private Long toUserId;

        @Nullable
        public Long getArticleId() {
            return articleId;
        }

        public void setArticleId(@Nullable Long articleId) {
            this.articleId = articleId;
        }

        @Nullable
        public Long getChannelId() {
            return channelId;
        }

        public void setChannelId(@Nullable Long channelId) {
            this.channelId = channelId;
        }

        @Nullable
        public Long getToUserId() {
            return toUserId;
        }

        public void setToUserId(@Nullable Long toUserId) {
            this.toUserId = toUserId;
        }
    }

    /**
     * 用户关注统计（公开）
     *
     * @param followers 粉丝数
     * @param following 关注数
     */
    public record UserStat(long followers, long following) {
    }
}
