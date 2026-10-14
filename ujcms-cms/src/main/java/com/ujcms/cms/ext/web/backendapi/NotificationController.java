package com.ujcms.cms.ext.web.backendapi;

import com.ujcms.cms.core.aop.annotations.OperationLog;
import com.ujcms.cms.core.aop.enums.OperationType;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.core.service.UserService;
import com.ujcms.cms.core.service.args.UserArgs;
import com.ujcms.cms.core.support.Contexts;
import com.ujcms.cms.ext.service.NotificationService;
import com.ujcms.common.web.Responses;
import com.ujcms.common.web.Responses.Body;
import com.ujcms.common.web.exception.Http400Exception;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotEmpty;
import jakarta.validation.constraints.NotNull;
import org.apache.commons.lang3.StringUtils;
import org.hibernate.validator.constraints.Length;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.Nullable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.io.Serial;
import java.io.Serializable;
import java.util.Collections;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;

import static com.ujcms.cms.core.support.UrlConstants.BACKEND_API;

/**
 * 通知 Controller。后台仅可发送系统通知，不提供用户收件箱的浏览接口
 *
 * @author PONY
 */
@RestController("backendNotificationController")
@RequestMapping(BACKEND_API + "/ext/notification")
public class NotificationController {
    private final NotificationService service;
    private final UserService userService;

    public NotificationController(NotificationService service, UserService userService) {
        this.service = service;
        this.userService = userService;
    }

    /**
     * 单次发送最大接收用户数量
     */
    public static final int MAX_RECIPIENTS = 1000;
    /**
     * 接收用户搜索最大返回条数
     */
    private static final int USER_SEARCH_MAX_LIMIT = 100;

    @PostMapping("system")
    @PreAuthorize("hasAnyAuthority('notification:send','*')")
    @OperationLog(module = "notification", operation = "sendSystem", type = OperationType.CREATE)
    public ResponseEntity<Body> sendSystem(@RequestBody @Valid SendSystemParams params) {
        List<Long> userIds = params.getUserIds().stream().filter(Objects::nonNull).distinct().toList();
        if (userIds.isEmpty()) {
            throw new Http400Exception("userIds is required");
        }
        // 全量校验接收用户，任一无效则整体失败，不发送任何通知
        for (Long userId : userIds) {
            User user = userService.select(userId);
            if (user == null || user.getId().equals(User.ANONYMOUS_ID) || user.isCancelled()) {
                throw new Http400Exception("Invalid recipient. ID: " + userId);
            }
        }
        User sender = Contexts.getCurrentUser();
        service.sendSystem(userIds, sender.getId(), params.getTitle(), params.getText(), params.getUrl());
        return Responses.ok();
    }

    /**
     * 接收用户搜索。刻意不复用 {@code user:list} 权限（其暴露完整用户数据），仅返回展示所需字段
     */
    @GetMapping("user")
    @PreAuthorize("hasAnyAuthority('notification:send','*')")
    public List<UserRefer> userList(@RequestParam String q,
                                    @RequestParam(required = false, defaultValue = "20") int limit) {
        if (StringUtils.isBlank(q)) {
            return Collections.emptyList();
        }
        Map<String, Object> queryMap = new HashMap<>(16);
        // 同组条件为 OR：用户名或昵称包含关键字
        queryMap.put("Contains_0q_username", q);
        queryMap.put("Contains_0q_nickname", q);
        queryMap.put("NE_status_Short", User.STATUS_CANCELLED);
        queryMap.put("NE_id_Long", User.ANONYMOUS_ID);
        List<User> users = userService.selectList(UserArgs.of(queryMap), 0,
                Math.min(Math.max(limit, 1), USER_SEARCH_MAX_LIMIT));
        return users.stream().map(user ->
                new UserRefer(user.getId(), user.getUsername(), user.getNickname(), user.getAvatar())).toList();
    }

    /**
     * 用户展示字段
     */
    public record UserRefer(Long id, String username, @Nullable String nickname, @Nullable String avatar) {
    }

    public static class SendSystemParams implements Serializable {
        @Serial
        private static final long serialVersionUID = 1L;

        /**
         * 接收用户ID列表
         */
        @NotEmpty
        @jakarta.validation.constraints.Size(max = MAX_RECIPIENTS)
        private List<Long> userIds;
        /**
         * 通知标题
         */
        @NotNull
        @Length(min = 1, max = 255)
        private String title;
        /**
         * 通知内容
         */
        @Nullable
        @Length(max = 1000)
        private String text;
        /**
         * 链接地址
         */
        @Nullable
        @Length(max = 255)
        private String url;

        public List<Long> getUserIds() {
            return userIds;
        }

        public void setUserIds(List<Long> userIds) {
            this.userIds = userIds;
        }

        public String getTitle() {
            return title;
        }

        public void setTitle(String title) {
            this.title = title;
        }

        @Nullable
        public String getText() {
            return text;
        }

        public void setText(@Nullable String text) {
            this.text = text;
        }

        @Nullable
        public String getUrl() {
            return url;
        }

        public void setUrl(@Nullable String url) {
            this.url = url;
        }
    }
}
