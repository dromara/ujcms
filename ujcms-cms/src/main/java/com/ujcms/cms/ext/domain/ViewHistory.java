package com.ujcms.cms.ext.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.annotation.JsonIncludeProperties;
import com.ujcms.cms.core.domain.Article;
import com.ujcms.cms.core.domain.Site;
import com.ujcms.cms.core.domain.User;
import com.ujcms.cms.ext.domain.generated.GeneratedViewHistory;

/**
 * 浏览历史实体类
 *
 * @author PONY
 */
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ViewHistory extends GeneratedViewHistory {
    /**
     * 站点
     */
    @JsonIncludeProperties({"id", "name", "url"})
    private Site site = new Site();
    /**
     * 所属文章
     */
    @JsonIncludeProperties({"id", "title", "url", "image", "publishDate"})
    private Article article = new Article();
    /**
     * 浏览用户
     */
    @JsonIncludeProperties({"id", "username", "nickname"})
    private User user = new User();

    public Site getSite() {
        return site;
    }

    public void setSite(Site site) {
        this.site = site;
    }

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public User getUser() {
        return user;
    }

    public void setUser(User user) {
        this.user = user;
    }

    public static final String NOT_FOUND = "ViewHistory not found. ID: ";
}
