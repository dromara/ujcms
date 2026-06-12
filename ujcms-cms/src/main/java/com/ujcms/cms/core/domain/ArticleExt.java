package com.ujcms.cms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.fasterxml.jackson.annotation.JsonInclude;
import com.ujcms.cms.core.domain.generated.GeneratedArticleExt;

import io.swagger.v3.oas.annotations.media.Schema;

/**
 * 文章扩展数据实体类
 *
 * @author PONY
 */
@Schema(name = "Article.ArticleExt")
@JsonInclude(JsonInclude.Include.NON_NULL)
@JsonIgnoreProperties({"imageListJson", "fileListJson", "handler"})
public class ArticleExt extends GeneratedArticleExt {
}