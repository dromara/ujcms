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
import org.jspecify.annotations.Nullable;

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

    // 留言人联系方式（联系人/电话/邮箱/地址）与 IP 属于隐私字段：仅在详情/后台视图（默认 Whole）返回。
    // 公开的留言列表/详情使用 @JsonView(Views.List)，不会返回这些字段，避免向匿名访客泄露他人联系方式。
    // 留言的标题与正文是公开内容，不在此列。

    @JsonView(Views.Whole.class)
    @Nullable
    @Override
    public String getContact() {
        return super.getContact();
    }

    @JsonView(Views.Whole.class)
    @Nullable
    @Override
    public String getPhone() {
        return super.getPhone();
    }

    @JsonView(Views.Whole.class)
    @Nullable
    @Override
    public String getEmail() {
        return super.getEmail();
    }

    @JsonView(Views.Whole.class)
    @Nullable
    @Override
    public String getAddress() {
        return super.getAddress();
    }

    @JsonView(Views.Whole.class)
    @Override
    public String getIp() {
        return super.getIp();
    }

    /**
     * 是否可在前台显示。只有已审核且公开的留言才可在前台显示
     */
    @JsonIgnore
    public boolean isDisplayable() {
        return getStatus() == STATUS_AUDITED && Boolean.TRUE.equals(getOpen());
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