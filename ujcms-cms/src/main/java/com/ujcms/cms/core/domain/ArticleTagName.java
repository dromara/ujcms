package com.ujcms.cms.core.domain;

import java.io.Serial;
import java.io.Serializable;

/**
 * 文章的一个 TAG 名称（批量读取多篇文章的 TAG 时用）
 *
 * @author PONY
 */
public class ArticleTagName implements Serializable {
    @Serial
    private static final long serialVersionUID = 1L;

    /**
     * 文章ID
     */
    private Long articleId = 0L;
    /**
     * TAG ID
     */
    private Long tagId = 0L;
    /**
     * TAG 名称
     */
    private String name = "";

    public Long getArticleId() {
        return articleId;
    }

    public void setArticleId(Long articleId) {
        this.articleId = articleId;
    }

    public Long getTagId() {
        return tagId;
    }

    public void setTagId(Long tagId) {
        this.tagId = tagId;
    }

    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }
}
