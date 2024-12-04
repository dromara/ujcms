package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.base.MessageBoardBase;
import com.ujcms.commons.web.HtmlParserUtils;
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

    /**
     * 留言类型
     */
    @JsonIncludeProperties({"id", "name"})
    private MessageBoardType type = new MessageBoardType();
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private Site site = new Site();
    /**
     * 留言用户
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();
    /**
     * 回复用户
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    @Nullable
    private User replyUser;

    public MessageBoardType getType() {
        return type;
    }

    public void setType(MessageBoardType type) {
        this.type = type;
    }

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    @Nullable
    public User getReplyUser() {
        return replyUser;
    }

    public void setReplyUser(@Nullable User replyUser) {
        this.replyUser = replyUser;
    }

    /**
     * 状态：已审核
     */
    public static final short STATUS_AUDITED = 0;
    /**
     * 状态：未审核
     */
    public static final short STATUS_UNREVIEWED = 1;
    /**
     * 状态：已屏蔽
     */
    public static final short STATUS_BLOCKED = 2;

    public static final String NOT_FOUND = "MessageBoard not found. ID: ";
}