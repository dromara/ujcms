package com.ujcms.cms.core.listener;

import com.ujcms.cms.core.domain.Article;

/**
 * 文章浏览监听器。仅登录会员的浏览触发（匿名浏览不回调）
 *
 * @author PONY
 */
public interface ArticleViewListener {
    /**
     * 会员浏览文章的回调（浏览计数登记之后；高频调用，实现方负责节流与幂等）
     *
     * @param article 文章对象
     * @param userId  浏览用户ID（非空，只有登录会员触发）
     */
    void afterArticleView(Article article, Long userId);
}
