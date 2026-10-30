package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.generated.GeneratedNotification;
import org.springframework.lang.Nullable;

/**
 * 通知实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Notification extends GeneratedNotification {
    /**
     * 发送用户。{@code null} 为系统
     */
    @Nullable
    @JsonIncludeProperties({"id", "username", "nickname", "avatar"})
    private User sender;
    /**
     * 所属文章。{@code url} 供与前台同源的主题使用，{@code fullUrl} 供控制台（与前台不一定同源）
     */
    @Nullable
    @JsonIncludeProperties({"id", "title", "url", "fullUrl"})
    private Article article;
    /**
     * 所属栏目。{@code url} / {@code fullUrl} 的分工同上
     */
    @Nullable
    @JsonIncludeProperties({"id", "name", "url", "fullUrl"})
    private Channel channel;

    @Nullable
    public User getSender() {
        return sender;
    }

    public void setSender(@Nullable User sender) {
        this.sender = sender;
    }

    @Nullable
    public Article getArticle() {
        return article;
    }

    public void setArticle(@Nullable Article article) {
        this.article = article;
    }

    @Nullable
    public Channel getChannel() {
        return channel;
    }

    public void setChannel(@Nullable Channel channel) {
        this.channel = channel;
    }

    /**
     * 类型：系统公告
     */
    public static final short TYPE_SYSTEM = 1;
    /**
     * 类型：评论回复
     */
    public static final short TYPE_COMMENT_REPLY = 2;
    /**
     * 类型：@提及
     */
    public static final short TYPE_MENTION = 3;
    /**
     * 类型：关注文章新评论
     */
    public static final short TYPE_FOLLOW_ARTICLE_COMMENT = 4;
    /**
     * 类型：关注栏目新文章
     */
    public static final short TYPE_FOLLOW_CHANNEL_ARTICLE = 5;
    /**
     * 类型：好友申请
     */
    public static final short TYPE_FRIEND_REQUEST = 6;
    /**
     * 类型：好友通过
     */
    public static final short TYPE_FRIEND_ACCEPTED = 7;
    /**
     * 类型：新增粉丝（有人关注了我）
     */
    public static final short TYPE_FOLLOW_USER = 8;
    /**
     * 类型：关注的用户发布新文章
     */
    public static final short TYPE_FOLLOW_USER_ARTICLE = 9;
    /**
     * 类型：投稿审核结果（发布或退回，docs/spec/member-contribute/plan.md）。text 为退回原因
     */
    public static final short TYPE_CONTRIBUTE_RESULT = 10;

    public static final String NOT_FOUND = "Notification not found. ID: ";
}
