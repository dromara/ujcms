package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.ujcms.cms.core.domain.base.ArticleTagBase;

import java.io.Serializable;

/**
 * 文章TAG关联实体类
 *
 * @author PONY
 */
public class ArticleTag extends ArticleTagBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public ArticleTag() {
    }

    public ArticleTag(Long articleId, Long tagId, Integer order) {
        setArticleId(articleId);
        setTagId(tagId);
        setOrder(order);
    }

    @JsonIgnore
    private Article article = new Article();
    @JsonIgnore
    private Tag tag = new Tag();

    public Article getArticle() {
        return article;
    }

    public void setArticle(Article article) {
        this.article = article;
    }

    public Tag getTag() {
        return tag;
    }

    public void setTag(Tag tag) {
        this.tag = tag;
    }
}