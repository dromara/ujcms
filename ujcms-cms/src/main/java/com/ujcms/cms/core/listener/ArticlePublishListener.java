package com.ujcms.cms.core.listener;

import com.ujcms.cms.core.domain.Article;

/**
 * 文章发布监听器
 *
 * @author PONY
 */
public interface ArticlePublishListener {
    /**
     * 文章处于已发布状态的保存后回调（同一事务内；重复回调由实现方负责幂等）
     *
     * @param article 文章对象
     */
    void afterArticlePublish(Article article);
}
