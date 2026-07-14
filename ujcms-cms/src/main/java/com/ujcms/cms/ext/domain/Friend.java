package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.generated.GeneratedFriend;
import org.springframework.lang.Nullable;

/**
 * 好友实体类
 * <p>
 * 一行数据代表 {@code userId} 视角的好友关系：待通过时仅申请方一行；通过后双方各一行（镜像行），由 Service 在同一事务内维护。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Friend extends GeneratedFriend {
    /**
     * 行所有者（待处理列表中即申请人）
     */
    @Nullable
    @JsonIncludeProperties({"id", "username", "nickname", "avatar"})
    private User user;
    /**
     * 对方用户（好友列表展示对象）
     */
    @Nullable
    @JsonIncludeProperties({"id", "username", "nickname", "avatar"})
    private User friend;

    @Nullable
    public User getUser() {
        return user;
    }

    public void setUser(@Nullable User user) {
        this.user = user;
    }

    @Nullable
    public User getFriend() {
        return friend;
    }

    public void setFriend(@Nullable User friend) {
        this.friend = friend;
    }

    /**
     * 状态：已通过
     */
    public static final short STATUS_ACCEPTED = 0;
    /**
     * 状态：待通过
     */
    public static final short STATUS_PENDING = 1;

    /**
     * 好友关系（相对于当前用户）
     */
    public enum Relation {
        /**
         * 无关系
         */
        NONE,
        /**
         * 我已申请，待对方通过
         */
        PENDING_SENT,
        /**
         * 对方已申请，待我通过
         */
        PENDING_RECEIVED,
        /**
         * 已是好友
         */
        ACCEPTED,
        /**
         * 本人
         */
        SELF
    }
}
