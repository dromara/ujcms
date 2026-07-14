package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.generated.GeneratedConversation;
import org.springframework.lang.Nullable;

/**
 * 私信会话实体类
 * <p>
 * 信箱模型：一行数据代表 {@code userId} 视角的会话，一对用户各有一行，首次发送时创建，可独立删除。
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Conversation extends GeneratedConversation {
    /**
     * 对方用户
     */
    @Nullable
    @JsonIncludeProperties({"id", "username", "nickname", "avatar"})
    private User peer;

    @Nullable
    public User getPeer() {
        return peer;
    }

    public void setPeer(@Nullable User peer) {
        this.peer = peer;
    }
}
