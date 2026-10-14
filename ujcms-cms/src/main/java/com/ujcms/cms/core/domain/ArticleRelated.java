package com.ujcms.cms.core.domain;

import com.ujcms.cms.core.domain.generated.GeneratedArticleRelated;

/**
 * 相关文章关联实体类。有向：{@code articleId → relatedId} 一行，互相关联为两行
 *
 * @author PONY
 */
public class ArticleRelated extends GeneratedArticleRelated {

    public ArticleRelated() {
    }

    public ArticleRelated(Long articleId, Long relatedId, Integer order) {
        setArticleId(articleId);
        setRelatedId(relatedId);
        setOrder(order);
    }
}
