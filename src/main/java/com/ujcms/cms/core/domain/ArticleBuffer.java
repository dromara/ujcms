package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.base.ArticleBufferBase;
import io.swagger.v3.oas.annotations.media.Schema;

import java.io.Serializable;

/**
 * 文章缓冲实体类
 *
 * @author PONY
 */
@Schema(name = "Article.ArticleBuffer")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties("handler")
public class ArticleBuffer extends ArticleBufferBase implements Serializable {
    private static final long serialVersionUID = 1L;

    public ArticleBuffer() {
    }

    public ArticleBuffer(Integer id) {
        setId(id);
    }
}