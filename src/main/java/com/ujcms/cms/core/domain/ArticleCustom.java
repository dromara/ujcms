package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.ArticleCustomBase;
import com.ujcms.cms.core.domain.support.CustomBean;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 文章自定义数据实体类
 *
 * @author PONY
 */
@Schema(name = "Article.ArticleCustom")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ArticleCustom extends ArticleCustomBase implements Serializable, CustomBean {
    private static final long serialVersionUID = 1L;

    public ArticleCustom() {
    }

    public ArticleCustom(Integer articleId, String name, String type, String value) {
        setArticleId(articleId);
        setName(name);
        setType(type);
        setValue(value);
    }
}