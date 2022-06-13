package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.base.ArticleCustomBase;
import com.ujcms.cms.core.domain.support.CustomBean;

import java.io.Serializable;

/**
 * 文章自定义数据 实体类
 *
 * @author PONY
 */
public class ArticleCustom extends ArticleCustomBase implements Serializable, CustomBean {
    public ArticleCustom() {
    }

    public ArticleCustom(Integer articleId, String name, String type, String value) {
        setArticleId(articleId);
        setName(name);
        setType(type);
        setValue(value);
    }
}