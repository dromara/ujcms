package com.ujcms.core.domain;

import com.ujcms.core.domain.base.ArticleCustomBase;
import com.ujcms.core.domain.support.CustomBean;

import java.io.Serializable;

/**
 * 文章自定义数据 实体类
 *
 * @author PONY
 */
public class ArticleCustom extends ArticleCustomBase implements Serializable, CustomBean {
    public ArticleCustom() {
    }

    public ArticleCustom(Integer articleId, String name, String value) {
        setArticleId(articleId);
        setName(name);
        setValue(value);
    }
}