package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.fasterxml.jackson.annotation.JsonView;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.generated.GeneratedMessageBoard;
import com.ujcms.common.web.HtmlParserUtils;
import com.ujcms.common.web.Views;
import org.springframework.lang.Nullable;

import java.util.List;

/**
 * 留言板实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class MessageBoard extends GeneratedMessageBoard {

    @JsonIgnore
    public List<String> getAttachmentUrls() {
        return HtmlParserUtils.getUrls(getReplyText());
    }

    /**
     * 是否可在前台显示。只有已审核且公开的留言才可在前台显示
     */
    @JsonIgnore
    public boolean isDisplayable() {
        return getStatus() == STATUS_AUDITED && Boolean.TRUE.equals(getOpen());
    }

    // IP、联系方式为隐私数据，只在后台接口输出，前台接口（使用 Views.List 或 Views.Whole）不输出

    @JsonView(Views.Backend.class)
    @Override
    @Nullable
    public String getContact() {
        return super.getContact();
    }

    @JsonView(Views.Backend.class)
    @Override
    @Nullable
    public String getPhone() {
        return super.getPhone();
    }

    @JsonView(Views.Backend.class)
    @Override
    @Nullable
    public String getEmail() {
        return super.getEmail();
    }

    @JsonView(Views.Backend.class)
    @Override
    @Nullable
    public String getAddress() {
        return super.getAddress();
    }

    @JsonView(Views.Backend.class)
    @Override
    public String getIp() {
        return super.getIp();
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