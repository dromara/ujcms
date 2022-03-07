package com.ujcms.core.domain;

import com.ujcms.core.domain.base.ArticleBufferBase;

import java.io.Serializable;

/**
 * 文章缓冲 实体类
 *
 * @author PONY
 */
public class ArticleBuffer extends ArticleBufferBase implements Serializable {
    public ArticleBuffer() {
    }

    public ArticleBuffer(Integer id) {
        setId(id);
    }
}