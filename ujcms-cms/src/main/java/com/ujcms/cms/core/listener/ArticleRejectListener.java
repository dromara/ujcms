package com.ujcms.cms.core.listener;

import com.ujcms.cms.core.domain.Article;

/**
 * 文章审核退回监听器（docs/spec/member-contribute/plan.md）。退回是一次人为动作，每次退回都回调；
 * 实现方按需判定是否通知作者
 *
 * @author PONY
 */
public interface ArticleRejectListener {
    /**
     * 文章被退回（状态已置为已退回、退回原因已写入）后的回调，同一事务内
     *
     * @param article 文章对象
     */
    void afterArticleReject(Article article);
}
