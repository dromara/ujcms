package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.base.MessageBoardBase;
import com.ujcms.util.web.HtmlParserUtils;
import io.swagger.v3.oas.annotations.media.Schema;
import org.springframework.lang.Nullable;

import java.io.Serializable;
import java.util.List;

/**
 * 留言板实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class MessageBoard extends MessageBoardBase implements Serializable {
    private static final long serialVersionUID = 1L;

    @JsonIgnore
    public List<String> getAttachmentUrls() {
        return HtmlParserUtils.getUrls(getReplyText());
    }

    private Dict type = new Dict();
    private User user = new User();
    @Nullable
    private User replyUser;

    @Schema(description = "留言类型")
    @JsonIncludeProperties({"id", "name"})
    public Dict getType() {
        return type;
    }

    public void setType(Dict type) {
        this.type = type;
    }

    @Schema(description = "留言用户")
    @JsonIncludeProperties({"id", "username"})
    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Schema(description = "回复用户")
    @Nullable
    @JsonIncludeProperties({"id", "username"})
    public User getReplyUser() {
        return replyUser;
    }

    public void setReplyUser(@Nullable User replyUser) {
        this.replyUser = replyUser;
    }
}