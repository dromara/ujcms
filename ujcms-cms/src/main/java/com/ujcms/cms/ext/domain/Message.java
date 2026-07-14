package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.generated.GeneratedMessage;
import org.springframework.lang.Nullable;

/**
 * 私信实体类
 * <p>
 * 一行数据为一个参与方的消息副本：一次发送插入两行（双方会话各一行），{@code senderId} 为消息作者，
 * 用于在会话内区分发送与接收。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Message extends GeneratedMessage {
    /**
     * 发送用户
     */
    @Nullable
    @JsonIncludeProperties({"id", "username", "nickname", "avatar"})
    private User sender;

    @Nullable
    public User getSender() {
        return sender;
    }

    public void setSender(@Nullable User sender) {
        this.sender = sender;
    }

    /**
     * 内容最大长度
     */
    public static final int TEXT_MAX_LENGTH = 1000;
    /**
     * 会话摘要最大长度
     */
    public static final int EXCERPT_MAX_LENGTH = 100;
}
