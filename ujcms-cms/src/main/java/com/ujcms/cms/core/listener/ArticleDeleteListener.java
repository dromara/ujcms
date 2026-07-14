package com.ujcms.cms.core.listener;

/**
 * 文章删除监听器
 *
 * @author PONY
 */
public interface ArticleDeleteListener {
    /**
     * 文章彻底删除前回调
     *
     * @param articleId 文章ID
     */
    void preArticleDelete(Long articleId);
}
