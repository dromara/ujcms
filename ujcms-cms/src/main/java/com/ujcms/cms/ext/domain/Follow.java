package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Channel;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.generated.GeneratedFollow;
import org.springframework.lang.Nullable;

/**
 * 关注实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class Follow extends GeneratedFollow {
    /**
     * 关注的文章
     */
    @Nullable
    @JsonIncludeProperties({"id", "title", "url", "image", "publishDate"})
    private Article article;
    /**
     * 关注的栏目
     */
    @Nullable
    @JsonIncludeProperties({"id", "name", "url"})
    private Channel channel;
    /**
     * 关注用户（本关注行的所有者，即粉丝）。供"我的粉丝"列表展示
     */
    @Nullable
    @JsonIncludeProperties({"id", "username", "nickname", "avatar"})
    private User user;
    /**
     * 被关注的用户(type=3)。供"我的关注-用户"列表展示
     */
    @Nullable
    @JsonIncludeProperties({"id", "username", "nickname", "avatar"})
    private User toUser;

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

    @Nullable
    public User getUser() {
        return user;
    }

    public void setUser(@Nullable User user) {
        this.user = user;
    }

    @Nullable
    public User getToUser() {
        return toUser;
    }

    public void setToUser(@Nullable User toUser) {
        this.toUser = toUser;
    }

    /**
     * 类型：文章
     */
    public static final short TYPE_ARTICLE = 1;
    /**
     * 类型：栏目
     */
    public static final short TYPE_CHANNEL = 2;
    /**
     * 类型：用户
     */
    public static final short TYPE_USER = 3;

    public static final String NOT_FOUND = "Follow not found. ID: ";
}
