package com.ujcms.core.domain;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;
import com.ujcms.core.domain.base.ArticleExtBase;

import java.io.Serializable;

/**
 * 文章扩展数据 实体类
 *
 * @author PONY
 */
@JsonIgnoreProperties("handler")
public class ArticleExt extends ArticleExtBase implements Serializable {
}